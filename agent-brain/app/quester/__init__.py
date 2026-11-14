"""Quester — chief QA tester agent for EpitomeJob (agent-brain side).

Shape: a focused FastAPI router + service module, NOT a LangGraph agent.
Justification: Quester's two jobs (generate tests, triage a failure) are
single-shot and deterministic-first. There is no multi-step planning, no
inter-agent talk, and no state machine to traverse — a Supervisor +
specialists graph would add failure modes and latency for no capability
gain. The LLM is used only to write text (test bodies, triage
explanations) at temperature 0; it never decides pass/fail and never
overrides a deterministic triage verdict.

Public surface:
  * ``app.quester.router.router`` — POST /quester/generate-tests and
    POST /quester/analyze-failure (Bearer auth, same as /chat).
  * ``app.quester.generator.generate_tests`` / ``analyze_failure`` — the
    service functions; both take any object with an async
    ``chat(messages, temperature=...)`` method (``OllamaClient`` in prod).

Safety rules (frozen contract):
  * Generated tests only call endpoints listed in the request's
    ``known_endpoints`` — anything else is rejected, never returned.
  * Generated tests are NEVER auto-committed or auto-merged; they are
    returned as JSON for human review.
  * Generated output must parse (py_compile for Python, ``node --check``
    for JS); unparseable output is rejected, never returned.
  * No cloud LLM, ever — local Ollama llama3.1:8b only.
"""

from app.quester.generator import analyze_failure, generate_tests
from app.quester.router import router as quester_router

# NOTE: the router is exported as ``quester_router``, not ``router``: the
# package attribute ``router`` would shadow the ``app.quester.router``
# submodule for ``import app.quester.router`` users (e.g. tests that
# monkeypatch ``app.quester.router.get_llm``).
__all__ = ["analyze_failure", "generate_tests", "quester_router"]
