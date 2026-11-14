"""Unit tests for the Resume Builder Co-Pilot (endpoint + specialist).

Fake LLM only — no Ollama, no Java, no Temporal, no Postgres.
"""

from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient

import app.main as main_mod
from app.auth import InvalidTokenError
from app.graph.copilot import COPILOT_SYSTEM_PROMPT, run_copilot, safe_fallback
from app.main import app
from tests.fakes import USER_ID

AUTH = {"Authorization": "Bearer test-token"}
JD = (
    "Senior Java Developer — Spring Boot, Kafka, PostgreSQL, Azure. "
    "Lead a team of engineers and ship banking APIs."
)
RESUME_STATE = {
    "name": "Balachandar Jeldi",
    "summary": "Java developer with 17 years of experience.",
    "experience": [{"title": "Senior Java Developer", "company": "Navy Federal"}],
}


def _option(option_id: int, tag: str, bullets: list[str], keywords: list[str] | None = None) -> dict:
    return {
        "option_id": option_id,
        "angle_tag": tag,
        "content": bullets,
        "missing_placeholders": ["[X%]"],
        "ats_keywords_used": keywords if keywords is not None else ["Spring Boot", "Kafka"],
    }


def _valid_reply(n: int = 4, keywords: list[str] | None = None) -> str:
    tags = ["Metrics & Impact", "Technical Depth", "Leadership", "ATS Keywords", "Executive Punch"]
    options = [
        _option(
            i + 1,
            tags[i],
            [f"Led Spring Boot migration serving [N+ users], cutting latency by [X%]."],
            keywords,
        )
        for i in range(n)
    ]
    return json.dumps(
        {
            "ui_message": "Pick the angle that fits this role best.",
            "active_section": "experience_item_2",
            "action_type": "PRESENT_OPTIONS",
            "options": options,
            "rendered_patch": {"target_block_id": "experience_item_2", "applied_content": None},
            "suggested_next_step": "Fill in the [X%] placeholder with your real number.",
        }
    )


class FakeCopilotLLM:
    """Fake Ollama: replies are popped in order; None means Ollama down."""

    def __init__(self, replies: list[str | None] | None = None) -> None:
        self.replies = list(replies) if replies is not None else []
        self.calls: list[list[dict[str, str]]] = []
        self.temperatures: list[object] = []

    async def probe(self) -> bool:
        return True

    async def chat(self, messages: list[dict[str, str]], temperature: float | None = None) -> str | None:
        self.calls.append(messages)
        self.temperatures.append(temperature)
        if not self.replies:
            return None
        return self.replies.pop(0)

    async def embed(self, texts: list[str]) -> list[list[float]] | None:
        return [[0.01] * 768 for _ in texts]


async def _fake_validate(token: str, java_base_url: str, client=None):  # type: ignore[no-untyped-def]
    return {"userId": USER_ID, "headline": "Java Developer"}


async def _fake_validate_bad(token: str, java_base_url: str, client=None):  # type: ignore[no-untyped-def]
    raise InvalidTokenError("bad token")


def _client() -> TestClient:
    return TestClient(app, raise_server_exceptions=False)


def _copilot_body(**overrides: object) -> dict:
    body = {
        "active_section": "experience_item_2",
        "user_context": "Senior Java Developer at Navy Federal. Led Spring Boot migration.",
        "target_jd": JD,
        "resume_state": RESUME_STATE,
    }
    body.update(overrides)
    return body


# -- specialist ------------------------------------------------------------
async def test_copilot_returns_valid_schema_and_uses_temperature_zero() -> None:
    llm = FakeCopilotLLM([_valid_reply(4)])
    result = await run_copilot("experience_item_2", "Led Spring Boot migration.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert set(result) == {
        "ui_message", "active_section", "action_type",
        "options", "rendered_patch", "suggested_next_step",
    }
    assert result["active_section"] == "experience_item_2"
    assert result["action_type"] == "PRESENT_OPTIONS"
    assert 3 <= len(result["options"]) <= 5  # type: ignore[arg-type]
    assert result["rendered_patch"]["applied_content"] is None  # type: ignore[index]
    assert llm.temperatures == [0]
    # System prompt is the verbatim constant; user message carries all 4 fields.
    assert llm.calls[0][0] == {"role": "system", "content": COPILOT_SYSTEM_PROMPT}
    user_msg = llm.calls[0][1]["content"]
    assert "experience_item_2" in user_msg and "Spring Boot" in user_msg
    assert "Senior Java Developer" in user_msg and "Balachandar Jeldi" in user_msg


