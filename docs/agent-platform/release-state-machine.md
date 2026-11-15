# Release Lifecycle and Approval Invariants

## State model

The release lifecycle is deliberately gated. A status is a server-derived projection of evidence and approvals, not a field an agent may set directly.

| State | Entry condition | Allowed next states |
|---|---|---|
| CREATED | Candidate created with repository, PR, source SHA and idempotency key | ARCHITECTURE_REVIEW, CANCELLED |
| ARCHITECTURE_REVIEW | Candidate SHA is fixed for review | QA_PENDING, BLOCKED, CANCELLED |
| QA_PENDING | ArXtechT has no blocking findings for the exact SHA | UAT_PENDING, BLOCKED, CANCELLED |
| UAT_PENDING | Quester reports required suites green for the same SHA; candidate artifact digest is recorded | UAT_APPROVED, REJECTED, CANCELLED |
| UAT_APPROVED | Human approves exact artifact digest for UAT | PREPROD_PENDING, BLOCKED |
| PREPROD_PENDING | UAT deployment and smoke checks succeed | PREPROD_APPROVED, REJECTED, CANCELLED |
| PREPROD_APPROVED | Human approves exact artifact digest for PreProd | PROD_PENDING, BLOCKED |
| PROD_PENDING | PreProd deployment and release checks succeed | PROD_APPROVED, REJECTED, CANCELLED |
| PROD_APPROVED | Human approves exact artifact digest for PROD and deployment succeeds | COMPLETED, ROLLBACK_PENDING |
| COMPLETED | Production health gate passes | ROLLBACK_PENDING |
| BLOCKED | A blocking review, QA failure or policy violation exists | ARCHITECTURE_REVIEW, QA_PENDING, CANCELLED |
| REJECTED | Human rejects a specific environment promotion | CREATED (new candidate only), CANCELLED |
| ROLLBACK_PENDING | Authorized rollback requested to a previously approved digest | ROLLED_BACK, BLOCKED |
| ROLLED_BACK | Previously approved digest restored and verified | terminal |
| CANCELLED | Authorized cancellation | terminal |

Implementation may split deployment-pending and approval-pending states further, but must preserve the invariants below.

## Required invariants

1. **Immutable candidate:** repository, PR number, source SHA and artifact digest are immutable once a release candidate enters environment promotion.
2. **Review binding:** architecture verdict must identify the exact full commit SHA. A changed PR head invalidates the verdict and requires a new review.
3. **QA binding:** QA verdict must identify the same source SHA that produced the release artifact. A later source commit requires a new QA run and artifact.
4. **Artifact identity:** one image digest is promoted through UAT, PreProd and PROD. Never rebuild between environments.
5. **Human approvals:** UAT, PreProd and PROD each require a separate human approval. An approval is bound to environment, release ID, artifact digest, approver identity and timestamp.
6. **No agent approvals:** agent service identities may submit evidence only. They cannot approve, reject, merge or promote.
7. **No implicit approval:** timeout, successful CI, workflow completion or a chat message from an agent is not human approval.
8. **Idempotency:** every create, callback, webhook and approval request has a stable idempotency key or event identifier.
9. **Concurrency:** state transitions use optimistic locking or compare-and-set. Two concurrent requests cannot both advance the same release.
10. **Append-only audit:** each accepted or rejected transition attempt is recorded with actor, reason, timestamp, correlation ID, SHA and digest.
11. **Fail closed:** missing evidence, stale evidence, unavailable identity provider or unknown state blocks promotion.
12. **Rollback:** rollback selects a previously approved immutable digest and records the incident/reason; it does not rewrite history.

## Human approval payload

An approval request must show:
- product and environment;
- release ID and PR link;
- source commit SHA;
- exact container image digest;
- architecture verdict and reviewer SHA;
- QA run URL and suite results;
- deployment diff / migration notes;
- known risks and rollback target.

The approval endpoint must derive the approver from the authenticated principal. Never accept an arbitrary `approvedBy` field from the request body.

## Service identities

Use separate identities for:
- TheVoper implementation writes (task branch only);
- ArXtechT architecture evidence (read-only GitHub access);
- Quester QA evidence (read-only product access plus narrowly scoped issue reporting);
- release controller / deployment broker.

Agent credentials must not be interchangeable with the human approval identity.
