"""Routing tests: LLM routing + the contract §6 keyword fallback table."""

import pytest

from app.graph.router import FALLBACK_RULES, route_fallback, route_with_llm
from app.graph.state import SPECIALISTS
from tests.fakes import FakeOllama


@pytest.mark.parametrize(
    "message,expected",
    [
        ("Review my resume please", "resume"),
        ("What is missing from my CV?", "resume"),
        ("Help me practice for an interview", "interview"),
        ("Give me mock questions", "interview"),
        ("How well do I match this job?", "matching"),
        ("Why is my score low?", "matching"),
        ("Find Java jobs in Reston", "job_discovery"),
        ("What job openings are there?", "job_discovery"),
        ("What should I learn next?", "learning"),
        ("Recommend a Python course", "learning"),
        ("Apply now, no questions asked", "application"),
        ("Draft a cover letter", "application"),
        ("Tell me about the weather", "matching"),  # default
        ("", "matching"),  # default
    ],
)
def test_route_fallback_table(message: str, expected: str) -> None:
    assert route_fallback(message) == expected


def test_route_fallback_first_match_wins() -> None:
    # "resume" rule comes before "job" in the table.
    assert route_fallback("Find jobs matching my resume") == "resume"
    # "interview" comes before "matching".
    assert route_fallback("Will my interview score match the rubric?") == "interview"


def test_fallback_table_covers_all_specialists() -> None:
    routed = {specialist for _, specialist in FALLBACK_RULES}
    assert routed == set(SPECIALISTS)


async def test_route_with_llm_accepts_valid_name() -> None:
    llm = FakeOllama(down=False, chat_replies=["matching"])
    assert await route_with_llm("hello", llm) == "matching"


async def test_route_with_llm_rejects_garbage() -> None:
    llm = FakeOllama(down=False, chat_replies=["not a specialist at all"])
    assert await route_with_llm("hello", llm) is None


async def test_route_with_llm_none_when_ollama_down() -> None:
    llm = FakeOllama(down=True)
    assert await route_with_llm("hello", llm) is None
