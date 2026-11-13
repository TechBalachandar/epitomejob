"""The six specialist nodes (contract §6).

Each specialist is an async function ``(state, ctx) -> partial state``.
Specialists NEVER write anything except through the MCP tools in
``app.mcp.tools`` — and those tools never write without proposing a Java
approval first. Agents never approve each other; only the user approves.

``learning`` is a CURATOR ONLY: it returns links from a static curated list
(the same sources as the W0 learning page) matched to the requested topic.
No course library, no generated course content.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from app.graph.state import ChatAction, Citation, CoachState
from app.java_client import JavaClient
from app.llm import OllamaClient
from app.mcp import tools as mcp_tools
from app.mcp.tools import ToolContext
from app.rag.store import RagStore

logger = logging.getLogger(__name__)

FALLBACK_SUFFIX = "Note: the local model was unreachable, so this answer used rules only."

# Static curated learning sources (W0 learning page). Topic -> links.
CURATED_LEARNING: list[dict[str, str]] = [
    {
        "topic": "python",
        "title": "freeCodeCamp — Scientific Computing with Python",
        "url": "https://www.freecodecamp.org/learn/scientific-computing-with-python/",
    },
    {
        "topic": "python",
        "title": "Kaggle Learn — Python",
        "url": "https://www.kaggle.com/learn/python",
    },
    {
        "topic": "ai",
        "title": "freeCodeCamp — Machine Learning with Python",
        "url": "https://www.freecodecamp.org/learn/machine-learning-with-python/",
    },
    {
        "topic": "ai",
        "title": "Kaggle Learn — Intro to Machine Learning",
        "url": "https://www.kaggle.com/learn/intro-to-machine-learning",
    },
    {
        "topic": "java",
        "title": "freeCodeCamp — Java Programming",
        "url": "https://www.freecodecamp.org/learn/java/",
    },
    {
        "topic": "sql",
        "title": "Kaggle Learn — Intro to SQL",
        "url": "https://www.kaggle.com/learn/intro-to-sql",
    },
    {
        "topic": "langgraph",
        "title": "LangChain Academy — Introduction to LangGraph (free, 55 lessons, ~6 hours)",
        "url": "https://academy.langchain.com/courses/intro-to-langgraph",
    },
    {
        "topic": "langgraph",
        "title": "LangGraph — official documentation",
        "url": "https://docs.langchain.com/langgraph/",
    },
    {
        "topic": "general",
        "title": "Coursera — audit any course for free (choose Audit at enrolment)",
        "url": "https://www.coursera.org/",
    },
    {
        "topic": "general",
        "title": "freeCodeCamp — full free curriculum",
        "url": "https://www.freecodecamp.org/",
    },
]


@dataclass
class SpecialistContext:
    java: JavaClient
    store: RagStore | None
    llm: OllamaClient
    token: str
    user_id: str

    def tools(self) -> ToolContext:
        return ToolContext(java=self.java, store=self.store, llm=self.llm, token=self.token, user_id=self.user_id)


def _message(state: CoachState) -> str:
    messages = state.get("messages") or []
    return str(messages[-1].get("content", "")) if messages else ""


def _used(state: CoachState, name: str) -> list[str]:
    used = list(state.get("specialists_used") or [])
    if name not in used:
        used.append(name)
    return used


def _to_citations(chunks: list[dict[str, object]]) -> list[Citation]:
    out: list[Citation] = []
    for c in chunks:
        out.append(
            Citation(
                docId=str(c.get("docId", "")),
                chunk=str(c.get("text", "")),
                score=float(c.get("score", 0.0)),
                source=str(c.get("source", "")),
            )
        )
    return out


async def _latest_resume_id(ctx: SpecialistContext) -> str | None:
    try:
        resumes = await ctx.java.list_resumes()
    except Exception as exc:  # noqa: BLE001 - specialist degrades, never crashes
        logger.warning("list_resumes failed: %s", exc)
        return None
    if not resumes:
        return None
    ready = [r for r in resumes if str(r.get("status", "")).upper() == "READY"]
    chosen = (ready or resumes)[0]
    return str(chosen.get("id")) if chosen.get("id") else None


# Skills the seeded Senior AI Engineer roles ask for, keyed by the
# lower-case form to scan for in the resume text (display name as value).
# Used to ground resume advice in what the resume actually says.
_RESUME_SIGNALS: dict[str, str] = {
    "java": "Java",
    "spring boot": "Spring Boot",
    "kafka": "Kafka",
    "python": "Python",
    "langgraph": "LangGraph",
}
# Senior-role skills whose absence is worth naming explicitly.
_MISSING_FOR_SENIOR: tuple[str, ...] = ("python", "langgraph")


def _resume_signals(preview: str) -> tuple[list[str], list[str]]:
    """Skills present in the resume text vs senior-role skills missing from it."""
    lowered = preview.lower()
    present = [display for key, display in _RESUME_SIGNALS.items() if key in lowered]
    missing = [
        _RESUME_SIGNALS[key] for key in _MISSING_FOR_SENIOR if key not in lowered
    ]
    return present, missing


async def resume_specialist(state: CoachState, ctx: SpecialistContext) -> CoachState:
    """Reads the resume text + profile; gives concrete improvement advice
    with citations from the resume."""
    tools = ctx.tools()
    citations: list[Citation] = []
    resume_id = await _latest_resume_id(ctx)
    advice_lines: list[str] = []
    if resume_id:
        try:
            parsed = await tools_parse_resume(tools, resume_id)
            preview = str(parsed.get("textPreview", ""))
            if preview:
                present, missing = _resume_signals(preview)
                advice_lines.append(
                    "I read your latest resume "
                    f"({parsed.get('charCount', 0)} characters). "
                    + (
                        f"It shows {', '.join(present)} experience."
                        if present
                        else "It does not name a clear technical stack."
                    )
                )
                if missing:
                    advice_lines.append(
                        "What is missing for senior roles: "
                        + ", ".join(missing)
                        + " — not mentioned in your resume, and the "
                        "Senior AI Engineer roles ask for "
                        + ("it" if len(missing) == 1 else "them")
                        + ". Add a project using "
                        + " and ".join(missing)
                        + " with measured outcomes."
                    )
                advice_lines.append(
                    "Make sure every bullet starts with a strong action verb, "
                    "quantifies impact (%, $, users, time saved), and names the "
                    "technologies used — recruiters scan for those first."
                )
            result = await mcp_tools.ask_docs(tools, _message(state), top_k=5)
            citations = _to_citations(result.get("chunks", []))
        except Exception as exc:  # noqa: BLE001
            logger.warning("resume specialist degraded: %s", exc)
            advice_lines.append("I could not read your resume just now — please check it is uploaded and READY.")
    else:
        advice_lines.append(
            "I could not find an uploaded resume. Upload one on the Resumes page, "
            "then ask me again and I will review it line by line."
        )
    try:
        profile = await ctx.java.get_profile()
        headline = profile.get("headline")
        if headline:
            advice_lines.append(f"Your headline is: “{headline}”. Keep it to one line naming your target role + top stack.")
    except Exception as exc:  # noqa: BLE001
        logger.warning("get_profile failed in resume specialist: %s", exc)
    draft = " ".join(advice_lines) if advice_lines else "Here is a quick resume review."
    return CoachState(
        reply_draft=draft,
        citations=citations,
        actions=list(state.get("actions") or []),
        specialists_used=_used(state, "resume"),
        next="respond",
    )


async def tools_parse_resume(tools: ToolContext, resume_id: str) -> dict[str, object]:
    return await mcp_tools.parse_resume(tools, resume_id)


async def _rag_citations(tools: ToolContext, message: str) -> list[Citation]:
    """Best-effort RAG grounding for a specialist reply (empty on any failure)."""
    try:
        result = await mcp_tools.ask_docs(tools, message, top_k=5)
        return _to_citations(result.get("chunks", []))
    except Exception as exc:  # noqa: BLE001
        logger.warning("ask_docs failed: %s", exc)
        return []


async def job_discovery_specialist(state: CoachState, ctx: SpecialistContext) -> CoachState:
    """search_jobs with the user's filters (incl. sponsorship on visa/H-1B
    mentions); lists jobs with title/company/location/salary/sponsors flag."""
    tools = ctx.tools()
    message = _message(state)
    lowered = message.lower()
    sponsors = True if ("h-1b" in lowered or "h1b" in lowered or "visa" in lowered or "sponsor" in lowered) else None
    jobs: list[dict[str, object]] = []
    try:
        jobs = await mcp_tools.search_jobs(tools, sponsors_h1b=sponsors, limit=10)
    except Exception as exc:  # noqa: BLE001
        logger.warning("job_discovery degraded: %s", exc)
    if not jobs:
        draft = "I did not find jobs matching that just now. Try broadening the search, or check the Jobs page for the full seeded list."
    else:
        lines = ["Here are the closest jobs I found:"]
        for job in jobs[:10]:
            salary = _salary_text(job)
            work_type = _work_type_text(job.get("workType"))
            lines.append(
                f"- {job.get('title')} at {job.get('company')} — {job.get('location')}"
                f"{salary}{work_type} — sponsors H-1B: {'yes' if job.get('sponsorsH1b') else 'no'}"
            )
        draft = "\n".join(lines)
    return CoachState(
        reply_draft=draft,
        citations=await _rag_citations(tools, message),
        actions=list(state.get("actions") or []),
        specialists_used=_used(state, "job_discovery"),
        next="respond",
    )


def _salary_text(job: dict[str, object]) -> str:
    lo, hi = job.get("salaryMin"), job.get("salaryMax")
    if lo is None and hi is None:
        return ""
    if lo is not None and hi is not None:
        return f" (${lo:,}–${hi:,})"
    val = lo if lo is not None else hi
    return f" (${val:,})"


_WORK_TYPE_LABELS = {"REMOTE": "Remote", "HYBRID": "Hybrid", "ONSITE": "On-site"}


def _work_type_text(work_type: object) -> str:
    """Display segment for a job's workType ('' when unknown)."""
    if not isinstance(work_type, str) or not work_type.strip():
        return ""
    label = _WORK_TYPE_LABELS.get(work_type.strip().upper(), work_type.strip().title())
    return f" — {label}"


