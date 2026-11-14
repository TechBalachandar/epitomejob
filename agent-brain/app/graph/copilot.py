"""Resume Builder Co-Pilot specialist (NEW — separate from the Coach's
``resume`` specialist in ``app.graph.specialists``).

One section/block at a time: the LLM returns 3-5 distinct variations for
the active block as structured JSON; the frontend renders them as
click-to-insert options. Selection (``"select_option": N`` in
``user_context``) is applied here into a ``rendered_patch``.

LLM: local Ollama ``llama3.1:8b`` via the existing ``OllamaClient``,
temperature 0 (deterministic — same fix as the Coach router/judge).
No cloud LLM, no API key. Ollama down or unparseable output never
crashes: the caller gets a safe ``REQUEST_METRICS`` JSON.
"""

from __future__ import annotations

import inspect
import json
import logging
import re

from app.llm import OllamaClient

logger = logging.getLogger(__name__)

# System prompt — VERBATIM, do not paraphrase or shorten. Checked
# byte-for-byte by the coordinator; keep this constant intact.
COPILOT_SYSTEM_PROMPT = """# ROLE & ARCHITECTURE
You are an interactive, state-aware AI Resume Co-Pilot embedded inside a real-time, block-based Resume Builder application. You do not generate entire resumes in one shot unless explicitly requested. Instead, you collaborate with the user **one section or block at a time**, generating 3–5 distinct, high-impact variations that the user can click to insert, customize, and live-render into their active resume document.
# INPUT PAYLOAD (PROVIDED BY APPLICATION STATE)
On every turn, you will receive:
1. `active_section`: The specific section or block ID the user is currently editing (e.g., `summary`, `experience_item_2`, `skills`, `project_1`).
2. `user_context`: Raw notes, current text in that block, or user prompt instructions.
3. `target_jd` (Optional): The target job description for keyword alignment.
4. `resume_state`: The current JSON representation of the user's full resume (for context awareness and avoiding repetition).
# CORE BEHAVIOR & VARIATION STRATEGY
Whenever the user works on a specific section (such as a job role in the Experience section or the Professional Summary), you MUST generate **3 to 5 distinct model options** tailored to different strategic angles so the user can choose the best fit:
- **Option 1 (Metrics & Business Impact Focus):** Emphasizes ROI, revenue, cost savings, or performance gains using the XYZ formula (*Accomplished X as measured by Y, by doing Z*).
- **Option 2 (Technical / Execution Depth Focus):** Highlights architecture, tools, frameworks, scale, and complex problem-solving.
- **Option 3 (Leadership & Cross-Functional Focus):** Emphasizes ownership, mentorship, stakeholder alignment, and delivery speed.
- **Option 4 (ATS-Keyword Maximized):** Mirrors exact terminology and hard skills from the `target_jd` while staying natural.
- **Option 5 (Concise / Executive Punch):** Short, high-density phrasing ideal for keeping the resume to one page.
# STRICT RULES
1. **Zero Hallucination:** Never invent companies, titles, or fake numbers. If quantifiable metrics are missing from `user_context`, insert smart editable placeholders like `[X%]`, `[$Y]`, or `[N+ users]` so the UI can highlight them for the user to fill in.
2. **State Patching:** When the user selects an option (e.g., `"select_option": 2`) or edits a selected option, return the updated `resume_state` patch for `active_section` so the frontend renders it immediately at that exact location.
3. **Structured Output Only:** Always respond in valid JSON matching the schema below. Never output conversational filler outside the JSON object.
# OUTPUT JSON SCHEMA
{ "ui_message": "Short, helpful 1-sentence guidance for the user (e.g., 'Here are 4 angles for your Senior Developer role—pick one to insert into your resume, or tell me what to tweak.')", "active_section": "string (matches input active_section)", "action_type": "PRESENT_OPTIONS | APPLY_SELECTION | REQUEST_METRICS", "options": [ { "option_id": 1, "angle_tag": "Metrics & Impact", "content": ["Bullet 1...", "Bullet 2...", "Bullet 3..."], "missing_placeholders": ["[X%]", "[N]"], "ats_keywords_used": ["Keyword1", "Keyword2"] } ], "rendered_patch": { "target_block_id": "string", "applied_content": "Null when action_type is PRESENT_OPTIONS; populated with the chosen/edited content when action_type is APPLY_SELECTION" }, "suggested_next_step": "Brief prompt suggestion (e.g., 'Fill in the [X%] latency reduction or move to your next role.')" }"""