async def test_copilot_five_options_accepted() -> None:
    llm = FakeCopilotLLM([_valid_reply(5)])
    result = await run_copilot("summary", "17y Java developer.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert len(result["options"]) == 5  # type: ignore[arg-type]


async def test_copilot_filters_ats_keywords_not_in_jd() -> None:
    llm = FakeCopilotLLM([_valid_reply(3, keywords=["Spring Boot", "Go", "Rust"])])
    result = await run_copilot("skills", "Java, Spring Boot.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    keywords = result["options"][0]["ats_keywords_used"]  # type: ignore[index]
    assert keywords == ["Spring Boot"]


async def test_copilot_retries_once_on_invalid_json_then_succeeds() -> None:
    llm = FakeCopilotLLM(["not json at all", _valid_reply(3)])
    result = await run_copilot("experience_item_2", "Led migration.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "PRESENT_OPTIONS"
    assert len(llm.calls) == 2
    assert "only a valid JSON object" in llm.calls[1][-1]["content"]


async def test_copilot_falls_back_after_two_invalid_replies() -> None:
    llm = FakeCopilotLLM(["garbage", "{also: broken"])
    result = await run_copilot("experience_item_2", "Led migration.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result == safe_fallback("experience_item_2")
    assert result["action_type"] == "REQUEST_METRICS"
    assert result["options"] == []  # type: ignore[comparison-overlap]
    assert len(llm.calls) == 2


async def test_copilot_schema_invalid_reply_also_retries() -> None:
    bad = json.dumps({"ui_message": "hi", "action_type": "PRESENT_OPTIONS"})  # missing keys
    llm = FakeCopilotLLM([bad, _valid_reply(3)])
    result = await run_copilot("skills", "Java.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "PRESENT_OPTIONS"
    assert len(llm.calls) == 2


async def test_copilot_ollama_down_returns_fallback_without_retry() -> None:
    llm = FakeCopilotLLM([None])
    result = await run_copilot("experience_item_2", "Led migration.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "REQUEST_METRICS"
    assert result["rendered_patch"]["applied_content"] is None  # type: ignore[index]
    assert len(llm.calls) == 1


async def test_copilot_selection_returns_populated_patch() -> None:
    llm = FakeCopilotLLM([_valid_reply(4)])
    ctx = json.dumps({"select_option": 2, "notes": "Led Spring Boot migration."})
    result = await run_copilot("experience_item_2", ctx, JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "APPLY_SELECTION"
    patch = result["rendered_patch"]  # type: ignore[assignment]
    assert patch["target_block_id"] == "experience_item_2"
    assert patch["applied_content"] is not None
    assert "Spring Boot migration" in patch["applied_content"]
    assert patch["applied_content"] == result["options"][1]["content"][0]  # type: ignore[index]


async def test_copilot_selection_in_free_text_also_applies() -> None:
    llm = FakeCopilotLLM([_valid_reply(3)])
    result = await run_copilot(
        "summary", 'Please use "select_option": 3 for my summary.', JD, RESUME_STATE, llm  # type: ignore[arg-type]
    )
    assert result["action_type"] == "APPLY_SELECTION"
    assert result["rendered_patch"]["applied_content"] is not None  # type: ignore[index]


async def test_copilot_edited_selection_uses_edited_content() -> None:
    llm = FakeCopilotLLM([_valid_reply(3)])
    ctx = json.dumps({"select_option": 1, "edited_content": "Led migration serving 2M members."})
    result = await run_copilot("experience_item_2", ctx, JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "APPLY_SELECTION"
    assert result["rendered_patch"]["applied_content"] == "Led migration serving 2M members."  # type: ignore[index]


async def test_copilot_unknown_selection_id_falls_back() -> None:
    llm = FakeCopilotLLM([_valid_reply(3)])
    ctx = json.dumps({"select_option": 99})
    result = await run_copilot("experience_item_2", ctx, JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "REQUEST_METRICS"
    assert "99" in result["ui_message"]  # type: ignore[operator]


async def test_copilot_works_without_target_jd() -> None:
    llm = FakeCopilotLLM([_valid_reply(3)])
    result = await run_copilot("skills", "Java, Spring Boot, Kafka.", None, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "PRESENT_OPTIONS"
    assert "None provided" in llm.calls[0][1]["content"]


# -- endpoint --------------------------------------------------------------
def test_copilot_endpoint_requires_bearer(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    resp = _client().post("/resume-builder/copilot", json=_copilot_body())
    assert resp.status_code == 401


def test_copilot_endpoint_rejects_bad_token(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate_bad)
    resp = _client().post("/resume-builder/copilot", json=_copilot_body(), headers=AUTH)
    assert resp.status_code == 401


def test_copilot_endpoint_rejects_empty_fields(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    resp = _client().post(
        "/resume-builder/copilot", json=_copilot_body(active_section="  "), headers=AUTH
    )
    assert resp.status_code == 400
    resp = _client().post(
        "/resume-builder/copilot", json=_copilot_body(user_context=""), headers=AUTH
    )
    assert resp.status_code == 400


def test_copilot_endpoint_returns_copilot_json_verbatim(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    monkeypatch.setattr(main_mod, "get_llm", lambda: FakeCopilotLLM([_valid_reply(4)]))
    resp = _client().post("/resume-builder/copilot", json=_copilot_body(), headers=AUTH)
    assert resp.status_code == 200
    body = resp.json()
    assert set(body) == {
        "ui_message", "active_section", "action_type",
        "options", "rendered_patch", "suggested_next_step",
    }
    assert body["action_type"] == "PRESENT_OPTIONS"
    assert len(body["options"]) == 4
    assert body["rendered_patch"] == {"target_block_id": "experience_item_2", "applied_content": None}


def test_copilot_endpoint_applies_selection(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    monkeypatch.setattr(main_mod, "get_llm", lambda: FakeCopilotLLM([_valid_reply(3)]))
    body = _copilot_body(user_context=json.dumps({"select_option": 1, "notes": "x"}))
    resp = _client().post("/resume-builder/copilot", json=body, headers=AUTH)
    assert resp.status_code == 200
    assert resp.json()["action_type"] == "APPLY_SELECTION"
    assert resp.json()["rendered_patch"]["applied_content"] is not None


def test_copilot_endpoint_ollama_down_returns_200_fallback(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    monkeypatch.setattr(main_mod, "get_llm", lambda: FakeCopilotLLM([None]))
    resp = _client().post("/resume-builder/copilot", json=_copilot_body(), headers=AUTH)
    assert resp.status_code == 200
    body = resp.json()
    assert body["action_type"] == "REQUEST_METRICS"
    assert body["options"] == []
