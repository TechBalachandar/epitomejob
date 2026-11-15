# Governed Agent Platform — Architecture

**Status:** Proposed implementation baseline  
**Owner:** Repository owner  
**Scope:** TheVoper, ArXtechT, Quester, release orchestration, audit and human promotion gates.

## 1. Current-state facts

- The application backend is a Java 21 / Spring Boot 3.5.x modular monolith.
- PostgreSQL and Flyway are already used by the backend.
- Redis is already present in the root Compose stack.
- `agent-brain/` is Python and uses LangGraph for agent reasoning.
- Temporal is already running and has a Python worker on task queue `coach-queue` for the Coach workflow.
- The existing Coach Temporal workflow is not a release workflow. Do not represent it as one.
- The repository has GitHub Actions for CI/QA and Docker Compose for local integration.

## 2. Architecture decision

Start the release-control API as a bounded module in the existing Spring Boot backend. Reuse its authentication, PostgreSQL connection, Flyway migrations, validation, observability and deployment lifecycle. Do not create a second identity system or a second PostgreSQL cluster for the MVP.

Keep the module behind a dedicated `/internal/release-control/**` API namespace and explicit authorization. Extract it into a separately deployed service only when independent scaling, deployment or ownership justifies the operational cost. If a separate service is later approved, Kotlin + Spring Boot is an acceptable implementation choice; do not introduce a second service merely to change languages.

Use the existing systems for their intended responsibilities:

| Concern | System of record / owner |
|---|---|
| Agent reasoning and tool selection | LangGraph in agent-brain |
| Durable, resumable orchestration | Existing Temporal cluster; add a distinct release workflow and task queue |
| Release state, policy, approvals, audit | Spring Boot release-control module + PostgreSQL/Flyway |
| Short-lived coordination/cache | Existing Redis; never the source of truth |
| Source, diffs, PRs, checks, environments | GitHub |
| Build artifacts | Container registry; immutable image digest |
| Human-facing progress | REST initially; SSE only when a real dashboard requires streaming |
| Local model inference | Ollama on the developer/QA host |

Do not add Kafka to the release-control path unless a measured event-volume or integration requirement makes it necessary. Kafka is already in the stack but is not a substitute for a transactional outbox or durable workflow state.

## 3. Agent responsibilities

### TheVoper — implementer
- Starts from `qa` and creates a task-specific branch.
- Works only on the assigned task and adds regression tests.
- Pushes only to its task branch and opens/updates a PR targeting `qa`.
- Responds to reviewer findings; cannot approve its own work, merge, promote environments or deploy PROD.

### ArXtechT — independent architecture reviewer
- Is read-only.
- Reviews the exact PR head SHA, not a moving branch name.
- Produces severity-ranked findings with path/line, evidence, impact and remediation.
- A head-SHA change invalidates the previous verdict.
- Does not approve GitHub PRs or environment promotions.

### Quester — independent QA
- Runs the required suites and permitted isolated smoke tests.
- Reports the tested commit SHA, workflow run URL, per-suite outcomes and reproducible evidence.
- Files actionable defects; never changes product code or approves a release.

### Human release owner
- Owns merge and UAT, PreProd and PROD approval.
- Is the only actor allowed to authorize environment promotion.
- Can reject, pause or cancel a release at any point.

## 4. End-to-end flow

1. A human-approved issue creates an implementation task.
2. TheVoper creates a task branch from the current `qa` head.
3. CI validates the PR.
4. ArXtechT reviews the exact head SHA. Findings are attached to that SHA.
5. TheVoper fixes findings; the changed SHA triggers a fresh architecture review and fresh required CI.
6. Quester runs regression and smoke suites against the same candidate SHA.
7. A human reviews and merges the PR to `qa`.
8. A release candidate is built once. The registry digest and source SHA are recorded.
9. The same immutable image digest is promoted through UAT, PreProd and PROD.
10. Each environment promotion requires a separate explicit human approval and successful environment-specific gates.
11. Rollback redeploys the previously approved digest; it does not rebuild an older source revision.

## 5. Temporal integration

Add a separate release workflow and task queue (for example, `release-queue`) to the existing Temporal Python worker. Do not modify the Coach workflow contract or overload `coach-queue`.