def _score_level(score: object) -> str:
    """Plain-language level for a match score (0-1 or 0-100 scale)."""
    try:
        value = float(score)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return "a match"
    if value > 1:
        value /= 100
    if value >= 0.8:
        return "a strong match"
    if value >= 0.6:
        return "a good match"
    return "a lower match"


async def matching_specialist(state: CoachState, ctx: SpecialistContext) -> CoachState:
    """match_score for the top jobs; explains score, reasons, gaps."""
    tools = ctx.tools()
    lines: list[str] = []
    try:
        jobs = await mcp_tools.search_jobs(tools, limit=5)
    except Exception as exc:  # noqa: BLE001
        logger.warning("matching: search_jobs failed: %s", exc)
        jobs = []
    if not jobs:
        lines.append("I could not load jobs to score just now. Please try again in a moment.")
    for job in jobs:
        job_id = str(job.get("id", ""))
        if not job_id:
            continue
        try:
            match = await mcp_tools.match_score(tools, job_id)
        except Exception as exc:  # noqa: BLE001
            logger.warning("matching: match_score %s failed: %s", job_id, exc)
            continue
        score = match.get("score")
        reasons = match.get("reasons") or []
        gaps = match.get("gaps") or []
        lines.append(
            f"Your match score for {job.get('title')} at {job.get('company')} "
            f"is {score} — {_score_level(score)}."
        )
        if isinstance(reasons, list) and reasons:
            lines.append(
                "Your profile fits this role because "
                + "; ".join(str(r) for r in reasons)
                + "."
            )
        if isinstance(gaps, list) and gaps:
            lines.append(
                "What keeps your score from being perfect is the gaps in "
                + ", ".join(str(g) for g in gaps)
                + ": these are skills this role asks for that your profile "
                "does not show yet. Closing those gaps is what would lift "
                "your score toward perfect."
            )
        elif isinstance(reasons, list) and reasons:
            lines.append(
                "You have no gaps for this role — your profile covers "
                "everything it asks for."
            )
    draft = "\n".join(lines) if lines else "No matches scored yet."
    citations: list[Citation] = []
    try:
        result = await mcp_tools.ask_docs(tools, _message(state), top_k=5)
        citations = _to_citations(result.get("chunks", []))
    except Exception as exc:  # noqa: BLE001
        logger.warning("matching: ask_docs failed: %s", exc)
    return CoachState(
        reply_draft=draft,
        citations=citations,
        actions=list(state.get("actions") or []),
        specialists_used=_used(state, "matching"),
        next="respond",
    )


