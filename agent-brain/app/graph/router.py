"""Routing: LLM routing with the deterministic keyword fallback (contract §6).

The fallback table MUST be used when Ollama is down or returns unparseable
output. First match wins; default is ``matching`` (the most common intent).
"""

from __future__ import annotations

import re

from app.graph.state import SPECIALISTS
from app.llm import OllamaClient

# Contract §6 fallback table, in order (first match wins).
FALLBACK_RULES: list[tuple[str, str]] = [
    (r"\b(resume|cv)\b", "resume"),
    (r"\b(interview|mock|practice)\b", "interview"),
    (r"\b(match|score|fit|why)\b", "matching"),
    (r"\b(job|search|find|role|opening)\b", "job_discovery"),
    (r"\b(learn|course|study|skill)\b", "learning"),
    (r"\b(apply|application|cover)\b", "application"),
]

_COMPILED: list[tuple[re.Pattern[str], str]] = [
    (re.compile(pattern, re.IGNORECASE), specialist) for pattern, specialist in FALLBACK_RULES
]


def route_fallback(message: str) -> str:
    """Deterministic keyword routing. First match wins; default ``matching``."""
    for pattern, specialist in _COMPILED:
        if pattern.search(message or ""):
            return specialist
    return "matching"


async def route_with_llm(message: str, llm: OllamaClient) -> str | None:
    """Ask Ollama to pick one specialist. Returns None when unusable.

    The caller MUST fall back to :func:`route_fallback` on None.
    """
    reply = await llm.chat(
        [
            {
                "role": "system",
                "content": (
                    "You route a job-coach request to exactly one specialist. "
                    f"Reply with exactly one of: {', '.join(SPECIALISTS)}. "
                    "Reply with only that word, nothing else."
                ),
            },
            {"role": "user", "content": message},
        ]
    )
    if reply is None:
        return None
    word = reply.strip().lower().split()[0].strip(".,;:") if reply.strip() else ""
    return word if word in SPECIALISTS else None