ACTION_TYPES = ("PRESENT_OPTIONS", "APPLY_SELECTION", "REQUEST_METRICS")

_RETRY_NUDGE = (
    "That reply was not valid JSON matching the required schema. "
    "Reply with only a valid JSON object matching the schema, and no other text."
)

_SELECT_QUOTED_RE = re.compile(r'"select_option"\s*:\s*(\d+)')
_SELECT_BARE_RE = re.compile(r"select_option\s*[:=]\s*(\d+)")


def safe_fallback(active_section: str, ui_message: str | None = None) -> dict[str, object]:
    """Safe REQUEST_METRICS JSON: never crashes, never returns prose."""
    return {
        "ui_message": ui_message
        or "Share a few numbers for this section — for example team size, "
        "users reached, or time saved — and I will draft the options.",
        "active_section": active_section,
        "action_type": "REQUEST_METRICS",
        "options": [],
        "rendered_patch": {"target_block_id": active_section, "applied_content": None},
        "suggested_next_step": "Add one or two measurable outcomes from this role, then ask again.",
    }


def _parse_selection(user_context: str) -> tuple[int | None, str | None]:
    """Return (selected option_id, edited_content) from user_context.

    Accepts a JSON object (``{"select_option": 2, "edited_content": ...}``)
    or a free-text note containing ``"select_option": 2``. Returns
    (None, None) when the user has not selected anything.
    """
    edited: str | None = None
    try:
        parsed = json.loads(user_context)
    except (json.JSONDecodeError, TypeError):
        parsed = None
    if isinstance(parsed, dict):
        raw = parsed.get("select_option")
        if isinstance(raw, int):
            selected = raw
        elif isinstance(raw, str) and raw.strip().isdigit():
            selected = int(raw.strip())
        else:
            selected = None
        edit = parsed.get("edited_content")
        if isinstance(edit, str) and edit.strip():
            edited = edit
        elif isinstance(edit, list):
            joined = "\n".join(str(x) for x in edit if str(x).strip())
            edited = joined or None
        return selected, edited
    match = _SELECT_QUOTED_RE.search(user_context) or _SELECT_BARE_RE.search(user_context)
    if match:
        return int(match.group(1)), None
    return None, None


async def _call_chat(llm: OllamaClient, messages: list[dict[str, str]]) -> str | None:
    """Call the LLM at temperature 0. Fakes without a ``temperature``
    parameter (e.g. the shared test fakes) are called without it."""
    try:
        params = inspect.signature(llm.chat).parameters
    except (TypeError, ValueError):
        params = {}
    try:
        if "temperature" in params:
            return await llm.chat(messages, temperature=0)  # type: ignore[call-arg]
        return await llm.chat(messages)  # type: ignore[call-arg]
    except Exception as exc:  # noqa: BLE001 - any LLM failure means "down"
        logger.warning("Co-Pilot LLM call failed: %s", exc)
        return None