async def interview_specialist(state: CoachState, ctx: SpecialistContext) -> CoachState:
    """Lists real mock-interview questions for the user's jobs. It does
    NOT run a live interview in chat (the AI interviewer is a follow-up)."""
    tools = ctx.tools()
    lines = [
        "Here are mock interview questions to practice — answer them out loud, "
        "then run a mock session on the Interview page for rubric-scored feedback:"
    ]
    try:
        questions = await ctx.java.get_interview_questions()
        shown = 0
        for item in questions or []:
            if shown >= 5:
                break
            q = item.get("question") or item.get("text") or ""
            if q:
                shown += 1
                lines.append(f"{shown}. {q}")
        if not shown:
            lines.append("I could not load practice questions just now — open the Interview page to see the full list.")
        sessions = await ctx.java.list_interview_sessions()
        if sessions:
            lines.append(f"You already have {len(sessions)} mock session(s) — review them on the Interview page before your next one.")
    except Exception as exc:  # noqa: BLE001
        logger.warning("interview specialist degraded: %s", exc)
    return CoachState(
        reply_draft="\n".join(lines),
        citations=await _rag_citations(tools, _message(state)),
        actions=list(state.get("actions") or []),
        specialists_used=_used(state, "interview"),
        next="respond",
    )


_TOPIC_LABELS = {
    "langgraph": "LangGraph",
    "python": "Python",
    "ai": "AI",
    "java": "Java",
    "sql": "SQL",
    "general": "general topics",
}


