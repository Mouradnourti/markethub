"""Optional large-language-model description writer (Claude).

Enabled only when an Anthropic API key is configured (``ANTHROPIC_API_KEY`` or another credential the SDK finds).
The rule-based writer in ``copywriter.py`` stays the default and the fallback: any error, refusal, or output that
mentions a number the seller never gave falls back to it, so the feature works offline and never invents specs.
Specs are always extracted by the rule-based parser, never by the model.
"""

from __future__ import annotations

import json
import logging
import os
import re

from app.ml import copywriter

log = logging.getLogger(__name__)

DEFAULT_MODEL = "claude-opus-5-5"
NUMBER = re.compile(r"\d+(?:[.,]\d+)?")

SYSTEM = """You write product copy for MarketHub, a multi-vendor marketplace. Sellers give a product name, a category \
and a few keywords; you write a tagline, a short description and key benefits that help shoppers decide.

Rules:
- Use only facts present in the seller's input. Never invent numbers, measurements, certifications, awards, \
materials, compatibility, warranties or reviews.
- No urgency or scarcity claims (no "limited time", "only a few left", "selling fast").
- English, plain and concrete. Address the shopper as "you". No exclamation marks, no emoji.
- Tagline: one sentence, at most 100 characters, about the main benefit.
- Description: two short paragraphs separated by a blank line, 60 to 110 words in total. The first says what the \
product does for the shopper; the second says when or how they will use it.
- Highlights: 3 to 5 benefits, each at most 70 characters, no trailing period."""

SCHEMA = {
    "type": "object",
    "properties": {
        "tagline": {"type": "string"},
        "description": {"type": "string"},
        "highlights": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["tagline", "description", "highlights"],
    "additionalProperties": False,
}

TONE_GUIDE = {
    "friendly": "Warm and approachable, like a helpful shop assistant.",
    "premium": "Refined and confident, focused on quality and craft, never boastful.",
    "technical": "Precise and factual, led by what the product does and its stated specifications.",
}


def is_enabled() -> bool:
    if os.getenv("AI_LLM_ENABLED", "auto").lower() == "false":
        return False
    return bool(os.getenv("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_AUTH_TOKEN"))


def model_name() -> str:
    return os.getenv("AI_LLM_MODEL", DEFAULT_MODEL)


def _client():
    import anthropic  # imported lazily: the service runs without the SDK's credentials

    return anthropic.Anthropic(timeout=25.0, max_retries=1)


def _prompt(name: str, category: str | None, keywords: list[str], tone: str, variant: int,
            specs: list[tuple[str, str]]) -> str:
    lines = [f"Product name: {name}", f"Category: {category or 'not given'}",
             f"Seller keywords: {', '.join(keywords) if keywords else 'none'}"]
    if specs:
        lines.append("Facts found in the input: " + "; ".join(f"{label}: {value}" for label, value in specs))
    lines.append(f"Tone: {tone}. {TONE_GUIDE[tone]}")
    if variant:
        lines.append(f"This is alternative version {variant}: use a different angle and wording than usual.")
    return "\n".join(lines)


def invented_numbers(output: str, source: str) -> set[str]:
    """Numbers in the model output that do not appear in what the seller wrote."""
    allowed = {n.replace(",", ".") for n in NUMBER.findall(source)}
    return {n for n in (m.replace(",", ".") for m in NUMBER.findall(output)) if n not in allowed}


def write(name: str, category: str | None, keywords: list[str], tone: str, variant: int,
          client=None) -> copywriter.Copy | None:
    """Returns Claude's copy, or None when the caller should use the rule-based writer."""
    import anthropic

    tone = tone if tone in TONE_GUIDE else "friendly"
    base = copywriter.write(name, category, keywords, tone, variant)  # specs and spec suggestions
    try:
        response = (client or _client()).beta.messages.create(
            model=model_name(),
            max_tokens=4000,
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
            output_config={"effort": "low", "format": {"type": "json_schema", "schema": SCHEMA}},
            system=SYSTEM,
            messages=[{"role": "user", "content": _prompt(name, category, keywords, tone, variant, base.specs)}],
        )
    except anthropic.RateLimitError:
        log.warning("Claude rate limit reached, using the rule-based writer")
        return None
    except anthropic.APIStatusError as error:
        log.warning("Claude returned HTTP %s, using the rule-based writer", error.status_code)
        return None
    except anthropic.APIConnectionError:
        log.warning("Claude is unreachable, using the rule-based writer")
        return None

    if response.stop_reason != "end_turn":
        log.info("Claude stopped with %s, using the rule-based writer", response.stop_reason)
        return None
    try:
        text = next(block.text for block in response.content if block.type == "text")
        data = json.loads(text)
        tagline = data["tagline"].strip()
        description = data["description"].strip()
        highlights = [h.strip().rstrip(".") for h in data["highlights"] if h.strip()][:6]
    except (StopIteration, json.JSONDecodeError, KeyError, AttributeError, TypeError):
        log.warning("Claude returned an unexpected shape, using the rule-based writer")
        return None
    if not tagline or not description or not highlights:
        return None

    source = " ".join([name, category or "", *keywords])
    invented = invented_numbers(" ".join([tagline, description, *highlights]), source)
    if invented:
        log.info("Claude mentioned numbers the seller did not give (%s), using the rule-based writer", invented)
        return None
    return copywriter.Copy(tagline=tagline[:200], description=description[:4000], highlights=highlights,
                           specs=base.specs, suggested_spec_labels=base.suggested_spec_labels,
                           category_profile=base.category_profile, engine=response.model)