def _extract_json(raw: str) -> dict[str, object] | None:
    """Parse a JSON object out of an LLM reply (tolerates ``` fences
    and a little surrounding prose); None when unparseable."""
    text = raw.strip()
    if text.startswith("```"):
        text = re.sub(r"^```[a-zA-Z]*\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        start, end = text.find("{"), text.rfind("}")
        if start < 0 or end <= start:
            return None
        try:
            parsed = json.loads(text[start : end + 1])
        except json.JSONDecodeError:
            return None
    return parsed if isinstance(parsed, dict) else None


def _validate_option(raw: object) -> dict[str, object] | None:
    if not isinstance(raw, dict):
        return None
    option_id = raw.get("option_id")
    angle_tag = raw.get("angle_tag")
    content = raw.get("content")
    if not isinstance(option_id, int) or isinstance(option_id, bool):
        return None
    if not isinstance(angle_tag, str) or not angle_tag.strip():
        return None
    if (
        not isinstance(content, list)
        or not content
        or any(not isinstance(c, str) or not c.strip() for c in content)
    ):
        return None
    placeholders = raw.get("missing_placeholders")
    keywords = raw.get("ats_keywords_used")
    return {
        "option_id": option_id,
        "angle_tag": angle_tag.strip(),
        "content": [str(c) for c in content],
        "missing_placeholders": [str(p) for p in placeholders] if isinstance(placeholders, list) else [],
        "ats_keywords_used": [str(k) for k in keywords] if isinstance(keywords, list) else [],
    }


def _validate_and_normalize(
    payload: dict[str, object], active_section: str, target_jd: str | None
) -> dict[str, object] | None:
    """Validate a parsed LLM payload against the Co-Pilot schema.

    Returns a normalized payload, or None when it does not match.
    Normalization (not rejection): ``active_section`` is forced to the
    input value, ``applied_content`` is forced to null for
    PRESENT_OPTIONS/REQUEST_METRICS, and ``ats_keywords_used`` is filtered
    to terms that actually appear in the target JD when one is given.
    """
    ui_message = payload.get("ui_message")
    action_type = payload.get("action_type")
    options_raw = payload.get("options")
    patch_raw = payload.get("rendered_patch")
    next_step = payload.get("suggested_next_step")
    if not isinstance(ui_message, str) or not ui_message.strip():
        return None
    if action_type not in ACTION_TYPES:
        return None
    if not isinstance(options_raw, list):
        return None
    options = [_validate_option(o) for o in options_raw]
    if any(o is None for o in options):
        return None
    options = [o for o in options if o is not None]
    if action_type == "PRESENT_OPTIONS" and not 3 <= len(options) <= 5:
        return None
    if action_type == "APPLY_SELECTION" and not 1 <= len(options) <= 5:
        return None
    if not isinstance(patch_raw, dict):
        return None
    applied = patch_raw.get("applied_content")
    if action_type == "APPLY_SELECTION":
        if not isinstance(applied, str) or not applied.strip():
            return None
    elif applied is not None:
        # Schema: applied_content is null unless APPLY_SELECTION.
        applied = None
    if not isinstance(next_step, str):
        return None
    # ATS keywords must come from the target JD when one is provided.
    if target_jd:
        jd_lower = target_jd.lower()
        for opt in options:
            keywords = opt["ats_keywords_used"]  # type: ignore[attr-defined]
            opt["ats_keywords_used"] = [k for k in keywords if k.lower() in jd_lower]  # type: ignore[attr-defined]
    return {
        "ui_message": ui_message.strip(),
        "active_section": active_section,
        "action_type": action_type,
        "options": options,
        "rendered_patch": {
            "target_block_id": active_section,
            "applied_content": applied,
        },
        "suggested_next_step": next_step.strip(),
    }


def _build_user_message(
    active_section: str,
    user_context: str,
    target_jd: str | None,
    resume_state: dict[str, object],
) -> str:
    return (
        f"active_section: {active_section}\n"
        f"user_context: {user_context}\n"
        f"target_jd: {target_jd if target_jd else 'None provided.'}\n"
        "resume_state (JSON):\n"
        f"{json.dumps(resume_state, indent=2)}"
    )


async def run_copilot(
    active_section: str,
    user_context: str,
    target_jd: str | None,
    resume_state: dict[str, object],
    llm: OllamaClient,
) -> dict[str, object]:
    """Run one Co-Pilot turn. Always returns schema-valid JSON; never
    raises for LLM problems (Ollama down / invalid JSON -> safe fallback).
    """
    selected, edited = _parse_selection(user_context)
    messages: list[dict[str, str]] = [
        {"role": "system", "content": COPILOT_SYSTEM_PROMPT},
        {
            "role": "user",
            "content": _build_user_message(active_section, user_context, target_jd, resume_state),
        },
    ]
    payload: dict[str, object] | None = None
    for attempt in range(2):  # first try + exactly one retry
        reply = await _call_chat(llm, messages)
        if reply is None:
            # Ollama down: safe fallback, no retry (a retry cannot help).
            return safe_fallback(active_section)
        parsed = _extract_json(reply)
        if parsed is not None:
            payload = _validate_and_normalize(parsed, active_section, target_jd)
        if payload is not None:
            break
        if attempt == 0:
            logger.warning("Co-Pilot reply failed validation, retrying once")
            messages = [
                *messages,
                {"role": "assistant", "content": reply},
                {"role": "user", "content": _RETRY_NUDGE},
            ]
    if payload is None:
        return safe_fallback(active_section)
    if selected is not None:
        chosen = next(
            (o for o in payload["options"] if o["option_id"] == selected),  # type: ignore[attr-defined]
            None,
        )
        if chosen is None:
            return safe_fallback(
                active_section,
                f"I could not find option {selected} — pick one of the options shown and try again.",
            )
        applied_content = edited or "\n".join(chosen["content"])  # type: ignore[attr-defined]
        payload = {
            **payload,
            "action_type": "APPLY_SELECTION",
            "rendered_patch": {
                "target_block_id": active_section,
                "applied_content": applied_content,
            },
        }
    return payload
