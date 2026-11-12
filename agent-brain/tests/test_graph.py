"""End-to-end graph tests with fakes (Ollama down -> deterministic fallback)."""

from app.graph.supervisor import CoachDeps, run_coach
from app.rag.index import reset_indexed_cache
from tests.fakes import TOKEN, USER_ID, FakeJava, FakeOllama, FakeStore


def _deps(down: bool = True) -> CoachDeps:
    return CoachDeps(java=FakeJava(), llm=FakeOllama(down=down), store=FakeStore())  # type: ignore[arg-type]


async def test_chat_fallback_reply_ends_with_rules_only_note() -> None:
    reset_indexed_cache()
    result = await run_coach("Find Java jobs in Reston", USER_ID, TOKEN, _deps())
    assert result["reply"].endswith(
        "Note: the local model was unreachable, so this answer used rules only."
    )
    assert result["specialistsUsed"] == ["job_discovery"]
    assert "Senior AI Engineer" in result["reply"]


async def test_matching_turn_uses_citations() -> None:
    reset_indexed_cache()
    # Mocked LLM "up" for embeddings (chat silent -> keyword fallback routing).
    result = await run_coach("How well do I match the Senior AI Engineer job?", USER_ID, TOKEN, _deps(down=False))
    assert result["specialistsUsed"] == ["matching"]
    assert result["citations"], "matching turn should carry RAG citations"
    assert all(c["score"] > 0 for c in result["citations"])


async def test_application_turn_proposes_approval_only() -> None:
    reset_indexed_cache()
    deps = _deps()
    result = await run_coach("Apply now for me", USER_ID, TOKEN, deps)
    assert result["specialistsUsed"] == ["application"]
    assert len(result["actions"]) == 1
    action = result["actions"][0]
    assert action["type"] == "APPLY_JOB"
    assert action["status"] == "proposed"
    assert action["approvalId"] == "approval-1"
    # The fake Java recorded exactly one write, and it was a proposal.
    assert deps.java.proposals == [{"action": "APPLY_JOB", "payload": {"jobId": "10000000-0000-0000-0000-000000000001", "resumeId": "20000000-0000-0000-0000-000000000002"}}]  # type: ignore[attr-defined]
    assert all(c.startswith("propose_approval") or c in ("list_jobs", "list_resumes", "get_resume_text") for c in deps.java.calls)  # type: ignore[attr-defined]


async def test_learning_turn_is_curated_links_only() -> None:
    reset_indexed_cache()
    result = await run_coach("Recommend a Python course for LangGraph", USER_ID, TOKEN, _deps())
    assert result["specialistsUsed"] == ["learning"]
    assert "freecodecamp" in result["reply"].lower() or "kaggle" in result["reply"].lower()
    assert result["actions"] == []
