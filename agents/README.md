# Agents — Quester and TheVoper

Two agents run EpitomeJob quality in a loop. Neither approves the other's work. The user (Balachandar) approves.

- **Quester** — `agents/quester.md` — Chief QA tester. Tests everything, files one GitHub issue per bug (label `qa-agent`), keeps `qa-reports/<date>.md` current. Never writes product code.
- **TheVoper** — `agents/thevoper.md` — Senior developer + solution architect + SME. Fixes what Quester files, verifies before committing, pushes to the `qa` branch.

## The QA branch loop

```
TheVoper fixes issues #8-#13 in branch `qa`
  -> commit (one-line message) -> push to `qa`
  -> Quester QA workflow runs on the `qa` branch
       - regression: the five suites (backend, agent-brain tests,
         agent-brain evals, web unit, web E2E)
       - smoke: compose up + gateway health + web homepage + jobs endpoint
  -> triage writes the QA report (run summary + issue on failure)
  -> Quester reports the result to the user
  -> if green, the fix is ready to promote to `main` (PR, user approves)
  -> if red, TheVoper fixes again; Quester re-tests
```

## Regression + smoke after every fix

After **every** TheVoper commit to `qa`, Quester runs:

**Regression** (must be green):
- Backend `mvn verify` — gate lines ≥ 80%, branches ≥ 70%
- Agent-brain `pytest agent-brain/tests`
- Agent-brain evals `pytest agent-brain/evals` — judge pass ≥ 4/5
- Web unit `pnpm test` (+ `tsc --noEmit`)
- Web E2E `pnpm test:e2e`

**Smoke** (must be green):
- `docker compose up -d --build` from the repo root
- Gateway healthy: `GET http://localhost:8080/actuator/health` returns 200
- Web serves: `GET http://localhost:3000` returns 200
- Jobs endpoint returns real listings through the gateway
- The specific fixed flow is exercised in the live app (e.g. refresh keeps you logged in for #8, Builder preview shows Experience for #10, Coach answers for #11)
- `docker compose down` afterwards (never `down -v` on the root stack)

**Report:** Quester posts the result (per-suite pass/fail, smoke result, run link, and the issue numbers verified) to the user. A fix is not "done" until regression + smoke are green on the `qa` branch.

## Branch rules

- `qa` is the fix branch. TheVoper commits and pushes here, never directly to `main`.
- `main` only changes by PR from `qa`, approved by the user, after Quester reports green.
- The Quester QA workflow lives at `agents/quester-qa.yml` in this branch. It cannot be pushed to `.github/workflows/` by the automation (GitHub blocks workflow writes from this path), so the user copies it there once from his Mac:
  `cp agents/quester-qa.yml .github/workflows/quester-qa.yml` then commit + push.
- Commit messages: one simple line.
- Verify before committing. Never weaken an assertion, a test, or a coverage gate to make it pass.
