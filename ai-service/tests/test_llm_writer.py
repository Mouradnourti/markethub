import json
from types import SimpleNamespace

import anthropic
import httpx

from app.ml import llm_writer


class FakeMessages:
    def __init__(self, payload=None, stop_reason="end_turn", error=None):
        self.payload, self.stop_reason, self.error, self.calls = payload, stop_reason, error, []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        if self.error:
            raise self.error
        text = json.dumps(self.payload) if not isinstance(self.payload, str) else self.payload
        return SimpleNamespace(stop_reason=self.stop_reason, model="claude-opus-5-5",
                               content=[SimpleNamespace(type="text", text=text)])


def client(messages):
    return SimpleNamespace(beta=SimpleNamespace(messages=messages))


GOOD = {
    "tagline": "Hot coffee for hours, wherever your day takes you.",
    "description": "This 750 ml steel flask keeps your coffee hot on the commute.\n\nTake it hiking or to the office.",
    "highlights": ["Keeps drinks hot all day.", "750 ml capacity", "Leak-proof lid"],
}


def test_claude_copy_is_used_and_specs_still_come_from_the_parser():
    messages = FakeMessages(GOOD)
    copy = llm_writer.write("Steel Thermos 750 ml", "Kitchen", ["leak-proof"], "friendly", 0, client=client(messages))
    assert copy is not None
    assert copy.engine == "claude-opus-5-5"
    assert copy.highlights[0] == "Keeps drinks hot all day"
    assert ("Capacity", "750 ml") in copy.specs or any("750" in v for _, v in copy.specs)
    call = messages.calls[0]
    assert call["model"] == "claude-opus-5-5"
    assert call["fallbacks"] == "default"
    assert call["output_config"]["format"]["type"] == "json_schema"
    assert "Steel Thermos 750 ml" in call["messages"][0]["content"]


def test_invented_numbers_fall_back_to_the_rules():
    invented = dict(GOOD, tagline="Keeps coffee hot for 24 hours.")
    assert llm_writer.write("Steel Thermos 750 ml", None, [], "friendly", 0, client=client(FakeMessages(invented))) is None
    assert llm_writer.invented_numbers("24 hours, 750 ml", "Thermos 750 ml") == {"24"}


def test_refusals_errors_and_bad_shapes_fall_back():
    request = httpx.Request("POST", "https://api.anthropic.com/v1/messages")
    for messages in (FakeMessages(GOOD, stop_reason="refusal"), FakeMessages("not json"),
                     FakeMessages({"tagline": "x"}),
                     FakeMessages(error=anthropic.APIConnectionError(request=request))):
        assert llm_writer.write("Lamp", None, [], "premium", 0, client=client(messages)) is None


def test_enabled_only_with_credentials(monkeypatch):
    monkeypatch.setenv("AI_LLM_ENABLED", "auto")
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    assert not llm_writer.is_enabled()
    monkeypatch.setenv("ANTHROPIC_API_KEY", "sk-test")
    assert llm_writer.is_enabled()
    monkeypatch.setenv("AI_LLM_ENABLED", "false")
    assert not llm_writer.is_enabled()