The release workflow should:
- Accept a stable release ID, source SHA and immutable artifact digest.
- Execute idempotent activities for architecture review, QA orchestration and deployment requests.
- Wait on explicit human-approval signals before UAT, PreProd and PROD promotion.
- Use bounded activity timeouts, retry policies and heartbeat for long-running activities.
- Treat cancellation, timeout and rejection as explicit terminal or paused states.
- Never hold a database transaction open while waiting on a Temporal signal.
- Persist authoritative state and audit records in PostgreSQL; Temporal history is orchestration history, not a replacement for the product audit ledger.

The release-control API should start and signal workflows through a small internal adapter. The adapter must use authenticated service-to-service communication and stable idempotency keys. A transactional outbox is required before relying on asynchronous database-to-Temporal dispatch, so a committed release cannot be lost between the database transaction and workflow start.

## 6. Persistence and consistency

PostgreSQL owns:
- release candidate identity and current state;
- source SHA, PR URL/number, artifact digest and environment;
- architecture and QA verdict metadata;
- human approval records;
- append-only audit events;
- idempotency keys and outbox events.

Use Flyway migrations. Enforce legal transitions in the service layer and with database constraints where practical. Use optimistic locking on mutable release state. Audit records are append-only; application code must not expose update/delete endpoints for them.

Redis may cache dashboard projections or coordinate short-lived locks. A Redis loss must not lose release state or approvals.

## 7. API shape (initial contract)

All endpoints are authenticated and unavailable to anonymous callers. Agent callbacks require a separate service identity, not a user JWT copied into an agent container.

- `POST /internal/release-control/releases` — create a release candidate (idempotency key required).
- `GET /internal/release-control/releases/{releaseId}` — fetch current state and gate evidence.
- `GET /internal/release-control/releases/{releaseId}/events` — read audit timeline.
- `POST /internal/release-control/releases/{releaseId}/architecture-verdict` — record a service-authenticated verdict bound to head SHA.
- `POST /internal/release-control/releases/{releaseId}/qa-verdict` — record a service-authenticated QA result bound to source SHA.
- `POST /internal/release-control/releases/{releaseId}/approvals/{environment}` — human approval or rejection for exactly one environment and one artifact digest.
- `POST /internal/release-control/releases/{releaseId}/cancel` — authorized cancellation.

Do not expose a generic endpoint that lets an agent set arbitrary release status. The server derives state from validated evidence and authorized approvals.

## 8. Security and operational requirements

- Use least-privilege GitHub App installation access scoped to this repository.
- Use short-lived installation tokens and narrowly scoped service identities; never place PATs or production secrets in agent prompts, logs or containers.
- Verify GitHub webhook HMAC signatures, reject stale timestamps where supported, and deduplicate delivery IDs.
- Pin GitHub Actions to reviewed full commit SHAs as a hardening task.
- Run agents in isolated, ephemeral containers with read-only mounts by default and explicit per-task write mounts.
- Never mount the Docker socket into untrusted agent containers.
- Do not provide PROD credentials to TheVoper, ArXtechT or Quester.
- Record actor, action, timestamp, correlation ID, source SHA, artifact digest, previous state, new state and reason for every state transition.
- Redact tokens, resume contents, personal data and secret values from logs and QA artifacts.
- Use expand/contract database migrations and test rollback before production rollout.
- Keep the current local Ollama default. No cloud LLM provider or paid API use without explicit owner authorization.

## 9. Non-goals for the first increment

- No autonomous merge.
- No agent-issued UAT, PreProd or PROD approval.
- No automatic production deployment.
- No second workflow engine.
- No second identity provider or database cluster.
- No broad write access to unrelated GitHub repositories.
- No claim that a green CI run proves live user acceptance.

## 10. Acceptance criteria

The release-control MVP is complete only when:
- a release can be created idempotently and survives service restart;
- illegal state transitions are rejected and audited;
- architecture and QA verdicts are bound to immutable SHAs;
- each environment promotion requires an authenticated human approval for the exact artifact digest;
- Temporal can resume a release after worker restart;
- duplicate webhooks and activity retries do not duplicate side effects;
- the same image digest is promoted through all environments;
- rollback restores the previously approved digest;
- automated tests cover authorization, concurrency, replay, rejection, timeout, cancellation and audit immutability.
