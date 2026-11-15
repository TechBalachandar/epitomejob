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
- [x] Add initial release-candidate and append-only audit schema using Flyway (V10).
- [x] Add authenticated candidate-create/read/timeline API in the existing backend.
- [x] Add request idempotency, creator ownership checks, optimistic-lock field, and append-only audit trigger.
- [x] Add unit tests for candidate creation, replay, idempotency-key conflict and ownership.
- [ ] Add full policy-enforced lifecycle transitions, architecture/QA evidence, approvals, outbox, repository/integration/concurrency tests and API documentation.

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

Phase 0 governance documentation/workflow changes and the first Phase 1 candidate API slice are in PR #17. The release controller is not production-ready: there are no lifecycle-transition, agent-evidence, human environment-approval, transactional-outbox or Temporal release-workflow implementations yet. The Maven test suite has not been run in this execution environment and the latest commit has no reported status checks. Do not mark the phase complete until CI and the remaining acceptance criteria pass.
