# QA Report — Resume Builder E2E fix (2026-11-22)

## Summary

At head `1cef19d` the Playwright suite was 70 tests: 65 passed, 1 skipped,
4 failed — all 4 in `web/e2e/resume-builder.spec.ts`, the same two tests on
both projects (chromium and mobile-390). This fix addresses both failures.
Test-side only: no product code, routing, behaviour, or backend contract
changed.

## Failure 1 — Test 1: Summary textbox timeout (spec line 259)

**Test:** "entry → upload → template pick → edit → AI options → download"
(`resume-builder.spec.ts:211`).
**Symptom:** `getByRole('textbox', { name: 'Summary' }).fill(...)` timed out
after 30s, on the original run and on retry. The "Full name" assertions
just before it passed.

**Root cause:** Content sections are accordions. `SectionCard` renders its
form only when expanded (`web/src/app/resume-builder/page.tsx:183`,
`{expanded && (<CardContent>…)}`), and `expandedSection` defaults to
`"contact"` (page.tsx:673), one section at a time. After reaching the
editor only Contact is open — which is why "Full name" rendered — while
the Summary textarea is not in the DOM at all until its header is expanded.
The test never expanded it.

**Fix (spec only):** click the Summary header toggle before filling:
`page.getByRole("button", { name: /^Summary/ })`. The header's accessible
name is "Summary <n> chars" (title + count span, e.g. "Summary 41 chars"
for the parsed fixture). The anchored regex matches exactly that button:
"Improve summary with AI" starts with "Improve", the include control is a
checkbox, and the Designer panel (which has its own "Summary" label) only
mounts in Designer mode, which these tests never enter. Both reviewers
confirmed no strict-mode risk in Content mode.

**No unmocked fetch involved:** the editor renders from local parsed state;
every fetch after template pick (PUT/GET `/documents/doc-1`, POST
`/documents/doc-1/copilot`, GET `/documents/doc-1/export`,
POST `/documents/doc-1/save-to-profile`) is stubbed. The `?documentId=`
load effect fires GET `/documents/doc-1` — stubbed. Proven by "Full name"
rendering in the failing run.

## Failure 2 — Test 2: "Download PDF" button never visible (spec line 317)

**Test:** "build fresh → templates → editor, 390px without horizontal
overflow" (`resume-builder.spec.ts:297`).
**Symptom:** the preview contained "Alex Morgan" (line 316 passed) but
`getByRole("button", { name: "Download PDF" })` never became visible.

**Root cause:** there is no "Download PDF" button in the product. The PDF
export button is labelled **"Export PDF"** (page.tsx:1273,
`exporting === "pdf" ? "Exporting…" : "Export PDF"`); only DOCX says
"Download DOCX" (page.tsx:1265). `grep -rn "Download PDF" web/src/`
returns nothing. The button is in the editor top bar, rendered
unconditionally in the editor step (`step === "editor"`, page.tsx:1218),
disabled only while `exporting !== null`, and its container is
`flex flex-wrap` — at 390px it wraps to a second row, it does not
disappear. This was a wrong label in the test, not a product bug.

**Fix (spec only):** renamed "Download PDF" → "Export PDF" in both tests
(3 occurrences: lines 280, 283, 317). "Download DOCX" kept as-is — it is
the real label. The same rename in test 1 covers its download assertions,
which it had never reached.

## Changes per file

- `web/e2e/resume-builder.spec.ts` — 13 insertions, 3 deletions:
  - Test 1: expand the Summary section before filling (click + comment).
  - Test 1 + Test 2: "Download PDF" → "Export PDF" (3 occurrences).
- `qa-reports/2026-11-22-resume-builder-e2e-fix-qa.md` — this report.
- No `web/src` changes (verified: `git diff -- web/src` is empty).

## Verification (local, this VM)

- Web unit suite (vitest): **26 files / 757 tests, all green.**
- `pnpm exec tsc --noEmit`: clean.
- `pnpm build`: clean (`/resume-builder` static).
- `playwright test --list`: **70 tests in 6 files**, spec parses.

## Reviewers

- **Quester QA: SHIPPABLE, no majors.** Verified every claim against
  product code (accordion gating, selector uniqueness, label grep,
  copilot apply value, export/save stubs, route-handler audit). Caveat:
  Playwright/Chromium cannot run on this VM — code review + targeted
  probes only.
- **Architect (TheVoper): SHIP, no required fixes.** Verified the diff is
  spec-only and minimal, both root causes, the `/^Summary/` selector
  (no strict-mode risk), test 2 at 390px, and no new flakiness.

## What is still unproven

Playwright/Chromium cannot run on this VM, so there was **no local E2E
run**. The real proof is the next CI run on the PR (CI "Web tests" job
runs `pnpm test:e2e` with Chromium). Both tests previously died before
reaching their later assertions, so the copilot-apply, placeholder-note,
export, save-to-profile, and overflow assertions have never executed —
review found no static red flag in any of them.

## Minors backlog

**Quester M-1–M-4:**
- M-1 — Toggle fragility (low): the Summary-header click toggles; it only
  expands because the default is `"contact"`. If a future change made
  Summary default-expanded, the click would collapse it and `fill()` would
  time out again. Harden with a guard (click only if
  `aria-expanded="false"`); not required now.
- M-2 — Spec comments quote the old label (cosmetic): lines 287 and 326
  still contain the string "Download PDF" inside comments explaining the
  rename. Harmless, but a repo-wide grep for the old label keeps hitting
  the spec.
- M-3 — 390px Export PDF assertion is presence-only (low): `toBeVisible()`
  passes even if the button wraps to a second row. The `overflow === 0`
  check covers layout separately; acceptable as written.
- M-4 — Live-run gap (process): the 4 failures have not been re-run
  anywhere yet — the first Chromium run (CI) is still the real proof.

**Architect N1–N3:**
- N1 — Harden the regex (optional): `/^Summary/` is unanchored at the
  end, so it would also match a future button named e.g. "Summary
  settings". Today there is exactly one match; if touched later,
  `/^Summary( \d+ chars)?$/` pins the two real forms.
- N2 — Product label inconsistency (genuine product nit, not a test
  issue): "Download DOCX" vs "Export PDF" is inconsistent naming for the
  same action in the same top bar. Worth a product-backlog note; the test
  correctly follows the product as built.
- N3 — First green run is still the proof (same as M-4).

**Carried forward (from the 1cef19d round):** Quester M-1–M-7 and
architect N1–N7 from `qa-reports/2026-11-22-web-e2e-fix-qa.md` still
stand (shared `e2e/home-stubs.ts`, `proxyJson` try/catch, expired-clock
E2E coverage, urgent-threshold boundaries).
