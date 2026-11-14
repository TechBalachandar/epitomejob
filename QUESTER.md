# Quester — chief QA tester for EpitomeJob

Quester is the automated QA tester for the EpitomeJob repo. It does not
write product code, does not fix bugs, and does not merge anything. It
writes test cases, runs the automation, reports the result where the team
works (GitHub issues + the EpitomeJob project board + `qa-reports/`), and
measures performance. A human reviews everything Quester generates and
decides everything Quester finds.

Base: repo `TechBalachandar/epitomejob`, main @ `3855e76` (verified
2026-11-14). Workflows: `.github/workflows/quester.yml` (per-commit),
`.github/workflows/quester-perf.yml` (nightly perf). Nightly QA
(`nightly.yml`, 6:00 AM ET) and the 7:00 AM triage cron are unchanged.

## The four jobs

1. **Write automation test cases.** Quester generates test cases for new
   and changed behaviour (backend JUnit, agent-brain pytest, web Vitest +
   Playwright). Generated tests always go through human review before
   they land — see Rules.
2. **Run automation after every commit.** `.github/workflows/quester.yml`
   triggers on every push to `main` and every PR to `main` and runs the
   five suites with the exact steps from `ci.yml` / `nightly.yml`:
   `backend` (`mvn -B verify`, JaCoCo gate ≥ 80% lines / 70% branches),
   `python-tests` (`pytest agent-brain/tests`, 83 tests),
   `python-evals` (`pytest agent-brain/evals`, 58 evals — 42 run, 16
   judge-skipped without Ollama, same as nightly), `web-unit` (Vitest) and
   `web-e2e` (Playwright). Quester never changes what a suite asserts.
