"""Coach tool tests: arg validation + approval-proposal-only writes.

Phase 5B: the functions moved from app.mcp.tools to app.coach_tools
(relocated verbatim); these tests are unchanged apart from the import.
"""

import json

import pytest

from app.coach_tools import (  # Phase 5B: was `app.mcp.tools`
    ToolContext,
    ToolError,
    apply_to_job,
    ask_docs,
    draft_application,
    match_score,
    parse_resume,
    search_jobs,
    update_profile,
)
from app.rag.store import RagStore
from tests.fakes import JOB_ID, RESUME_ID, FakeJava, FakeOllama, FakeStore


def _ctx(**kwargs) -> ToolContext:
    return ToolContext(
        java=kwargs.get("java", FakeJava()),
        store=kwargs.get("store", FakeStore()),
        llm=kwargs.get("llm", FakeOllama(down=True)),
        token="test-token",
        user_id="00000000-0000-0000-0000-000000000001",
    )


async def test_search_jobs_trims_to_contract_shape() -> None:
    jobs = await search_jobs(_ctx())
    assert len(jobs) == 1
    job = jobs[0]
    assert set(job) == {
        "id", "title", "company", "location", "workType",
        "salaryMin", "salaryMax", "sponsorsH1b", "skills",
    }
    assert job["title"] == "Senior AI Engineer"


async def test_search_jobs_limit_validation() -> None:
    with pytest.raises(ToolError):
        await search_jobs(_ctx(), limit=0)
    with pytest.raises(ToolError):
        await search_jobs(_ctx(), limit=21)


async def test_parse_resume_preview_and_truncation() -> None:
    result = await parse_resume(_ctx(), RESUME_ID)
    assert result["resumeId"] == RESUME_ID
    assert result["charCount"] > 0
    assert result["truncated"] is False
    assert "Java developer" in str(result["textPreview"])


async def test_parse_resume_requires_id() -> None:
    with pytest.raises(ToolError):
        await parse_resume(_ctx(), "")


async def test_match_score_verbatim() -> None:
    result = await match_score(_ctx(), JOB_ID)
    assert result["score"] == 0.82
    assert result["gaps"] == ["Python"]


async def test_ask_docs_empty_when_ollama_down() -> None:
    result = await ask_docs(_ctx(llm=FakeOllama(down=True)), "python jobs")
    assert result == {"chunks": []}


async def test_ask_docs_returns_chunks_when_up() -> None:
    result = await ask_docs(_ctx(llm=FakeOllama(down=False)), "python jobs", top_k=1)
    assert len(result["chunks"]) == 1
    assert result["chunks"][0]["source"] in ("job", "resume")


async def test_update_profile_proposes_approval_never_writes() -> None:
    java = FakeJava()
    result = await update_profile(_ctx(java=java), {"headline": "Senior AI Engineer"})
    assert result == {"status": "proposed", "approvalId": "approval-1"}
    assert len(java.proposals) == 1
    assert java.proposals[0]["action"] == "UPDATE_PROFILE"
    payload = json.loads(java.proposals[0]["payload"]) if isinstance(java.proposals[0]["payload"], str) else java.proposals[0]["payload"]
    assert payload == {"fields": {"headline": "Senior AI Engineer"}}
    # No direct write call was recorded — only the proposal.
    assert java.calls == ["propose_approval:UPDATE_PROFILE"]


async def test_update_profile_rejects_unknown_fields() -> None:
    with pytest.raises(ToolError, match="unknown profile field"):
        await update_profile(_ctx(), {"email": "a@b.c"})


async def test_update_profile_rejects_bad_salary() -> None:
    with pytest.raises(ToolError):
        await update_profile(_ctx(), {"salaryMin": -1})
    with pytest.raises(ToolError):
        await update_profile(_ctx(), {"salaryMin": "high"})


async def test_apply_to_job_proposes_only() -> None:
    java = FakeJava()
    result = await apply_to_job(_ctx(java=java), JOB_ID)
    assert result == {"status": "proposed", "approvalId": "approval-1"}
    assert java.proposals[0]["action"] == "APPLY_JOB"
    payload = java.proposals[0]["payload"]
    assert payload == {"jobId": JOB_ID, "resumeId": RESUME_ID}


async def test_draft_application_proposes_with_preview() -> None:
    java = FakeJava()
    result = await draft_application(_ctx(java=java, llm=FakeOllama(down=True)), JOB_ID)
    assert result["status"] == "proposed"
    assert result["approvalId"] == "approval-1"
    assert "draftPreview" in result and len(str(result["draftPreview"])) > 0
    assert java.proposals[0]["action"] == "DRAFT_APPLICATION"
    assert "draftText" in java.proposals[0]["payload"]


async def test_apply_requires_job_id() -> None:
    with pytest.raises(ToolError):
        await apply_to_job(_ctx(), "")
