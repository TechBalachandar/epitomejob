# QA Report — Mission Model: clock-first landing + home dashboard — 2026-11-18

**Tester:** Quester (chief QA) · **Branch:** `kotlin-migration` (head `8fa8287` + uncommitted work) ·
**Scope:** Mission Model only — public landing `/`, home dashboard `/home`, pure logic `lib/home-daily.ts`, routing in `app-shell.tsx` + login/register redirects. Web app only, uncommitted working tree. No product files modified, nothing committed, no GitHub issues filed (per task brief).

## Baseline (re-run, confirmed)

| Check | Result |
|---|---|
| Web Vitest suite | **26 files, 745 tests, all pass** (61.3 s) — incl. `home-daily.test.ts` (45) + `landing-home.test.tsx` (13) |
| `tsc --noEmit` | Clean, exit 0 |
| `next build` | Clean, exit 0 — `/` and `/home` both build as static routes |
| SSR (production server, this VM) | `GET /` → **200**, mission headline + "Example — not your clock" + pricing in the HTML, zero `password` occurrences; `GET /home` → **200** with the AppShell "Restoring your session…" loading state (unauthenticated first paint — the guard then redirects client-side, see below); `GET /login` → **200** |
| Adversarial sweep (temporary Vitest file, 32 tests) | **29 pass, 3 fail** — the 3 failures are the 2 bugs below (B1 × 2 probes, B2 × 1), each failing on the correct expectation. File deleted afterwards; no scratch left in the repo |

**Not run here (cannot run on this VM):** live app in a browser (login persistence across refresh, real feed data on `/home`, mobile widths), live mark-seen flow against a real backend feed. Where a verdict depends on one of those, it says so.

## Verdicts

| # | Flow | Verdict | Notes |
|---|---|---|---|
| 1 | Landing is truly public | **Pass** | `/` is in `PUBLIC_PATHS` (`app-shell.tsx`); rendered AppShell test proves unauthenticated `/` shows the landing shell with no redirect and no sidebar nav, and SSR serves the full page (200, no password field anywhere). All sections readable: mission, 4 steps, 9 features, daily-visit band, 3 prices. |
| 2 | Example countdown labelling | **Pass** | "Example — not your clock" label + `aria-label="Example 60-day countdown"` + body copy "A job seeker who started 23 days ago has 37 days left… Your clock starts when you set your date". 37 days left verified in SSR HTML and in the author suite. No personal-clock language anywhere on the page. |
| 3 | `/home` requires auth | **Pass** | AppShell guard rendered and proven: unauthenticated `/home` calls `router.replace("/login")` after `restoreSession()` resolves false; authenticated `/home` renders children and never redirects. SSR first paint is the "Restoring your session…" hold, not protected content. Observation O1 (no post-restore "redirecting…" state). |
| 4 | Weekly applications (Mon–Sun UTC) + funnel | **Pass with minor** | Week math re-probed beyond the author suite's Wednesday-only case: Sunday today → week Mon 11-16–Sun 11-22; Saturday same week; year boundary Fri 2027-01-01 → week Mon 2026-12-28–Sun 2027-01-03; next-Monday 00:00 excluded, Sunday 23:59 included. Funnel null denominators hold (empty → all null; only-SAVED → appliedRate 0, rest null). Minor M3 (all-REJECTED shows "Applied 0 … 100%"). |
| 5 | New sponsor jobs today + mark-seen | **Pass** | Stale-job exclusion re-probed: yesterday 23:59 excluded, today 00:00 included, date-only `postedAt` counts, invalid `postedAt` never counts, non-sponsor excluded, no-overlap excluded. Dedupe re-probed: same job as match + feed item appears once (via "match"); cap of 5 applies to matches too (8 matches → best 5 scores, 57→53); seen-set blocks both paths. Author component test proves "Mark these as seen" clears the list. |
| 6 | Question / tip of the day rotation + grounding | **Pass** | 2-question bank alternates daily over a full week; leap-year `dayOfYear` Dec 31 2028 = 365, Jan 1 2029 wraps to 0; tip rotation visits every failing check over 10 days and is always a failing check (30-day sweep); empty bank / all-passing resume → null, never a made-up question/tip. Both are drawn from real data (question bank for the interview job, real Analyzer failing checks). |
| 7 | Streak / daily check-in | **Pass with minors** | Consecutive days, broken streak, midnight rollover (23:59 check-in counts for that day), idempotent same-day check-in, corrupt/non-array/invalid-JSON storage all load as empty — all re-probed and holding. Minors M1 (B1: `lastCheckin` wrong on multi-day streaks) and M2 (B2: impossible dates accepted by the validator). |
| 8 | Login/register redirect + old `/` dashboard gone | **Pass** | Rendered submit tests prove login and register both `router.replace("/home")` on success (register needed `fullName` + `confirmPassword` filled — the probe initially failed on my own mock gap, not the product). Repo-wide grep: no `replace("/")` / `push("/")` remains outside the logo/landing links; nav "Home" → `/home`; no route still serves the dashboard at `/`. |
| 9 | Branding / invented numbers | **Pass** | No "Teal" / "Monster" text anywhere in the new files (grep); no testimonials, user counts, or star ratings on the landing (author test asserts the same); pricing is exactly $79–99 / $149 / $299–499, one-time, "No subscription, no monthly charge". |

