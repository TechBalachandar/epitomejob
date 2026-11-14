"""Quester request/response schemas (pydantic — both endpoints).

Validation is the first line of defence: a bad request is a 422 from
FastAPI before any service code runs, and a response that does not match
its model is a 500, never a silently malformed payload.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

Verdict = Literal["regression", "flaky", "environment"]


class GenerateTestsRequest(BaseModel):
    """Generate tests for ``target`` from ``context``.

    ``known_endpoints`` is the live endpoint list from the code/OpenAPI
    contract (e.g. ["/jobs", "/resumes/{resumeId}"]). The generator is
    instructed to use only these, and the endpoint guard rejects any
    generated test that calls anything else. When the list is empty the
    guard cannot run and a warning says so — callers should always pass it.
    """

    target: str = Field(..., min_length=1, max_length=500)
    context: str = Field(..., min_length=1, max_length=20_000)
    known_endpoints: list[str] = Field(default_factory=list)


class GeneratedTest(BaseModel):
    path: str = Field(..., min_length=1, max_length=500)
    content: str = Field(..., min_length=1, max_length=100_000)


class GenerateTestsResponse(BaseModel):
    tests: list[GeneratedTest] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


class AnalyzeFailureRequest(BaseModel):
    failure_text: str = Field(..., min_length=1, max_length=20_000)


class TriageResponse(BaseModel):
    """Same shape as the v3 §8 report format, plus provenance fields."""

    verdict: Verdict
    failing_step: str
    likely_cause: str
    what_to_do_first: str
    # Provenance: True when a deterministic signature decided the verdict
    # (the LLM then only enriched the explanation, never the verdict).
    deterministic: bool = False
    matched_signature: str | None = None
