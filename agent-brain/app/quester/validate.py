"""Validation for generated tests: path safety, parse checks, endpoint guard.

A generated test reaches the caller only if ALL of these pass:
  1. the path is safe (relative, no ``..``, under an allowed root, with a
     known test-file extension);
  2. the content parses for its language (``py_compile`` for Python,
     ``node --check`` for JS; TypeScript and Java get a deterministic
     structural check because ``node --check`` cannot parse TS type
     annotations and this VM has no ``javac`` — both cases are flagged in
     the returned notes so a human knows the check was partial);
  3. every endpoint the test calls is in the request's ``known_endpoints``
     list (template segments such as ``{resumeId}`` match any one segment).

``validate_generated`` returns ``(problems, notes)``: problems reject the
test, notes are warnings attached to an accepted test.
"""

from __future__ import annotations

import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ALLOWED_ROOTS = ("tests/", "evals/", "e2e/", "web/", "backend/")
ALLOWED_EXTENSIONS = {".py", ".java", ".js", ".jsx", ".ts", ".tsx"}

# An HTTP call site: get/post/put/patch/delete/fetch/request followed by a
# (possibly f-prefixed) quoted URL literal. page.goto() is navigation, not
# an API call, so it is deliberately NOT in this list.
_HTTP_CALL_RE = re.compile(
    r"(?:get|post|put|patch|delete|fetch|request)\s*\(\s*f?[\"']([^\"']*)[\"']",
    re.IGNORECASE,
)
# A path inside a URL literal: scheme://host is stripped first, then the
# path must start at a segment boundary (start of string or after '}').
_PATH_IN_LITERAL_RE = re.compile(r"(?:^|\})((?:/[A-Za-z0-9_$\{\}.-]+)+)")


def validate_path(path: str) -> str | None:
    """Return a problem string, or None when the path is safe and known."""
    if not path or path.startswith("/") or "\\" in path or ".." in Path(path).parts:
        return f"unsafe path: {path!r}"
    if not any(path.startswith(root) for root in ALLOWED_ROOTS):
        return f"path {path!r} is not under an allowed test root {ALLOWED_ROOTS}"
    if Path(path).suffix not in ALLOWED_EXTENSIONS:
        return f"unsupported extension for generated test: {path!r}"
    return None


def _balanced(text: str) -> str | None:
    """Cheap delimiter-balance check, ignoring string/char literals."""
    pairs = {"(": ")", "[": "]", "{": "}"}
    closers = set(pairs.values())
    stack: list[str] = []
    i = 0
    while i < len(text):
        ch = text[i]
        if ch in ("'", '"', "`"):
            quote = ch
            i += 1
            while i < len(text) and text[i] != quote:
                if text[i] == "\\":
                    i += 1
                i += 1
        elif ch in pairs:
            stack.append(pairs[ch])
        elif ch in closers:
            if not stack or stack.pop() != ch:
                return f"unbalanced delimiter {ch!r}"
        i += 1
    if stack:
        return "unbalanced delimiters (unclosed bracket)"
    return None


def _check_python(content: str) -> str | None:
    try:
        compile(content, "<generated>", "exec")
    except SyntaxError as exc:
        return f"Python syntax error: {exc}"
    return None


def _check_javascript(content: str) -> tuple[str | None, str | None]:
    """(problem, note). Runs ``node --check`` when node exists."""
    node = shutil.which("node")
    if node is None:
        return None, "node not found — JS syntax check skipped"
    with tempfile.NamedTemporaryFile("w", suffix=".js", delete=False) as tmp:
        tmp.write(content)
        tmp_path = tmp.name
    try:
        proc = subprocess.run(
            [node, "--check", tmp_path],
            capture_output=True,
            text=True,
            timeout=30,
        )
    except subprocess.TimeoutExpired:
        return "JS syntax check timed out", None
    finally:
        Path(tmp_path).unlink(missing_ok=True)
    if proc.returncode != 0:
        last_line = (proc.stderr.strip().splitlines() or [""])[-1]
        return f"JS syntax error: {last_line}", None
    return None, None


def _check_structural(content: str, language: str, test_markers: tuple[str, ...]) -> str | None:
    if not content.strip():
        return f"empty {language} file"
    problem = _balanced(content)
    if problem:
        return f"{language} structural check failed: {problem}"
    if not any(marker in content for marker in test_markers):
        return f"{language} file contains no test (no {test_markers[0]!r} call found)"
    return None


def validate_generated(path: str, content: str) -> tuple[list[str], list[str]]:
    """Return (problems, notes) for one generated test file."""
    problems: list[str] = []
    notes: list[str] = []
    suffix = Path(path).suffix
    if suffix == ".py":
        problem = _check_python(content)
        if problem:
            problems.append(problem)
    elif suffix in (".js", ".jsx"):
        problem, note = _check_javascript(content)
        if problem:
            problems.append(problem)
        if note:
            notes.append(note)
    elif suffix in (".ts", ".tsx"):
        problem = _check_structural(content, "TypeScript", ("test(", "it(", "describe("))
        if problem:
            problems.append(problem)
        notes.append("TypeScript syntax check not run (node --check cannot parse TS types) — structural check only")
    elif suffix == ".java":
        problem = _check_structural(content, "Java", ("@Test", "void test"))
        if problem:
            problems.append(problem)
        elif "class " not in content:
            problems.append("Java file contains no class")
        notes.append("Java compile check not run (no javac here) — structural check only")
    else:  # pragma: no cover - validate_path rejects these first
        problems.append(f"unsupported extension for generated test: {path!r}")
    return problems, notes


def endpoint_from_literal(literal: str) -> str | None:
    """Extract the API path from a URL literal, or None when there is none.

    Handles plain paths ("/jobs"), f-strings (f"{BASE_URL}/jobs") and full
    URLs ("http://localhost:8080/jobs"). Returns the path without a
    trailing slash.
    """
    scheme_match = re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]+", literal)
    if scheme_match:
        literal = literal[scheme_match.end() :]
    match = _PATH_IN_LITERAL_RE.search(literal)
    if not match:
        return None
    return match.group(1).rstrip("/") or "/"


def extract_referenced_endpoints(content: str) -> set[str]:
    """Endpoints a generated test actually calls (HTTP call sites only)."""
    found: set[str] = set()
    for match in _HTTP_CALL_RE.finditer(content):
        path = endpoint_from_literal(match.group(1))
        if path:
            found.add(path)
    return found


def _segments(path: str) -> list[str]:
    return [seg for seg in path.split("/") if seg]


def endpoint_allowed(referenced: str, known_endpoints: list[str]) -> bool:
    """True when ``referenced`` matches a known endpoint exactly, or a
    known template where ``{param}`` segments match any single segment."""
    for known in known_endpoints:
        if referenced == known:
            return True
        ref_segs, known_segs = _segments(referenced), _segments(known)
        if len(ref_segs) != len(known_segs):
            continue
        if all(
            k.startswith("{") and k.endswith("}") or k == r
            for k, r in zip(known_segs, ref_segs)
        ):
            return True
    return False


def unknown_endpoints(content: str, known_endpoints: list[str]) -> set[str]:
    """Referenced endpoints that are NOT in the known list (empty when the
    list is empty — the guard cannot run without it)."""
    if not known_endpoints:
        return set()
    return {
        ref
        for ref in extract_referenced_endpoints(content)
        if not endpoint_allowed(ref, known_endpoints)
    }
