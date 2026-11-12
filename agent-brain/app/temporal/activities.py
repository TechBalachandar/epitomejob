"""Temporal activities (contract §7).

``run_supervisor_graph`` executes the LangGraph graph in-process inside
the activity. ``index_resume_if_needed`` runs the lazy RAG indexing step.
Activities build their own clients from settings + the caller's token —
the token is passed in the workflow input, never stored.
"""

from __future__ import annotations

import logging

from temporalio import activity

from app.config import settings
from app.graph.supervisor import CoachDeps, run_coach
from app.java_client import JavaClient
from app.llm import OllamaClient
from app.rag.index import index_jobs_and_resume
from app.rag.store import RagStore

logger = logging.getLogger(__name__)


def _deps(token: str) -> CoachDeps:
    return CoachDeps(
        java=JavaClient(settings.java_base_url, token),
        llm=OllamaClient(
            base_url=settings.ollama_base_url,
            chat_model=settings.ollama_chat_model,
            embed_model=settings.ollama_embed_model,
            timeout_s=settings.ollama_timeout_s,
        ),
        store=RagStore(settings.database_url),
    )


@activity.defn
async def run_supervisor_graph(input: dict[str, object]) -> dict[str, object]:
    deps = _deps(str(input["token"]))
    try:
        return await run_coach(
            str(input["message"]), str(input["user_id"]), str(input["token"]), deps
        )
    finally:
        await deps.java.aclose()
        await deps.llm.aclose()


@activity.defn
async def index_resume_if_needed(input: dict[str, object]) -> dict[str, int]:
    deps = _deps(str(input["token"]))
    try:
        return await index_jobs_and_resume(deps.java, deps.store, deps.llm)
    except Exception as exc:  # noqa: BLE001 - indexing is best-effort
        activity.logger.warning("index_resume_if_needed failed: %s", exc)
        return {"jobs": 0, "resume": 0}
    finally:
        await deps.java.aclose()
        await deps.llm.aclose()