## Minor bugs (backlog — no majors found)

### M1 — `computeStreak` reports `lastCheckin` as the streak START, not the most recent check-in
**File:** `web/src/lib/home-daily.ts:337` — `return { count, lastCheckin: isoForMs(cursor + DAY_MS), alive: true }`
**Repro (proven by adversarial probe, 2 failing tests):** `computeStreak(["2026-11-16","2026-11-17","2026-11-18"], new Date("2026-11-18T15:30:00Z"))` → `{count: 3, lastCheckin: "2026-11-16"}`; with a gap, `["2026-11-10","2026-11-11","2026-11-17","2026-11-18"]` → `{count: 2, lastCheckin: "2026-11-17"}`. The `Streak` interface promises "ISO date of the most recent check-in" (`home-daily.ts:308`).
**Expected:** `lastCheckin` is `"2026-11-18"` in both cases (today when checked in today, yesterday when only yesterday is checked in).
**Actual:** after the counting loop, `cursor` sits on the day *before* the run starts, so `cursor + DAY_MS` is the run's first day. A 1-day streak coincidentally reports correctly, which is why the author suite's count-only assertions never caught it. The dead-streak branch (`:330`, max sorted date) is correct.
**Severity:** Minor — latent only: nothing in the UI reads `lastCheckin` today (`home/page.tsx` uses only `streak.count` / `streak.alive`), so no user sees a wrong date. Any future "last checked in …" display would show the wrong day.
**Suggested fix:** capture the most recent date before the loop — e.g. after the today/yesterday adjustment, `const mostRecent = isoForMs(cursor);` and return that as `lastCheckin`.

### M2 — `isValidISODate` accepts impossible dates (`2026-02-30`) because `Date.parse` rolls them over
**File:** `web/src/lib/home-daily.ts` (`isValidISODate`, used by `loadCheckinDates`)
**Repro (proven by adversarial probe, 1 failing test):** `localStorage["epitomejob:daily-checkins"] = '["2026-02-30","2026-11-18"]'` → `loadCheckinDates()` returns `["2026-02-30","2026-11-18"]`. `Date.parse("2026-02-30T00:00:00Z")` normalises to Mar 2 instead of returning `NaN`.
**Expected:** only real calendar dates are kept; `"2026-02-30"` is dropped like `"garbage"` and `"2026-13-40"` already are.
**Actual:** the impossible date stays in the set. Harmless in practice — it never matches a real UTC day, so it cannot inflate a streak — but the validator's contract ("valid ISO date") is false for it.
**Severity:** Minor — reachable only via hand-edited localStorage; no user-visible effect.
**Suggested fix:** after parsing, round-trip the components: `const d = new Date(value+"T00:00:00Z"); return d.getUTCFullYear()===+y && d.getUTCMonth()+1===+m && d.getUTCDate()===+d2;` (or equivalent).

### M3 — All-REJECTED tracker shows "Applied 0 … 100%" in the funnel
**Files:** `web/src/lib/home-daily.ts: pipelineFunnel` (`reachedApplied` includes REJECTED) × `web/src/app/home/page.tsx` (funnel rows render `[label, count, rate]` per stage)
**Repro (proven by probe):** `pipelineFunnel([REJECTED])` → `{applied: 0, appliedRate: 1, interviewRate: 0, offerRate: null}`. The home page renders the "Applied" row as count `0` with rate `100%` beside it.
**Expected:** the rate next to a stage should describe that stage's count (or be suppressed when the stage count is 0).
**Actual:** REJECTED is counted in "reached APPLIED" for the rate but not in the "Applied" count, so the row contradicts itself. Defensible at the logic layer (a rejection implies an application happened), wrong at the display layer.
**Severity:** Minor — only visible to a user whose whole pipeline is rejections; the counts themselves are correct.
**Suggested fix:** either exclude REJECTED from `reachedApplied` (rate = reached-APPLIED / total becomes 0 for all-rejected) or render the rate only when its stage count is > 0.

