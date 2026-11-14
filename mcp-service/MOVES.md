# Phase 5B — MCP split: MOVES.md

Base: live repo `TechBalachandar/epitomejob` @ main `b59dc45`.
Source of truth for every file below: the live repo, read via the GitHub
connector (never the stale `~/workspace/epitomejob-w2/backend/` tree).

Rule applied: **no half-shared package**. Each module is either moved
(mcp-service is now its only home) or deliberately duplicated verbatim,
with a header comment in the copy saying so. A bug fix must be applied
to both copies until a shared-helper decision is revisited.

## Decision table

| Module (live path) | Decision | Why |
|---|---|---|
| `app/mcp/server.py` | **MOVED** → `mcp-service/app/mcp/server.py` | The FastMCP SSE server is the thing being split out. Nothing in agent-brain imports it (verified: `main.py`, `temporal/worker.py`, `graph/*` never import `app.mcp.server`). Only change: the "Run from" docstring line (`agent-brain` → `mcp-service`). Same port 8001, path `/sse`, same `BearerAuthMiddleware`, same 15-min token cache (`TOKEN_CACHE_TTL_S = 900.0`). |
| `app/mcp/tools.py` (7 tools) | **DUPLICATED** → `mcp-service/app/mcp/tools.py` (canonical MCP copy) + relocated in agent-brain as `app/coach_tools.py` | The tools are needed on BOTH sides: the MCP server exposes them, and agent-brain's `/chat` specialists call the same functions in-process (`app/graph/specialists.py` calls `search_jobs`, `parse_resume`, `ask_docs`, `match_score`, `apply_to_job`, `draft_application`). Deleting them from agent-brain would break `/chat` and 13 tool tests. So the functions live in both places, verbatim. See `agent-brain-removal/REMOVAL.md` for the agent-brain side. |
| `app/mcp/__init__.py` | **MOVED** (empty file, recreated) | Package marker for `app.mcp` in mcp-service. Deleted from agent-brain with the rest of `app/mcp/`. |
| `app/auth.py` | **DUPLICATED** verbatim | agent-brain still needs `validate_token` for `/chat` (`app/main.py`); the MCP server needs it for per-connection Bearer validation (`server.py`). Small, stable, no reason to share. |
| `app/config.py` | **DUPLICATED, trimmed** | agent-brain keeps its full `Settings`. mcp-service's copy drops `agent_brain_port`, `temporal_address`, `temporal_enabled`, `langsmith_enabled` (the MCP server never talks to Temporal or LangSmith) and keeps `mcp_port`, `java_base_url`, `ollama_*`, `database_url`. Same env-var names, same localhost defaults. |
| `app/java_client.py` | **DUPLICATED** verbatim | agent-brain still needs the full client (`/chat`, specialists, RAG indexing use `list_matches`, `get_tracker`, `get_interview_questions`, etc.); the tools need `list_jobs`, `get_job`, `get_match`, `get_profile`, `list_resumes`, `get_resume_text`, `propose_approval`. Trimming the mcp-service copy would create a second, divergent client — kept whole instead. |
| `app/llm.py` | **DUPLICATED** verbatim | agent-brain still needs Ollama for `/chat` routing/judging; the tools need `chat` (`draft_application`) and `embed` (`ask_docs`). |
| `app/rag/store.py` | **DUPLICATED** verbatim | agent-brain still needs the store for `/rag/index`, `/rag/query`, `/chat`; `ask_docs` needs `query_hybrid` (+ `RagChunk`, `fuse_rrf`, `format_embedding`). `replace_document` is unused in mcp-service but kept verbatim to avoid divergence. |
| `app/rag/__init__.py` | **RECREATED** (empty) | Package marker so `app.rag.store` imports. |
| `app/__init__.py` | **RECREATED** (empty) | Package marker, as in agent-brain. |

## What stayed in agent-brain (not moved)

- `app/main.py` — FastAPI `/chat` :8000. It never imported `app.mcp`, so it
  needs **no edit at all** (verified by grep over the live tree).
- `app/graph/*` — supervisor, router, state, specialists. Specialists keep
  calling the same tool functions, now imported from `app.coach_tools`
  (see removal diff).
- `app/rag/*` — `chunking.py`, `index.py`, `store.py`: RAG indexing/query
  used by `/chat` stays.
- `app/temporal/*` — worker, workflows, activities. Never imported `app.mcp`.
- `app/auth.py`, `app/config.py`, `app/java_client.py`, `app/llm.py` —
  agent-brain keeps its own copies (see duplication rows above).
- `tests/` + `evals/` — unchanged in behaviour; `tests/test_tools.py`
  only re-points its import at `app.coach_tools`. No test deleted or
  weakened (see validation in the 5B report).

## What mcp-service does NOT get

- `langgraph`, `temporalio`, `fastapi` — dropped from requirements. The
  MCP path never imports them (supervisor/worker/`/chat` stay in
  agent-brain; the MCP server is a Starlette app served by uvicorn via
  FastMCP, and FastAPI is never imported).
- `app/graph/*`, `app/rag/chunking.py`, `app/rag/index.py`, `app/main.py`,
  `app/temporal/*` — chat-only code, stays in agent-brain.

## Approval rule (verified in the moved code)

`update_profile`, `draft_application`, `apply_to_job` NEVER write
directly. Each calls `JavaClient.propose_approval(...)` (`POST
/approvals`) and returns `{status: "proposed", approvalId}`. That POST is
the only write in `java_client.py` — there is no PUT/DELETE/PATCH and no
other POST anywhere in the moved code. Only the user approves. No blind
auto-apply, ever. The 13 tool tests (incl. `test_update_profile_proposes
_approval_never_writes`, `test_apply_to_job_proposes_only`) still cover
this in agent-brain via the relocated `app/coach_tools.py`.
