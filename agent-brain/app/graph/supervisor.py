"""LangGraph supervisor: supervisor -> one specialist -> respond (contract §6).

Flow: ``supervisor`` routes (LLM, else the keyword fallback) and sets
``next``; the chosen specialist runs; ``respond`` composes the reply.
When Ollama is down the graph still answers from the deterministic
fallback (routing table + tool results + template reply) and the reply
ends with: "Note: the local model was unreachable, so this answer used
rules only."
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from langgraph.graph import END, START, StateGraph

from app.graph.router import route_fallback, route_message
from app.graph.specialists import FALLBACK_SUFFIX, SPECIALIST_NODES, SpecialistContext
from app.graph.state import SPECIALISTS, ChatAction, Citation, CoachState
from app.java_client import JavaClient
from app.llm import OllamaClient
from app.rag.index import index_jobs_and_resume
from app.rag.store import RagStore

logger = logging.getLogger(__name__)


@dataclass
class CoachDeps:
    """Everything one chat turn needs. Built per request from the token."""

    java: JavaClient
    llm: OllamaClient
    store: RagStore | None


def _supervisor_node(ctx: SpecialistContext):
    async def supervisor(state: CoachState) -> CoachState:
        message = ""
        messages = state.get("messages") or []
        if messages:
            message = str(messages[-1].get("content", ""))
        specialist = "matching"
        if state.get("ollama_down"):
            specialist = route_fallback(message)
        else:
            # Fallback wins: a keyword match decides; the LLM decides only
            # when no keyword matched.
            specialist = await route_message(message, ctx.llm)
        return CoachState(specialist=specialist, next=specialist)

    return supervisor


def _specialist_node(name: str, ctx: SpecialistContext):
    fn = SPECIALIST_NODES[name]  # type: ignore[assignment]

    async def node(state: CoachState) -> CoachState:
        return await fn(state, ctx)  # type: ignore[no-any-return]

    return node


def _respond_node(ctx: SpecialistContext):
    async def respond(state: CoachState) -> CoachState:
        # No LLM rewrite here on purpose: three eval rounds proved the
        # rewrite re-renders a good deterministic draft into echo,
        # approval claims, and invented facts on llama3.1:8b, and prompt
        # bans did not hold at temperature 0.8. The specialist drafts
        # are already plain-text answers, so the reply is the draft
        # verbatim — the respond node only adds the offline suffix.
        draft = state.get("reply_draft") or "I could not produce an answer for that — please try rephrasing."
        reply = draft
        if state.get("ollama_down") and FALLBACK_SUFFIX not in reply:
            reply = reply.rstrip() + "\n\n" + FALLBACK_SUFFIX
        return CoachState(reply_draft=reply)

    return respond


def build_graph(ctx: SpecialistContext):
    """Build the supervisor graph for one request's context."""
    graph: StateGraph = StateGraph(CoachState)
    graph.add_node("supervisor", _supervisor_node(ctx))
    for name in SPECIALISTS:
        graph.add_node(name, _specialist_node(name, ctx))
    graph.add_node("respond", _respond_node(ctx))
    graph.add_edge(START, "supervisor")
    graph.add_conditional_edges(
        "supervisor", lambda s: s.get("next", "matching"), {**{n: n for n in SPECIALISTS}, "respond": "respond"}
    )
    for name in SPECIALISTS:
        graph.add_edge(name, "respond")
    graph.add_edge("respond", END)
    return graph.compile()


async def run_coach(
    message: str,
    user_id: str,
    token: str,
    deps: CoachDeps,
) -> dict[str, object]:
    """Run one chat turn end to end. Returns the POST /chat response object."""
    ollama_up = await deps.llm.probe()
    ctx = SpecialistContext(
        java=deps.java, store=deps.store, llm=deps.llm, token=token, user_id=user_id
    )
    if deps.store is not None:
        try:
            await index_jobs_and_resume(deps.java, deps.store, deps.llm)
        except Exception as exc:  # noqa: BLE001 - lazy indexing never breaks chat
            logger.warning("lazy indexing failed: %s", exc)
    initial: CoachState = CoachState(
        messages=[{"role": "user", "content": message}],
        user_id=user_id,
        token=token,
        specialist="matching",
        citations=[],
        actions=[],
        specialists_used=[],
        next="matching",
        reply_draft="",
        ollama_down=not ollama_up,
    )
    graph = build_graph(ctx)
    final = await graph.ainvoke(initial)
    citations: list[Citation] = list(final.get("citations") or [])
    actions: list[ChatAction] = list(final.get("actions") or [])
    return {
        "reply": str(final.get("reply_draft") or ""),
        "citations": [
            {"docId": c["docId"], "chunk": c["chunk"], "score": c["score"], "source": c["source"]}
            for c in citations
        ],
        "actions": [
            {
                "type": a.get("type", ""),
                "status": a.get("status", "done"),
                "approvalId": a.get("approvalId"),
                "detail": a.get("detail", ""),
            }
            for a in actions
        ],
        "specialistsUsed": list(final.get("specialists_used") or []),
    }