## Minor observations (no action required unless the team wants it)

- **O1 — No "redirecting…" state after a failed session restore.** The AppShell holds a loading state *while* the restore check runs, but once it fails it fires `router.replace("/login")` while still rendering the protected children until navigation completes (`app-shell.tsx:137-140`). In a real browser the navigation lands immediately, so this is at most a one-frame flash; in the jsdom probe the mocked router never navigates, so children stay mounted (test artifact, not a product finding). A `redirecting` hold would close the window entirely.
- **O2 — Authenticated users visiting `/` still see "Sign in" / "Get started".** Proven by rendered probe: with `isAuthenticated: true` at pathname `/`, the public header shows both CTAs and no user email. The nav "Home" link is the escape hatch, but a logged-in user landing on `/` is offered a sign-in they do not need. Consider rendering "Go to Home" instead when authenticated.
- **O3 — "See sponsor jobs" goes to `/register`, not `/jobs`.** (`page.tsx`, hero secondary CTA.) Standard marketing behaviour, but the label promises jobs and the click asks for an account first. Consider "See sponsor jobs" → `/jobs` (which redirects to `/login` when anonymous) or relabelling the CTA.
- **O4 — `applicationsThisWeek` counts by `updatedAt`, not by when the application was sent.** An entry applied last week but moved stages this week counts this week; an entry applied this week but since moved back to SAVED does not. This matches the documented rule ("updatedAt falls in the current Mon–Sun UTC week"), so it is semantics to confirm, not a bug.
- **O5 — "Mark these as seen" shares the sponsor-alert seen-set.** `markJobsSeen` writes the same `epitomejob:seen-sponsor-jobs` key the `/clock` alerts read, so a job marked seen on `/home` also never alerts on `/clock`. Consistent with the stated intent ("keeps tomorrow's list genuinely new"); flagging only because the sharing is implicit.
- **O6 — Quota-full localStorage path could not be simulated here.** In this jsdom setup `vi.spyOn(window.localStorage, "setItem")` does not intercept (proven with a standalone probe — the spy never fires), so the "save fails, streak still works in-memory" path is code-reviewed only: `saveCheckinDates` is wrapped in try/catch and `recordCheckin` returns the in-memory dates. The in-memory/UI divergence on a failed save is the documented behaviour in the code comment, not a surprise.

## What was verified and holds up (no action)

- **F1:** landing public + SSR 200 with mission text, example label, real pricing, zero password fields; no login form.
- **F2:** example countdown is 37 days left (23 days used) with the label, aria-label, and body copy all saying "example / not your clock".
- **F3:** AppShell guard proven by render (not just read): unauthenticated `/home` → `/login`; authenticated `/home` renders; unauthenticated `/` never redirects.
- **F4:** week boundaries (Sun/Mon/Sat/year-boundary), funnel null denominators, new-jobs staleness/dedupe/cap/seen-set, question/tip rotation over 7–30 days, leap-year day math, streak consecutive/broken/rollover/idempotent/corrupt-storage — all re-probed with independent tests, 29/32 passing (the 3 failures are M1/M2).
- **F5:** login and register both land on `/home` on success (rendered submit tests); no route serves the old dashboard at `/`.
- **F6:** no Teal/Monster branding, no testimonials, no invented counts; pricing matches the product's real one-time prices with no subscription.

## Still untested (needs the Mac / live services)

1. Live browser behaviour of `/home` with a real backend feed (jobs posted today actually appearing, mark-seen persisting across reload, streak surviving a reload).
2. Login persistence across a full page refresh landing back on `/home` (the AppShell restore path with a real refresh cookie).
3. Mobile widths for the landing hero and the home dashboard grids.
4. Quota-full / private-mode localStorage behaviour of the check-in (O6) — needs a real browser.

## Shippable?

**Yes — with minors on the backlog.** No majors found. Every flow in scope passes its adversarial re-probe except M1–M3, all minor and none user-visible except M3's "Applied 0 … 100%" edge. Fix round should cover M1–M3 (M1 is a one-line fix in `computeStreak`); O1–O3 are optional polish.
