"""Product description writer for sellers.

Template-based natural-language generation: it extracts attributes (capacity, power, size, material, colour…)
from the product name and the seller's keywords, turns known features into customer benefits, and writes a
tagline, a description and key benefits in the requested tone with category-specific phrasing.

It never invents facts: every specification and benefit comes from the seller's own input. Wording is chosen
deterministically from the inputs, and ``variant`` picks alternative phrasings ("Regenerate").
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field

TONES = ("friendly", "premium", "technical")


@dataclass(frozen=True)
class Profile:
    noun: str
    leads: dict[str, tuple[str, ...]]
    uses: tuple[str, ...]
    closings: tuple[str, ...]
    spec_labels: tuple[str, ...]


PROFILES: dict[str, Profile] = {
    "electronics": Profile(
        noun="device",
        leads={
            "friendly": ("Meet the {name}: the upgrade your everyday tech has been waiting for.",
                         "Say hello to the {name}, made to keep up with your day.",
                         "The {name} makes everyday tech simple, fast and fun."),
            "premium": ("The {name} brings refined design and dependable performance together.",
                        "Engineered for people who expect more, the {name} delivers without compromise.",
                        "Precision-built and beautifully finished, the {name} sets a new standard."),
            "technical": ("The {name} combines {facts}.",
                          "Key figures for the {name}: {facts}.",
                          "Built around {facts}, the {name} is ready for demanding use."),
        },
        uses=("Use it at home, at work or on the go.",
              "It slots straight into your routine, from the morning commute to late-night streaming.",
              "Set-up takes minutes, so you can enjoy it right out of the box."),
        closings=("Order today and see the difference from day one.",
                  "Add it to your cart and upgrade your setup.",
                  "A smart choice you will use every single day."),
        spec_labels=("Model", "Connectivity", "Battery", "Dimensions", "Weight", "In the box", "Warranty"),
    ),
    "home": Profile(
        noun="piece",
        leads={
            "friendly": ("Make home feel even more like home with the {name}.",
                         "The {name} is the little upgrade that makes every day at home nicer.",
                         "Cook, host and relax better with the {name}."),
            "premium": ("The {name} brings timeless design and lasting quality into your home.",
                        "Crafted to be used and admired, the {name} elevates every room.",
                        "Thoughtfully designed, the {name} is made to last for years."),
            "technical": ("The {name} offers {facts}.",
                          "Specifications at a glance for the {name}: {facts}.",
                          "Designed with {facts}, the {name} is practical every day."),
        },
        uses=("It fits naturally into a busy kitchen, a cosy living room or a calm bedroom.",
              "Easy to use, easy to clean and easy to love.",
              "Perfect for everyday life, and a lovely gift too."),
        closings=("Bring it home today.",
                  "Treat your home, or someone you love.",
                  "Order now and enjoy it for years to come."),
        spec_labels=("Dimensions", "Material", "Capacity", "Care", "Weight", "Colour"),
    ),
    "fashion": Profile(
        noun="piece",
        leads={
            "friendly": ("Meet your new favourite: the {name}.",
                         "The {name} is the piece you will reach for again and again.",
                         "Comfortable, easy to style and made for real life: the {name}."),
            "premium": ("The {name} pairs clean lines with considered materials.",
                        "Elevated essentials start with the {name}.",
                        "Understated and refined, the {name} is built to be worn for seasons."),
            "technical": ("The {name} features {facts}.",
                          "Construction details of the {name}: {facts}.",
                          "Made with {facts}, the {name} balances comfort and durability."),
        },
        uses=("Dress it up or down, from the office to the weekend.",
              "It pairs effortlessly with what is already in your wardrobe.",
              "Built for everyday wear, wash after wash."),
        closings=("Add it to your cart and complete the look.",
                  "Order your size today.",
                  "A wardrobe essential you will not want to take off."),
        spec_labels=("Material", "Fit", "Care", "Sizes", "Colour"),
    ),
    "beauty": Profile(
        noun="formula",
        leads={
            "friendly": ("Give your routine a glow-up with the {name}.",
                         "The {name} is the easy step your routine was missing.",
                         "Feel good in your skin with the {name}."),
            "premium": ("The {name} turns your daily ritual into a moment of care.",
                        "Carefully formulated, the {name} delivers visible results.",
                        "Luxurious texture, thoughtful ingredients: the {name}."),
            "technical": ("The {name} contains {facts}.",
                          "Formulation details of the {name}: {facts}.",
                          "Formulated with {facts}, the {name} targets everyday needs."),
        },
        uses=("Use it morning and evening as part of your routine.",
              "Small enough for your bag, effective enough for every day.",
              "Gentle enough to use daily."),
        closings=("Add it to your routine today.",
                  "Order now and feel the difference.",
                  "Your skin will thank you."),
        spec_labels=("Volume", "Key ingredients", "Skin type", "How to use"),
    ),
    "sports": Profile(
        noun="gear",
        leads={
            "friendly": ("Train harder, recover faster and have more fun with the {name}.",
                         "The {name} is ready whenever you are.",
                         "Get moving with the {name}."),
            "premium": ("The {name} is built for athletes who never settle.",
                        "Performance-driven design meets everyday durability in the {name}.",
                        "Engineered for your best sessions, the {name} goes the distance."),
            "technical": ("The {name} offers {facts}.",
                          "Performance data for the {name}: {facts}.",
                          "Designed around {facts}, the {name} supports serious training."),
        },
        uses=("At home, at the gym or outdoors, it goes wherever your training takes you.",
              "Light to carry and quick to set up.",
              "Made to handle sweat, mud and repetition."),
        closings=("Order now and start your next session stronger.",
                  "Add it to your kit today.",
                  "Your next personal best starts here."),
        spec_labels=("Weight", "Dimensions", "Material", "Max load", "Use"),
    ),
    "books": Profile(
        noun="title",
        leads={
            "friendly": ("Curl up with the {name}.",
                         "The {name} is the next thing you will not be able to put down.",
                         "Make room on your shelf for the {name}."),
            "premium": ("The {name} is a beautifully made edition worth keeping.",
                        "Thoughtfully produced, the {name} rewards every read.",
                        "A collector-worthy edition: the {name}."),
            "technical": ("The {name} includes {facts}.",
                          "Edition details of the {name}: {facts}.",
                          "Produced with {facts}, the {name} is built to last."),
        },
        uses=("Ideal for quiet evenings, long journeys and thoughtful gifts.",
              "A great pick for yourself or for someone who loves to learn.",
              "Keep it on your desk, your nightstand or in your bag."),
        closings=("Order your copy today.",
                  "Add it to your reading list now.",
                  "The perfect gift, wrapped in good ideas."),
        spec_labels=("Format", "Pages", "Language", "Size"),
    ),
    "toys": Profile(
        noun="toy",
        leads={
            "friendly": ("Hours of fun start with the {name}.",
                         "Get ready for smiles: the {name} is here.",
                         "The {name} turns any afternoon into an adventure."),
            "premium": ("The {name} is a beautifully made toy that grows with your child.",
                        "Built to be played with and passed on, the {name} stands out.",
                        "Quality play, made to last: the {name}."),
            "technical": ("The {name} comes with {facts}.",
                          "What is in the {name}: {facts}.",
                          "Designed with {facts}, the {name} encourages creative play."),
        },
        uses=("Perfect for family game nights, rainy days and birthday gifts.",
              "Simple to learn, fun to master.",
              "Encourages creativity, focus and play together."),
        closings=("Order now and make playtime special.",
                  "Add it to your cart: the perfect gift.",
                  "Let the fun begin."),
        spec_labels=("Age", "Players", "Pieces", "Dimensions", "Batteries"),
    ),
    "general": Profile(
        noun="product",
        leads={
            "friendly": ("Meet the {name}, made to make your day easier.",
                         "The {name} is the everyday essential you will be glad you found.",
                         "Discover the {name}."),
            "premium": ("The {name} is designed with care and built to last.",
                        "Quality you can feel: the {name}.",
                        "Considered design and lasting quality define the {name}."),
            "technical": ("The {name} offers {facts}.",
                          "Key details of the {name}: {facts}.",
                          "Built with {facts}, the {name} does the job well."),
        },
        uses=("Practical every day and simple to use.",
              "A dependable choice for home, work or travel.",
              "Made for everyday life."),
        closings=("Order yours today.",
                  "Add it to your cart now.",
                  "A great choice for yourself or as a gift."),
        spec_labels=("Dimensions", "Material", "Weight", "Colour"),
    ),
}

CATEGORY_KEYWORDS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("electronics", ("electronic", "phone", "tablet", "audio", "computer", "laptop", "tech", "camera")),
    ("beauty", ("beauty", "health", "skin", "hair", "cosmetic", "wellness")),
    ("fashion", ("fashion", "clothing", "shoe", "bag", "apparel", "jewel", "accessor")),
    ("sports", ("sport", "outdoor", "fitness", "gym", "camping")),
    ("books", ("book", "stationery", "station")),
    ("toys", ("toy", "game", "kid", "baby")),
    ("home", ("home", "kitchen", "decor", "light", "garden", "furniture")),
)

# Unit patterns: (spec label, regex). Each match becomes a spec and a fact.
UNIT_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("Storage", re.compile(r"\b(\d+(?:[.,]\d+)?)\s?(GB|TB)\b", re.I)),
    ("Battery", re.compile(r"\b(\d[\d\s,.]*)\s?mAh\b", re.I)),
    ("Power", re.compile(r"\b(\d+(?:[.,]\d+)?)\s?W\b")),
    ("Refresh rate", re.compile(r"\b(\d+)\s?Hz\b", re.I)),
    ("Camera", re.compile(r"\b(\d+)\s?MP\b", re.I)),
    ("Screen", re.compile(r"\b(\d+(?:[.,]\d+)?)\s?(?:\"|”|-?inch(?:es)?)", re.I)),
    ("Capacity", re.compile(r"\b(\d+(?:[.,]\d+)?)\s?(ml|L|litres?|liters?)\b", re.I)),
    ("Size", re.compile(r"\b(\d+(?:[.,]\d+)?(?:\s?[x×]\s?\d+(?:[.,]\d+)?)*)\s?(cm|mm|m)\b", re.I)),
    ("Weight", re.compile(r"\b(\d+(?:[.,]\d+)?)\s?(kg|g)\b", re.I)),
    ("Pieces", re.compile(r"\b(\d+)\s?(?:-?\s?(?:pcs|pieces|pack|pc))\b|\bset of (\d+)\b|\((\d+)-pack\)", re.I)),
)

MATERIALS = (
    "organic cotton", "stainless steel", "cast iron", "full-grain leather", "merino wool", "recycled polyester",
    "cotton", "linen", "merino", "wool", "leather", "suede", "denim", "fleece", "canvas", "bamboo", "oak",
    "walnut", "aluminium", "aluminum", "steel", "glass", "ceramic", "porcelain", "silicone", "titanium", "silk",
    "velvet", "marble", "soy wax",
)

COLOURS = ("black", "white", "grey", "gray", "silver", "gold", "navy", "blue", "red", "green", "pink",
           "beige", "brown", "purple", "yellow", "orange")

# Known features → customer benefit (only used when the seller mentions the feature).
BENEFITS: tuple[tuple[re.Pattern[str], str], ...] = tuple((re.compile(p, re.I), b) for p, b in (
    (r"noise[- ]cancel", "Noise cancelling that lets you focus on what matters"),
    (r"waterproof|water[- ]resistant|ip6\d|ipx\d", "Water resistant, ready for rain, sweat and splashes"),
    (r"wireless|bluetooth", "Wireless freedom, no tangled cables"),
    (r"fast[- ]charg", "Fast charging gets you going again in minutes"),
    (r"usb[- ]?c", "Modern USB-C connection"),
    (r"rechargeable", "Rechargeable, no disposable batteries"),
    (r"foldable|folding|collapsible|packable", "Folds away for easy storage and travel"),
    (r"lightweight|ultra[- ]?light", "Lightweight and easy to carry"),
    (r"portable", "Portable, take it anywhere"),
    (r"organic", "Made with organic materials"),
    (r"recycled", "Made with recycled materials"),
    (r"vegan|cruelty[- ]free", "Vegan and cruelty-free"),
    (r"handmade|hand[- ]made", "Handmade with care"),
    (r"dishwasher", "Dishwasher safe for effortless cleaning"),
    (r"induction", "Works on every hob, including induction"),
    (r"non[- ]?stick", "Non-stick surface for easy cooking and cleaning"),
    (r"machine washable", "Machine washable"),
    (r"adjustable", "Adjustable for a perfect fit"),
    (r"ergonomic", "Ergonomic design for all-day comfort"),
    (r"anti[- ]?slip|non[- ]?slip", "Non-slip grip for stability"),
    (r"stretch", "Stretch fabric that moves with you"),
    (r"fragrance[- ]free|unscented", "Fragrance-free, gentle on sensitive skin"),
    (r"spf\s?\d+", "Daily sun protection"),
    (r"warranty", "Covered by warranty"),
))

STOP_WORDS = {"with", "and", "for", "the", "a", "an", "of", "in", "set", "pack", "new"}


@dataclass
class Copy:
    tagline: str
    description: str
    highlights: list[str]
    specs: list[tuple[str, str]]
    suggested_spec_labels: list[str] = field(default_factory=list)
    category_profile: str = "general"
    engine: str = "rules"


def profile_for(category: str | None) -> str:
    text = (category or "").lower()
    for key, words in CATEGORY_KEYWORDS:
        if any(word in text for word in words):
            return key
    return "general"


def _pick(options: tuple[str, ...], seed: str, variant: int) -> str:
    digest = int(hashlib.sha256(seed.encode("utf-8")).hexdigest(), 16)
    return options[(digest + variant) % len(options)]


def _sentence(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip().rstrip(".")
    return text[:1].upper() + text[1:] + "." if text else ""


def _lower_first(text: str) -> str:
    """Lower-cases the first letter for use mid-sentence, but keeps acronyms such as "AMOLED" or "USB-C"."""
    if len(text) > 1 and text[1].isupper():
        return text
    return text[:1].lower() + text[1:]


def _capitalise(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip().rstrip(".")
    return text[:1].upper() + text[1:]


def extract_specs(text: str) -> list[tuple[str, str]]:
    """Attributes that can be read reliably from free text (units, materials, colours)."""
    specs: list[tuple[str, str]] = []
    seen: set[str] = set()
    for label, pattern in UNIT_PATTERNS:
        match = pattern.search(text)
        if match and label not in seen:
            value = re.sub(r"\s+", " ", match.group(0)).strip(" ()")
            if label == "Pieces":
                number = next(g for g in match.groups() if g)
                value = f"{number}"
            specs.append((label, value))
            seen.add(label)
    lower = text.lower()
    material = next((m for m in MATERIALS if re.search(rf"\b{re.escape(m)}\b", lower)), None)
    if material:
        specs.append(("Material", material.capitalize()))
    colour = next((c for c in COLOURS if re.search(rf"\b{c}\b", lower)), None)
    if colour:
        specs.append(("Colour", colour.capitalize()))
    return specs


def _facts_phrase(specs: list[tuple[str, str]], keywords: list[str]) -> str:
    facts = [f"{value} {label.lower()}" if label in ("Storage", "Battery", "Capacity")
             else value.lower() if label in ("Material", "Colour") else value
             for label, value in specs[:3]]
    if not facts:
        facts = [k.lower() for k in keywords[:3]]
    if not facts:
        return "a practical, well-considered design"
    if len(facts) == 1:
        return facts[0]
    return ", ".join(facts[:-1]) + " and " + facts[-1]


def write(name: str, category: str | None = None, keywords: list[str] | None = None,
          tone: str = "friendly", variant: int = 0) -> Copy:
    name = re.sub(r"\s+", " ", name).strip()
    tone = tone if tone in TONES else "friendly"
    keywords = [k.strip() for k in (keywords or []) if k and k.strip()][:12]
    profile_key = profile_for(category)
    profile = PROFILES[profile_key]
    source = " ".join([name, *keywords])
    specs = extract_specs(source)

    # Benefits: the seller's own keywords first (a recognised feature is reworded as a benefit), then other
    # recognised features, then extracted specs. Keywords that are only a colour or material go to the specs.
    highlights: list[str] = []

    def add(claim: str) -> None:
        if claim.lower() not in (h.lower() for h in highlights):
            highlights.append(claim)

    for keyword in keywords:
        benefit = next((b for pattern, b in BENEFITS if pattern.search(keyword)), None)
        if benefit:
            add(benefit)
        elif keyword.lower() not in COLOURS and keyword.lower() not in MATERIALS and len(keyword) >= 3:
            add(_capitalise(keyword))
    for pattern, benefit in BENEFITS:
        if pattern.search(source):
            add(benefit)
    for label, value in specs:
        if len(highlights) >= 6:
            break
        if not any(value.lower() in h.lower() for h in highlights):
            add(f"{label}: {value}")
    highlights = highlights[:6]

    facts = _facts_phrase(specs, keywords)
    lead = _pick(profile.leads[tone], name, variant).format(name=name, facts=facts)
    use = _pick(profile.uses, name + "use", variant)
    closing = _pick(profile.closings, name + "close", variant)

    short_claims = [k for k in keywords if k.lower() not in COLOURS and k.lower() not in MATERIALS] or         [h for h in highlights if ":" not in h]
    detail_parts = [_lower_first(c) for c in short_claims[:3]]
    detail = _sentence(f"Highlights: {_join(detail_parts)}") if detail_parts else ""
    paragraph_one = " ".join(part for part in (_sentence(lead), detail) if part)
    paragraph_two = " ".join(part for part in (_sentence(use), _sentence(closing)) if part)

    claims = [h for h in highlights if ":" not in h and len(h) <= 70]
    tagline = _tagline(tone, claims, facts, profile, name, variant)

    known = {label for label, _ in specs}
    suggestions = [label for label in profile.spec_labels if label not in known]
    return Copy(tagline=tagline, description=f"{paragraph_one}\n\n{paragraph_two}", highlights=highlights,
                specs=specs, suggested_spec_labels=suggestions, category_profile=profile_key)


def _join(parts: list[str]) -> str:
    if len(parts) <= 1:
        return "".join(parts)
    return ", ".join(parts[:-1]) + " and " + parts[-1]


def _tagline(tone: str, claims: list[str], facts: str, profile: Profile, name: str, variant: int) -> str:
    """One-line pitch built from the strongest claims, so it never states anything the seller did not."""
    lower = [_lower_first(c) for c in claims]
    if tone == "technical":
        options = (f"{_capitalise(facts)}.", f"{_capitalise(facts)}, in one {profile.noun}.")
    elif tone == "premium":
        options = ((f"{claims[0]}. Designed to last.", f"Considered design. {claims[0]}.") if claims
                   else ("Quality you can feel.", "Designed to last."))
    elif len(claims) >= 2:
        options = (f"{claims[0]} and {lower[1]}.", f"{claims[0]}. {claims[1]}.")
    elif claims:
        options = (f"{claims[0]}, made for every day.", f"{claims[0]}. Simple as that.")
    else:
        options = ("Your new everyday favourite.", "Made for real life.")
    return _pick(options, name + "tag", variant)[:200]
