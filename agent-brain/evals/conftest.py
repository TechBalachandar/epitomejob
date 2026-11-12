"""Eval terminal summary (contract §8): counts + which questions failed,
and ``judge: skipped (Ollama down)`` when the judge tests were skipped.
"""

from __future__ import annotations

import httpx


def _ollama_up() -> bool:
    try:
        from app.config import settings

        resp = httpx.get(f"{settings.ollama_base_url}/api/tags", timeout=2.0, trust_env=False)
        return resp.status_code == 200
    except Exception:  # noqa: BLE001
        return False


def pytest_terminal_summary(terminalreporter, exitstatus, config):  # type: ignore[no-untyped-def]
    stats = terminalreporter.stats
    passed = len(stats.get("passed", []))
    failed = len(stats.get("failed", []))
    skipped = len(stats.get("skipped", []))
    judge_skipped = [
        r for r in stats.get("skipped", []) if "judge" in str(getattr(r, "keywords", {}))
    ]
    terminalreporter.write_sep("=", "EVAL SUMMARY")
    terminalreporter.write_line(f"passed: {passed}  failed: {failed}  skipped: {skipped}")
    if failed:
        for rep in stats.get("failed", []):
            terminalreporter.write_line(f"FAILED: {rep.nodeid}")
    if judge_skipped or not _ollama_up():
        terminalreporter.write_line("judge: skipped (Ollama down)")
    else:
        terminalreporter.write_line("judge: ran (Ollama up)")
    terminalreporter.write_line(f"exit status: {exitstatus} (non-zero on any failure)")