async def learning_specialist(state: CoachState, ctx: SpecialistContext) -> CoachState:
    """CURATOR ONLY: static curated links matched to the requested topic."""
    message = _message(state).lower()
    # LangGraph is checked first: "a Python course for LangGraph" must not
    # fall through to the generic python topic.
    topic = "general"
    if "langgraph" in message:
        topic = "langgraph"
    else:
        for key in ("python", "ai", "java", "sql"):
            if key in message or (key == "ai" and ("machine learning" in message or "llm" in message)):
                topic = key
                break
    links = [l for l in CURATED_LEARNING if l["topic"] == topic] or [
        l for l in CURATED_LEARNING if l["topic"] == "general"
    ]
    lines = [
        f"Here are free resources matched to {_TOPIC_LABELS.get(topic, topic)} "
        "(curated list — I do not generate course content):"
    ]
    for link in links:
        lines.append(f"- {link['title']}: {link['url']}")
    if topic == "langgraph":
        # The LangGraph course assumes intermediate Python — include the
        # Python foundations as well.
        lines.append(
            "LangGraph needs solid Python first, so here are the Python foundations too:"
        )
        for link in CURATED_LEARNING:
            if link["topic"] == "python":
                lines.append(f"- {link['title']}: {link['url']}")
    return CoachState(
        reply_draft="\n".join(lines),
        citations=await _rag_citations(ctx.tools(), message),
        actions=list(state.get("actions") or []),
        specialists_used=_used(state, "learning"),
        next="respond",
    )


async def application_specialist(state: CoachState, ctx: SpecialistContext) -> CoachState:
    """draft_application / apply_to_job via the approval gate ONLY.

    The agent never applies directly. If the user names no job, it proposes
    for the top discovered job and says so explicitly; the user still has
    to approve in the inbox before anything happens.
    """
    tools = ctx.tools()
    message = _message(state).lower()
    actions: list[ChatAction] = list(state.get("actions") or [])
    jobs: list[dict[str, object]] = []
    try:
        jobs = await mcp_tools.search_jobs(tools, limit=5)
    except Exception as exc:  # noqa: BLE001
        logger.warning("application: search_jobs failed: %s", exc)
    if not jobs:
        return CoachState(
            reply_draft="I could not find a job to apply to. Tell me the job title or pick one on the Jobs page first.",
            citations=list(state.get("citations") or []),
            actions=actions,
            specialists_used=_used(state, "application"),
            next="respond",
        )
    top = jobs[0]
    job_id = str(top.get("id", ""))
    resume_id = await _latest_resume_id(ctx)
    try:
        if "apply" in message and "draft" not in message and "cover" not in message:
            result = await mcp_tools.apply_to_job(tools, job_id, resume_id)
            actions.append(
                ChatAction(
                    type="APPLY_JOB",
                    status="proposed",
                    approvalId=str(result.get("approvalId")),
                    detail=f"Application to {top.get('title')} at {top.get('company')} proposed — approve it in your inbox to apply.",
                )
            )
            draft = (
                f"I have proposed an application to {top.get('title')} at {top.get('company')}. "
                "Nothing is submitted yet — approve it in your Approvals inbox and it will go through. "
                "I never apply without your approval."
            )
        else:
            result = await mcp_tools.draft_application(tools, job_id, resume_id)
            preview = str(result.get("draftPreview", ""))
            actions.append(
                ChatAction(
                    type="DRAFT_APPLICATION",
                    status="proposed",
                    approvalId=str(result.get("approvalId")),
                    detail=f"Draft application for {top.get('title')} at {top.get('company')} proposed for your review.",
                )
            )
            draft = (
                f"I drafted an application for {top.get('title')} at {top.get('company')} "
                "and saved it as an approval proposal for your review:\n\n"
                f"{preview}"
                "\n\nApprove it in your inbox to keep it, or reject it to discard it."
            )
    except Exception as exc:  # noqa: BLE001
        logger.warning("application specialist: proposal failed: %s", exc)
        actions.append(
            ChatAction(
                type="DRAFT_APPLICATION" if "apply" not in message else "APPLY_JOB",
                status="needs_approval",
                approvalId=None,
                detail=f"I could not build the approval proposal: {exc}",
            )
        )
        draft = "I wanted to prepare that application, but I could not build the approval proposal. Please try again, or use the Jobs page."
    return CoachState(
        reply_draft=draft,
        citations=await _rag_citations(tools, _message(state)),
        actions=actions,
        specialists_used=_used(state, "application"),
        next="respond",
    )


SPECIALIST_NODES: dict[str, object] = {
    "resume": resume_specialist,
    "job_discovery": job_discovery_specialist,
    "matching": matching_specialist,
    "interview": interview_specialist,
    "learning": learning_specialist,
    "application": application_specialist,
}
