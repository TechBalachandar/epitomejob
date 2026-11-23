# Web E2E fix — Nov 22, 2026

Fixes the E2E failures in every run since the Mission Model landed (Nov 18):
CI "Web tests", Quester "Web E2E tests", and the nightly "Web tests + E2E" job.
Backend, agent-brain, evals, and web unit tests were already green — the failure
was E2E-only. No product code changed: only the E2E stubs/assertions and one
unit-test file. No backend contract change, no new dependencies, no fake data.

## Root cause (corrected finding)

The brief's prime suspect was only half right. The greeting `<h1>` on `/home`
renders unconditionally (it does not wait for any query), so the login step
itself was not timing out on a missing heading. The real failures, found by
comparing every endpoint each exercised page fetches against what the stubs
mock, plus every assertion against the current pages:

1. **Unmocked `/home` fetches (all six specs).** The Mission Model `/home`
   fetches `GET /api/jobs?sponsorsH1b=true&size=100`,
   `GET /api/resume-builder/documents`, and (when the plan has an interview
   job) `GET /api/interviews/questions?jobId=…`, on top of the old dashboard's
   profile / resumes / matches / tracker / approvals / sessions /
   unread-count. None of the login stubs mocked the new three. An unmocked
   request hits the real BFF proxy, whose `proxyJson` has no try/catch around
   `backendFetch` — with no backend in E2E it throws and the route 500s.
   The queries fail (retry 1, then error state), the page renders degraded,
   and — in the smoke test — the run also loses its clean "no unmocked
   backend calls" footing.
2. **Stale nav: "Dashboard" is gone (smoke.spec.ts).** The sidebar entry is
   "Home" (`/home`) now. The smoke `PAGES` loop's first entry clicked a link
   named "Dashboard" that does not exist, so the loop timed out.
3. **Stale clock-state text (dashboard.spec.ts).** `/home` renders no
   "Active" / "Urgent" label and no "almost up" copy — the clock state is
   the hero card's warning border (amber when urgent, rose when expired,
   plain when active). Tests 1 and 3 asserted the old labels.
4. **Stale builder entry flow (resume-builder.spec.ts).** Iteration 2
   (also Nov 18) made `/resume-builder` land on the home step (action cards
   + Recent Resumes) with a first-visit Quick Tour modal. Both builder tests
   asserted the old entry step immediately after nav ("Upload your resume to
   start from what you have", "Upload a resume" / "Build fresh" buttons,
   template names as headings) and never dismissed the tour modal, which
   covers the page. Template names are now `Select <name> template` buttons
   in the template-library modal, not headings.

## Stub gaps fixed, per spec

| Spec | Added stubs (real shapes, minimal payloads) |
|---|---|
| smoke.spec.ts | `GET /api/resume-builder/documents` → `[]` (jobs + questions were already stubbed) |
| dashboard.spec.ts | `GET /api/jobs**` → fixture jobs page; `GET /api/resume-builder/documents` → `[]`; `GET /api/interviews/questions**` → `[]` (empty question bank for the plan's interview job) |
| notifications.spec.ts | `GET /api/jobs**` → empty page; `GET /api/resume-builder/documents` → `[]` (no jobs/matches, so no questions fetch fires) |
| tracker-dnd.spec.ts | `GET /api/matches/me**` → `[]`; `GET /api/jobs**` → empty page; `GET /api/approvals/pending` → `[]`; `GET /api/interviews/sessions` → `[]`; `GET /api/resume-builder/documents` → `[]` |
| w6.spec.ts | `GET /api/resume-builder/documents` → `[]`; `GET /api/interviews/questions**` → `[]` (jobs stub already handled the feed query) |
| resume-builder.spec.ts | `GET /api/jobs**` → empty page; `GET /api/resume-builder/documents` → `[]` inside the builder handler (was 404) |

Empty `[]` / empty-page payloads are the real shapes for a user with no
documents / no jobs / no question bank — nothing invented. Fixture jobs in
dashboard.spec keep their Nov 10/11 `postedAt`, so none is "new today".

## Assertion / flow changes, per file

- `web/e2e/smoke.spec.ts` — `PAGES`: "Dashboard" → "Home"; added Clock
  ("Your 60 days"), Cover Letter ("Cover Letter"), Offers ("Compare offers").
  Resume Builder is deliberately NOT in the loop: its first-visit Quick Tour
  modal would intercept the generic loop's sidebar clicks; it is covered by
  its dedicated spec.
- `web/e2e/dashboard.spec.ts` — test 1: "Active" label replaced by asserting
  the clock card carries no `.border-amber-400` / `.border-rose-400` warning
  border. Test 3 ("urgent"): "Urgent" + "almost up" replaced by asserting
  exactly one `.border-amber-400` element (the urgent clock card).
- `web/e2e/resume-builder.spec.ts` — both tests now dismiss the Quick Tour
  ("Skip the Tour") first. Test 1: home step → "Have a resume already?
  Import it" → entry step (upload form already open, button reads
  "Hide upload") → upload → template-library modal → "Select Modern
  template" → "Use Modern" → editor; rest of the flow unchanged. Test 2:
  home step → "Start from template" card → modal → "Select Minimal
  template" → "Use Minimal" → editor; rest unchanged.
- `web/tests/landing-home.test.tsx` — 3 new unit tests pinning the product
  signal the E2E now asserts: active clock → no warning border; urgent
  clock (10 days left) → amber border; expired clock → rose border +
  "days past deadline". (Positive + negative + edge: active/urgent/expired.)

No product routing or behaviour was changed to satisfy a test.

## Verification on this VM

- Web unit suite (vitest): **26 files / 757 tests, all green** (was 754; +3 new).
- `pnpm exec tsc --noEmit`: clean (tsconfig includes the E2E specs).
- `pnpm build`: clean; `/` and `/home` both static.
- **Playwright E2E did NOT run on this VM**: Chromium cannot run here, so
  there is no local E2E run. The real proof is the next CI run on the PR
  (CI "Web tests" job runs `pnpm test:e2e` with Playwright Chromium).
- **Quester re-test: PENDING** — could not be run from this subagent (no
  subagent spawning at this depth). Parent to arrange: Quester reviews the
  changed E2E specs + targeted probes.
- **Architect (TheVoper) review: PENDING** — same reason. Parent to arrange.

## Minors backlog (not blocking)

- M-E2E-1: The smoke `PAGES` loop still does not cover Resume Builder (tour
  modal vs generic loop). Covered by resume-builder.spec.ts; a loop-safe
  tour dismissal could add it later.
- M-E2E-2: dashboard.spec's `stubDashboard` / `loginToDashboard` helper
  names still say "dashboard" though the page is "Home". Cosmetic rename.
- M-E2E-3: The six specs each carry their own copy of the login + `/home`
  stubs. A shared `e2e/home-stubs.ts` helper would stop the next routing
  change from breaking all six again. Refactor, not a fix.
- M-E2E-4: `proxyJson` (web `src/lib/server-api.ts`) has no try/catch around
  `backendFetch`, so an unmocked E2E request with the backend down throws
  and the BFF route 500s instead of returning a JSON error. Product-side
  hardening, out of scope for this test-only fix.
- M-E2E-5: Mobile-390 overflow on `/home` was not re-verified here (needs a
  real browser); the smoke + dashboard 390px tests cover it in CI.