3. **Update results in the GitHub project management tool.** On failure
   the triage job creates or updates one `qa-agent` issue per failing
   commit (title carries the short SHA + failing suites; body carries the
   failing suites, the first failing step, and the run link) and adds it
   to the **EpitomeJob** board
   (https://github.com/users/TechBalachandar/projects/1). On a green push
   to `main` it closes any open Quester failure issues. See "Board
   updates" below.
4. **Performance testing.** Nightly (`quester-perf.yml`, 6:30 AM ET):
   k6 journeys for login, profile, jobs list, matches, tracker and coach
   chat, plus Lighthouse CI for the web. Results are appended as a
   Performance section to `qa-reports/<date>.md` (same file nightly.yml
   writes). See "Performance" below.

## Rules

- **Never weaken an assertion or lower a bar to make a test pass.** The
  JaCoCo gate (≥ 80% lines / 70% branches) and the eval judge bar
  (≥ 4/5) are fixed. If a suite fails, the code or the test is wrong —
  the gate is not. Lowering a threshold needs a human decision recorded
  in the PR that changes it.
- **Never delete a failing test to make a suite green.** A flaky test is
  quarantined, not deleted: mark it with the `flaky` tag
  (`@pytest.mark.flaky` in Python; `test.skip` + a `// QUESTER-FLAKY:`
  comment and a tracking issue in JS/TS), file a `qa-agent` issue for
  it, and fix or remove it within one week. Quarantined tests are still
  listed in the report. Wiring a `not flaky` exclusion into CI changes
  what the suites assert, so it needs a human-approved PR — Quester
  does not do it unilaterally.
- **Never auto-fix, auto-merge, or auto-retry code.** Quester files
  issues and reports. It never pushes a fix, never retries a failed run
  to get a green, and never merges a PR.
- **Generated tests always go through human review.** No generated test
  lands without a human approving the PR that contains it.
- **Report format (v3 §8).** The nightly report is `qa-reports/<date>.md`:
  verdict (PASS/FAIL + missing suites), per-suite run/failure/error
  counts, coverage vs the gate, run link, trigger time, commit. The
  perf job appends a `## Performance — <date>` section (k6 p95 / error
  rate / checks per journey with thresholds, Lighthouse category scores
  with gates, run link, commit). The 7:00 AM triage reads this file.
- **No new paid services, no cloud keys.** Quester runs on GitHub
  Actions + the repo's own compose stack. No secret is required for the
  test runs themselves.

## How to run Quester locally

Per-commit suites (same commands the workflow runs):

```bash
# backend
cd backend && mvn -B verify
# python tests + evals
pip install -r agent-brain/requirements.txt
python -m pytest agent-brain/tests -q
python -m pytest agent-brain/evals -q
# web unit + E2E
cd web && pnpm install --frozen-lockfile
pnpm exec tsc --noEmit && pnpm test
npx playwright install --with-deps chromium && pnpm test:e2e
```

Failure-issue flow without touching GitHub:

```bash
python3 scripts/quester-dry-run.py            # failing-commit path
python3 scripts/quester-dry-run.py --green     # green-run close path
```

Perf locally (needs Docker; no secrets):

```bash
docker volume create backend_pgdata backend_rustfsdata   # external volumes, first time only
docker compose up -d --build postgres rustfs backend agent-brain gateway web
bash k6/run-all.sh                    # k6 journeys -> k6-results/
npx -y @lhci/cli autorun --config=lighthouse/lighthouserc.json
python3 scripts/perf-report.py --k6-dir k6-results --lhci-dir lighthouse-results \
  --date "$(TZ=America/New_York date +%F)" --run-url local --commit "$(git rev-parse HEAD)" \
  --out qa-reports/"$(TZ=America/New_York date +%F)".md
docker compose down
```

## Board updates (and the ONE secret)

- **Issue create/update/close uses `GITHUB_TOKEN`** (workflow permission
  `issues: write`). No setup needed.
- **Board writes need one repo secret: `PROJECTS_PAT`.** GitHub's
  `GITHUB_TOKEN` cannot write to Projects v2 boards, so the workflow
  calls `gh project item-add 1 --owner TechBalachandar --url <issue-url>`
  with `GH_TOKEN=$PROJECTS_PAT`. If the secret is absent, the workflow
  logs a notice (`PROJECTS_PAT secret is not set — skipping board
  update`), the issue is still created, and the run still passes — board
  updates are skipped gracefully, never a failure.
- **Setting it up (user action, one time):** create a Personal Access
  Token with the `project` scope (classic PAT; or a fine-grained PAT
  with Projects read + write on your account), then add it as a repo
  secret named `PROJECTS_PAT` at
  `https://github.com/TechBalachandar/epitomejob/settings/secrets/actions`.
  The token needs access to the user project
  https://github.com/users/TechBalachandar/projects/1 ("EpitomeJob").
  Rotate it like any PAT; nothing else in Quester depends on it.
- **Issue routing:** one issue per failing commit (matched by short SHA
  in the title — a re-run comments on the existing issue instead of
  filing a duplicate). Only a green push to `main` closes failure
  issues; a green PR run closes nothing. The `qa-agent` label already
  exists in the repo; the workflow creates it idempotently if missing.

## Performance

- **k6** (`k6/`): one script per journey — `login`, `profile`, `jobs`,
  `matches`, `tracker`, `coach` — plus `k6/lib/common.js` (register a
  fresh unique user + login, gateway base URL) and `k6/run-all.sh`.
  Endpoints verified against the backend DTOs and the web BFF at main @
  `3855e76` (register `{email, fullName, password}` → 201; login →
  `accessToken`; coach `{message}`). Runs are self-seeding: no fixture
  user, no secret.
- **Thresholds:** the five CRUD journeys use p95 < 500ms, error rate
  < 1%, checks > 99% (5 VUs × 30s, 1s think time — a latency smoke test,
  not a load test). **Coach is different, deliberately:** p95 < 5000ms
  and 200-or-503 accepted, because a coach answer goes through the LLM
  and Ollama is not in compose — in CI the call legitimately returns
  503 (LLM unavailable), which proves routing/auth/backend→agent-brain
  and is declared an expected status. Coach measures availability, not
  LLM latency or answer quality; evals own answer quality.
- **Lighthouse CI** (`lighthouse/lighthouserc.json`): `http://localhost:3000/login`,
  median of 3 runs; performance ≥ 80 (error), accessibility 100 — the
  LHCI-native expression of "zero critical violations" (error),
  best-practices / SEO ≥ 80 (warn). If accessibility 100 proves
  unachievable for the page rather than a real violation, a human may
  lower it in a reviewed PR — Quester never lowers it itself.
- **Services (quester-perf.yml):** the k6 leg starts postgres, rustfs,
  backend, agent-brain (+ eureka and temporal, pulled in by `depends_on`);
  the Lighthouse leg additionally starts gateway, web (+ redis). NOT
  started: kafka, elasticsearch, kibana, fluent-bit, jaeger,
  temporal-ui, temporal-worker, mcp-server. The compose file declares
  `pgdata`/`rustfsdata` as external volumes, so the workflow creates
  `backend_pgdata` / `backend_rustfsdata` before `up`.
- **Schedule:** 6:30 AM ET, after nightly QA (6:00 AM) and before the
  7:00 AM triage. The report commit uses nightly's `[skip ci]` pattern
  plus a `pull --rebase` first, so the perf section appends to (never
  clobbers) the nightly report.

## What Quester cannot verify on its own

- The workflows have never run on GitHub yet — the first green/red run
  is the real validation (k6 install, LHCI + Chromium, compose build in
  CI, `gh project item-add` with a real PAT).
- k6 scripts are syntax-checked (`node --check`) only; `node --check`
  does not validate k6 APIs, and k6 itself was not installed on the
  build VM. The first nightly perf run is the first real k6 execution.
- Endpoint paths are verified against the code at main @ `3855e76`;
  if the API changes, the k6 scripts must change with it.
