"""Deterministic triage signatures for ``analyze_failure``.

These run BEFORE any LLM call, in list order (first match wins), and the
verdict they produce is final — the LLM only enriches the explanation.
The signatures encode the known gotchas from this repo's own history
(port conflicts from running two compose projects at once, the
Testcontainers 1.21.3 / Engine 29 API incompatibility, Ollama down).

A signature matches when ANY of its ``patterns`` matches AND ALL of its
``requires`` patterns match (``requires`` is empty for most signatures).
``patterns`` are alternatives ("port is already allocated" OR "EADDRINUSE");
``requires`` is the extra evidence a composite signature needs (e.g. a
Testcontainers mention is only an API-version failure when a version
complaint is also present, so a bare mention does not mis-triage).
"""

from __future__ import annotations

import re
from dataclasses import dataclass


@dataclass(frozen=True)
class Signature:
    name: str
    verdict: str  # "regression" | "flaky" | "environment"
    patterns: tuple[str, ...]
    likely_cause: str
    what_to_do_first: str
    requires: tuple[str, ...] = ()


SIGNATURES: tuple[Signature, ...] = (
    # -- environment (checked first: a known env failure is never a bug) --
    Signature(
        name="port-conflict",
        verdict="environment",
        patterns=(r"port is already allocated", r"address already in use", r"EADDRINUSE", r"Bind for .* failed"),
        likely_cause="Port conflict — another process or container already holds the port.",
        what_to_do_first="Find what holds the port (lsof -i :<port>, or docker ps) and stop it before re-running.",
    ),
    Signature(
        name="docker-daemon-down",
        verdict="environment",
        patterns=(r"Cannot connect to the Docker daemon", r"is the docker daemon running"),
        likely_cause="Docker daemon is not running or not reachable.",
        what_to_do_first="Start Docker Desktop (or the Docker daemon) and re-run.",
    ),
    Signature(
        name="ollama-down",
        verdict="environment",
        patterns=(
            r"(ECONNREFUSED|Connection refused).*11434|11434.*(ECONNREFUSED|Connection refused)",
            r"Ollama.*(down|unavailable|not running|Connection refused)",
            r"failed to connect to Ollama",
        ),
        likely_cause="Local Ollama (:11434) is down or unreachable.",
        what_to_do_first="Start Ollama (ollama serve), confirm llama3.1:8b is pulled, then re-run.",
    ),
    Signature(
        name="testcontainers-api-version",
        verdict="environment",
        patterns=(r"Testcontainers",),
        requires=(r"API version|version .* not supported|client version",),
        likely_cause="Testcontainers / Docker Engine API-version incompatibility (known: Testcontainers 1.21.3 vs Engine 29).",
        what_to_do_first="Check the pinned Testcontainers version against the local Docker Engine version and align them (pin or upgrade), then re-run.",
    ),
    Signature(
        name="no-disk-space",
        verdict="environment",
        patterns=(r"No space left on device", r"ENOSPC"),
        likely_cause="Disk full on the machine running the tests.",
        what_to_do_first="Free disk space (docker system prune, remove old images/containers) and re-run.",
    ),
    Signature(
        name="network-unreachable",
        verdict="environment",
        patterns=(r"Name or service not known", r"Temporary failure in name resolution", r"Network is unreachable"),
        likely_cause="Network/DNS failure reaching a dependency.",
        what_to_do_first="Check network/DNS and that the dependency host is reachable, then re-run.",
    ),
    # -- flaky (intermittent, passes on retry without a code change) --
    Signature(
        name="retry-passed",
        verdict="flaky",
        patterns=(r"passed on retry", r"retry succeeded", r"flaky", r"intermittent (failure|error)", r"sometimes fails", r"failed.*then passed on (re-)?run"),
        likely_cause="Test is flaky — it fails intermittently and passes on retry without a code change.",
        what_to_do_first="Re-run the single test in isolation 3–5 times; if it passes inconsistently, quarantine it and file a flakiness issue with the log attached.",
    ),
    # -- regression (a real assertion failure with no env/flaky signature) --
    Signature(
        name="assertion-failure",
        verdict="regression",
        patterns=(r"AssertionError", r"FAILED \S+::", r"Assertion failed", r"expected .* but (got|was)", r"\bassert\b .* =="),
        likely_cause="A test assertion failed — behaviour changed versus the previous green run.",
        what_to_do_first="Open the failing test and the code it covers; check the most recent commit touching either, then fix the code or revert the breaking change.",
    ),
)


def match_signature(failure_text: str) -> Signature | None:
    """First matching signature, or None when nothing matches."""
    for sig in SIGNATURES:
        if any(re.search(p, failure_text, re.IGNORECASE) for p in sig.patterns) and all(
            re.search(p, failure_text, re.IGNORECASE) for p in sig.requires
        ):
            return sig
    return None


_FAILING_STEP_PATTERNS: tuple[str, ...] = (
    r"FAILED\s+\S+",
    r"FAIL:\s*\S+",
    r"ERROR:\s*\S+",
    r"\S+Error:.*",
)


def extract_failing_step(failure_text: str) -> str:
    """The failing step, in v3 §8 report style.

    First line that names a failure (FAILED <test>, FAIL:, ERROR:, or an
    ``*Error:`` line); otherwise the first non-empty line. Capped at
    300 chars so a pasted stack trace does not become the step name.
    """
    lines = [ln.strip() for ln in failure_text.splitlines() if ln.strip()]
    if not lines:
        return "(no failure text)"
    for line in lines:
        for pattern in _FAILING_STEP_PATTERNS:
            match = re.search(pattern, line)
            if match:
                return match.group(0)[:300]
    return lines[0][:300]
