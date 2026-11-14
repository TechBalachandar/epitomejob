# Phase 5B (MCP split): CANONICAL MCP copy of the 7 tools, moved verbatim
# from agent-brain's app/mcp/tools.py. agent-brain keeps a relocated copy
# at app/coach_tools.py for its in-process /chat specialists (see
# agent-brain-removal/REMOVAL.md) — same functions, same behaviour.
# Write rule unchanged: update_profile / draft_application / apply_to_job
# NEVER write directly; each proposes a Java approval via POST /approvals
# and returns {status: "proposed", approvalId}. No blind auto-apply, ever.

"""MCP tools (contract §4) — seven tools, exactly these names.

Each tool is a plain async Python function taking a :class:`ToolContext`
(the caller's token, userId, and clients). The FastMCP SSE server in
``app/mcp/server.py`` is a thin wrapper around these functions.

Write rule (applies to update_profile / draft_application / apply_to_job):
NEVER write directly. Each proposes a Java approval (``POST /approvals``)
and returns ``{status: "proposed", approvalId}``. Only the user approves.
No blind auto-apply, ever.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from app.java_client import JavaClient, JavaClientError
from app.llm import OllamaClient
from app.rag.store import RagChunk, RagStore

logger = logging.getLogger(__name__)

# ProfileUpdateRequest fields, verified against the W2 Java tree
# (ProfileUpdateRequest: headline, summary, location, workType,
# salaryMin, salaryMax — all optional, null means "leave unchanged").
PROFILE_FIELDS: frozenset[str] = frozenset(
    {"headline", "summary", "location", "workType", "salaryMin", "salaryMax"}
)

MAX_TEXT_PREVIEW = 4000
MAX_DRAFT_PREVIEW = 500


class ToolError(ValueError):
    """Tool argument validation failure — returned as an error result, never a crash."""


@dataclass
class ToolContext:
    java: JavaClient
    store: RagStore | None
    llm: OllamaClient
    token: str
    user_id: str


def _trim_job(job: dict[str, object]) -> dict[str, object]:
    """Trimmed JobDto per contract §4 (search_jobs return shape)."""
    return {
        "id": job.get("id"),
        "title": job.get("title"),
        "company": job.get("company"),
        "location": job.get("location"),
        "workType": job.get("workType"),
        "salaryMin": job.get("salaryMin"),
        "salaryMax": job.get("salaryMax"),
        "sponsorsH1b": job.get("sponsorsH1b"),
        "skills": job.get("skills") or [],
    }


async def search_jobs(
    ctx: ToolContext,
    query: str | None = None,
    location: str | None = None,
    work_type: str | None = None,
    sponsors_h1b: bool | None = None,
    limit: int = 10,
) -> list[dict[str, object]]:
    """Java GET /jobs with the token. ``limit`` defaults to 10, max 20."""
    if limit < 1 or limit > 20:
        raise ToolError(f"limit must be between 1 and 20, got {limit}")
    jobs = await ctx.java.list_jobs(
        query=query, location=location, work_type=work_type,
        sponsors_h1b=sponsors_h1b, size=limit,
    )
    return [_trim_job(job) for job in jobs[:limit]]


async def parse_resume(ctx: ToolContext, resume_id: str) -> dict[str, object]:
    """Java GET /resumes/{id}/text. Returns preview + counts."""
    if not resume_id or not str(resume_id).strip():
        raise ToolError("resumeId is required")
    doc = await ctx.java.get_resume_text(str(resume_id))
    text = str(doc.get("text", "") or "")
    return {
        "resumeId": str(doc.get("resumeId", resume_id)),
        "textPreview": text[:MAX_TEXT_PREVIEW],
        "charCount": len(text),
        "truncated": len(text) > MAX_TEXT_PREVIEW,
    }


async def match_score(ctx: ToolContext, job_id: str) -> dict[str, object]:
    """Java GET /matches/{jobId}. Returns {jobId, score, reasons, gaps} verbatim."""
    if not job_id or not str(job_id).strip():
        raise ToolError("jobId is required")
    result = await ctx.java.get_match(str(job_id))
    return dict(result)


async def ask_docs(ctx: ToolContext, query: str, top_k: int = 5) -> dict[str, object]:
    """RAG query (§5). Returns {chunks: [{docId, text, score, source}]}."""
    if not query or not query.strip():
        raise ToolError("query is required")
    if top_k < 1 or top_k > 10:
        raise ToolError(f"topK must be between 1 and 10, got {top_k}")
    if ctx.store is None:
        return {"chunks": []}
    embeddings = await ctx.llm.embed([query])
    if embeddings is None:
        logger.warning("ask_docs: Ollama embeddings down — returning no chunks")
        return {"chunks": []}
    chunks: list[RagChunk] = ctx.store.query_hybrid(query, embeddings[0], top_k=top_k)
    return {
        "chunks": [
            {"docId": c.doc_id, "text": c.text, "score": c.score, "source": c.source}
            for c in chunks
        ]
    }


async def update_profile(ctx: ToolContext, fields: dict[str, object]) -> dict[str, object]:
    """NEVER writes directly. Proposes Java approval UPDATE_PROFILE.

    ``fields`` may only contain ProfileUpdateRequest fields.
    """
    if not isinstance(fields, dict) or not fields:
        raise ToolError("fields must be a non-empty object")
    unknown = sorted(set(fields) - PROFILE_FIELDS)
    if unknown:
        raise ToolError(
            f"unknown profile field(s): {', '.join(unknown)}; "
            f"allowed: {', '.join(sorted(PROFILE_FIELDS))}"
        )
    for key in ("salaryMin", "salaryMax"):
        if key in fields:
            value = fields[key]
            if not isinstance(value, int) or isinstance(value, bool) or value < 0:
                raise ToolError(f"{key} must be a non-negative integer")
    for key in ("headline", "summary", "location", "workType"):
        if key in fields and not isinstance(fields[key], str):
            raise ToolError(f"{key} must be a string")
    approval = await ctx.java.propose_approval("UPDATE_PROFILE", {"fields": fields})
    return {"status": "proposed", "approvalId": approval.get("id")}


async def _latest_resume_id(ctx: ToolContext) -> str | None:
    resumes = await ctx.java.list_resumes()
    if not resumes:
        return None
    ready = [r for r in resumes if str(r.get("status", "")).upper() == "READY"]
    chosen = (ready or resumes)[0]
    return str(chosen.get("id")) if chosen.get("id") else None


async def draft_application(
    ctx: ToolContext, job_id: str, resume_id: str | None = None
) -> dict[str, object]:
    """LLM drafts application text grounded in resume + job + profile, then
    proposes Java approval DRAFT_APPLICATION. Never writes directly."""
    if not job_id or not str(job_id).strip():
        raise ToolError("jobId is required")
    job_id = str(job_id)
    resolved_resume = resume_id or await _latest_resume_id(ctx)
    if not resolved_resume:
        raise ToolError("no resume found — upload a resume before drafting an application")
    job = await ctx.java.get_job(job_id)
    resume_text = ""
    try:
        doc = await ctx.java.get_resume_text(str(resolved_resume))
        resume_text = str(doc.get("text", "") or "")
    except JavaClientError as exc:
        logger.warning("draft_application: resume text unavailable: %s", exc)
    profile: dict[str, object] = {}
    try:
        profile = await ctx.java.get_profile()
    except Exception as exc:  # noqa: BLE001 - profile is nice-to-have here
        logger.warning("draft_application: profile unavailable: %s", exc)
    draft = await ctx.llm.chat(
        [
            {
                "role": "system",
                "content": (
                    "You write a short, honest job application / cover letter. "
                    "Use only facts from the resume, job and profile below. "
                    "Do not invent experience, skills or qualifications. "
                    "Keep it under 250 words."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"Job: {job.get('title')} at {job.get('company')}. "
                    f"Summary: {job.get('summary', '')}\n"
                    f"Resume (first 4000 chars): {resume_text[:4000]}\n"
                    f"Profile headline: {profile.get('headline', '')}"
                ),
            },
        ]
    )
    if draft is None:
        draft = (
            f"Dear {job.get('company', '')} hiring team,\n\n"
            f"I am applying for the {job.get('title')} role. "
            "My resume (attached in EpitomeJob) sets out my experience for this role, "
            "and I would welcome the chance to discuss how I can contribute.\n\n"
            "Kind regards"
        )
    approval = await ctx.java.propose_approval(
        "DRAFT_APPLICATION",
        {"jobId": job_id, "resumeId": str(resolved_resume), "draftText": draft},
    )
    return {
        "status": "proposed",
        "approvalId": approval.get("id"),
        "draftPreview": draft[:MAX_DRAFT_PREVIEW],
    }


async def apply_to_job(
    ctx: ToolContext, job_id: str, resume_id: str | None = None
) -> dict[str, object]:
    """Proposes Java approval APPLY_JOB. NEVER applies directly.
    No blind auto-apply, ever."""
    if not job_id or not str(job_id).strip():
        raise ToolError("jobId is required")
    job_id = str(job_id)
    resolved_resume = resume_id or await _latest_resume_id(ctx)
    if not resolved_resume:
        raise ToolError("no resume found — upload a resume before applying")
    approval = await ctx.java.propose_approval(
        "APPLY_JOB", {"jobId": job_id, "resumeId": str(resolved_resume)}
    )
    return {"status": "proposed", "approvalId": approval.get("id")}
