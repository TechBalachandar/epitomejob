"""FastAPI shape tests with mocked Java / Ollama / Temporal / DB.

No network, no Ollama, no Temporal, no Postgres.
"""

from fastapi.testclient import TestClient

import app.main as main_mod
from app.auth import InvalidTokenError
from app.main import app
from tests.fakes import USER_ID, FakeOllama

AUTH = {"Authorization": "Bearer test-token"}


def _client() -> TestClient:
    return TestClient(app, raise_server_exceptions=False)


async def _fake_validate(token: str, java_base_url: str, client=None):  # type: ignore[no-untyped-def]
    return {"userId": USER_ID, "headline": "Java Developer"}


async def _fake_validate_bad(token: str, java_base_url: str, client=None):  # type: ignore[no-untyped-def]
    raise InvalidTokenError("bad token")


def test_health_never_500s_when_deps_down(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    monkeypatch.setattr(main_mod, "get_llm", lambda: FakeOllama(down=True))
    async def _down() -> bool:
        return False

    monkeypatch.setattr(main_mod, "_temporal_is_up", _down)
    resp = _client().get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok", "ollama": "down", "temporal": "down"}


def test_chat_requires_bearer(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    resp = _client().post("/chat", json={"message": "hi", "userId": USER_ID})
    assert resp.status_code == 401


def test_chat_rejects_bad_token(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate_bad)
    resp = _client().post("/chat", json={"message": "hi", "userId": USER_ID}, headers=AUTH)
    assert resp.status_code == 401


def test_chat_rejects_empty_message(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    resp = _client().post("/chat", json={"message": "  ", "userId": USER_ID}, headers=AUTH)
    assert resp.status_code == 400


def test_chat_rejects_userid_mismatch(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    resp = _client().post("/chat", json={"message": "hi", "userId": "someone-else"}, headers=AUTH)
    assert resp.status_code == 401


def test_chat_returns_contract_shape_in_process(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)

    async def _no_temporal(message, user_id, token):  # type: ignore[no-untyped-def]
        return None

    async def _fake_run(message, user_id, token, deps):  # type: ignore[no-untyped-def]
        return {
            "reply": "hello",
            "citations": [],
            "actions": [],
            "specialistsUsed": ["matching"],
        }

    monkeypatch.setattr(main_mod, "_run_via_temporal", _no_temporal)
    monkeypatch.setattr(main_mod, "run_coach", _fake_run)
    resp = _client().post("/chat", json={"message": "hi", "userId": USER_ID}, headers=AUTH)
    assert resp.status_code == 200
    body = resp.json()
    assert set(body) == {"reply", "citations", "actions", "specialistsUsed"}
    assert body["reply"] == "hello"
    assert body["specialistsUsed"] == ["matching"]


def test_rag_index_rejects_oversize_text(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    resp = _client().post(
        "/rag/index",
        json={"docId": "job:x", "ownerType": "job", "ownerId": "x", "text": "a" * 200_001},
        headers=AUTH,
    )
    assert resp.status_code == 400


def test_rag_query_empty_chunks_when_ollama_down(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(main_mod, "validate_token", _fake_validate)
    monkeypatch.setattr(main_mod, "get_llm", lambda: FakeOllama(down=True))
    resp = _client().post("/rag/query", json={"query": "java", "topK": 5}, headers=AUTH)
    assert resp.status_code == 200
    assert resp.json() == {"chunks": []}
