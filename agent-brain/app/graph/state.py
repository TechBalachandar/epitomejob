"""LangGraph state (contract §6).

State: ``{messages, userId, token, specialist, citations, actions,
specialistsUsed, next, reply_draft, ollama_down}``. ``next`` is a
specialist name or ``"respond"``. The supervisor itself is never listed
in ``specialistsUsed`` — only specialists are.
"""

from __future__ import annotations

from typing import TypedDict


SPECIALISTS: list[str] = [
    "resume",
    "job_discovery",
    "matching",
    "interview",
    "learning",
    "application",
]


class Citation(TypedDict):
    docId: str
    chunk: str
    score: float
    source: str


class ChatAction(TypedDict, total=False):
    type: str
    status: str  # "proposed" | "done" | "needs_approval"
    approvalId: str | None
    detail: str


class CoachState(TypedDict, total=False):
    messages: list[dict[str, str]]
    user_id: str
    token: str
    specialist: str
    citations: list[Citation]
    actions: list[ChatAction]
    specialists_used: list[str]
    next: str
    reply_draft: str
    ollama_down: bool
