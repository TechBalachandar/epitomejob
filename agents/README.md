# Agent governance

EpitomeJob uses three independent specialist agents under human-controlled release governance.

- **TheVoper** (agents/thevoper.md) — implementation agent. Works from a task-specific branch, reproduces defects, implements fixes, adds regression tests, and opens a PR to qa. It does not approve its own work.
- **ArXtechT** (agents/arxtecht.md) — independent architect/SME reviewer. Reviews the exact PR diff and head SHA, records actionable findings, and never writes implementation code or approves a release.
- **Quester** (agents/quester.md) — independent QA agent. Runs automated and available live checks, records evidence, and files one issue per defect. It never changes product code or approves a release.
- **Release Controller** — policy/state authority. It enforces allowed transitions and records audit events. It does not replace GitHub branch protections or human environment approvals.

The user is the final approver for UAT, PreProd, and PROD. Agents cannot approve one another, bypass an approval gate, deploy to PROD, or promote an unverified artifact.

## Development and review flow

Issue / approved task
-> TheVoper creates task/<issue>-<slug> from qa
-> implementation + focused regression tests
-> PR task branch -> qa
-> ArXtechT reviews exact head SHA (independent, read-only)
-> TheVoper addresses findings in the same task branch
-> Quester regression + smoke checks
-> human review / merge to qa
-> UAT -> PreProd -> PROD only through explicit human approvals

The PR is not ready to merge until required checks are green and ArXtechT has no unresolved blocking findings. GitHub branch rulesets must enforce required checks; this document alone is not enforcement.

## Workflow ownership

The canonical Quester workflow is .github/workflows/quester-qa.yml. Edit and review it in that location like any other repository file. GitHub does not categorically block workflow-file commits; the installed GitHub App must have the requested Workflows permission, and repository policy may impose additional restrictions. Do not maintain a second copy under agents/.

## Runtime boundaries

- Temporal remains the durable workflow runtime; do not introduce a competing workflow engine.
- LangGraph remains responsible for agent reasoning.
- The existing Java/Spring Boot backend release-control module owns policy, lifecycle transitions, audit, and REST control-plane APIs. A Kotlin extraction is optional future work only if independent deployment/ownership justifies it.
- PostgreSQL + Flyway store durable control-plane state; Redis is coordination/cache only.
- Local Ollama remains the default model provider. No cloud LLM API or spend without explicit user authorization.
- Agent execution is isolated in Docker with least-privilege, short-lived credentials.
- Agent-to-agent communication uses explicit task/review artifacts and stable IDs; reviewers inspect the exact immutable commit SHA.
- The duplicate MCP tool implementations in agent-brain/app/coach_tools.py and mcp-service/app/mcp/tools.py must remain behaviorally identical; changes to shared tool behavior require tests in both locations.
