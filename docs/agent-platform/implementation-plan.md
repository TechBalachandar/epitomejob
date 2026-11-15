# Agent Platform Implementation Plan

This plan is incremental. Each phase must have a passing automated gate before the next phase begins.

## Phase 0 — Governance baseline
- [x] Separate TheVoper, ArXtechT and Quester responsibilities.
- [x] Require task-specific branches and PRs to `qa`.
- [x] Bind architecture reviews to exact commit SHA.
- [x] Add MCP service test job to Quester workflow.
- [x] Remove duplicate agent-side workflow source.
- [ ] Add required branch rulesets and required checks (repository-owner action; inspect current rules before changing).
- [ ] Pin third-party GitHub Actions to full reviewed commit SHAs.

## Phase 1 — Release-control persistence and API
- [ ] Add release candidate, approval, audit-event, idempotency and outbox schema using Flyway.
- [ ] Add authenticated API and policy-enforced state machine to the existing backend.
- [ ] Add append-only audit and optimistic locking.
- [ ] Add unit, repository and integration tests for legal/illegal transitions and concurrent approvals.
- [ ] Add REST API documentation and a read-only release timeline endpoint.

## Phase 2 — Temporal orchestration
- [ ] Add a distinct `ReleaseWorkflow` and `release-queue` to the existing Temporal Python worker.
- [ ] Add idempotent activities for architecture review, QA, deployment and rollback requests.
- [ ] Add human-approval signals for UAT, PreProd and PROD.
- [ ] Add backend-to-Temporal adapter with transactional outbox, idempotency and service authentication.
- [ ] Test worker restart, activity retry, duplicate signal, cancellation and timeout.

## Phase 3 — GitHub integration
- [ ] Verify GitHub webhook signatures and deduplicate delivery IDs.
- [ ] Ingest PR, push, review and workflow-run events.
- [ ] Bind every event and review verdict to repository + PR + immutable SHA.
- [ ] Use short-lived GitHub App installation tokens with least privilege.
- [ ] Add status checks for architecture review and Quester QA.

## Phase 4 — Isolated agent execution
- [ ] Build ephemeral, task-scoped execution containers.
- [ ] TheVoper receives write access only to its task branch.
- [ ] ArXtechT receives read-only diff/source access.
- [ ] Quester receives test execution and narrowly scoped issue reporting access.
- [ ] Enforce resource limits, network policy, secret redaction and artifact retention.

## Phase 5 — Artifact promotion and environment gates
- [ ] Build and sign one immutable image digest.
- [ ] Generate provenance/SBOM and vulnerability scan results.
- [ ] Promote the exact digest to UAT, PreProd and PROD.
- [ ] Require a separate human approval for each environment.
- [ ] Implement deployment health checks and tested rollback to previous digest.

## Phase 6 — Dashboard and operations
- [ ] Add release timeline, current gate, evidence, pending human approval and audit views.
- [ ] Add SSE only if polling is insufficient for the dashboard.
- [ ] Add metrics, tracing, alerting, retention and operational runbooks.
- [ ] Conduct threat model and recovery exercise before enabling production automation.

## Current delivery status

Phase 0 documentation and workflow changes are in PR #17. This is not a deployed release controller. Phases 1–6 are not implemented yet. Do not mark them complete until code, tests and CI evidence exist.
