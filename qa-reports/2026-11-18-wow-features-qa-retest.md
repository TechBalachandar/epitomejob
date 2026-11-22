# QA Report — 7 Wow-Factor Features, PHASE 2 RE-TEST — 2026-11-18

**Tester:** Quester (chief QA) · **Branch:** `kotlin-migration` (head `a7822a2` + uncommitted work) ·
**Scope:** Re-test of B1–B5 + ATS-1/ATS-2 after the fix round, web app only, uncommitted working tree.
No product files modified, nothing committed, no GitHub issues filed (per task brief).

## Baseline (re-run, confirmed)

| Check | Result |
|---|---|
| Web Vitest suite | **24 files, 686 tests, all pass** (42.7 s) |
| `tsc --noEmit` | Clean, exit 0 |
| `next build` | Clean, exit 0 — `/clock`, `/cover-letter`, `/offers` all build as static routes |
| Adversarial sweep (temporary Vitest files, 33 tests) | **33/33 pass** — every verdict below was reproduced by an independent probe, then the files were deleted |

**Not run here (cannot run on this VM):** live app in a browser, live Coach generation (needs Ollama on the Mac), live export byte-check (needs the resume-builder service), backend `mvn verify` (no Java/Maven on this VM). Where a verdict depends on one of those, it says so.

## Verdicts

| Bug | Verdict | Evidence |
|---|---|---|
| B1 — cover-letter export paragraphs | **Fixed in code; live bytes still unproven** | Code trace + new-test assertions (see below). Live DOCX/PDF bytes still need the Mac. |
| B2 — alerts never appear on page load | **Fixed** | Own probes: alerts surface when profile and feed arrive in two steps, and when a feed refresh adds a second job — no click. Negative controls pass (non-sponsor / no-overlap jobs do not alert). |
| B3 — dismissed alerts resurface | **Fixed** | Own probes: dismiss survives a feed refresh (fresh array instances, same ids) that also adds a brand-new sponsor job, and survives a manual Check; dismissing both then checking leaves the list empty. |
| B4 — inline editor `$NaN` | **Fixed** (with one new minor, N1) | Own probes: `abc` / empty / `-5` / `$$$,,,` / `12.5x` in all three inline money fields — no `NaN` anywhere in the document, total cash falls back to `—` or the valid figure. Formatted `1,000,000` + `25,000` stores and totals `$1,025,000`. |
| B5 — fake `$0k – $0k` range | **Fixed** | Own probe: max-only posting renders `Listed range: $175k USD`, salary-less posting renders `Listed range: Not listed`, no `$0k` anywhere in the document. |
| ATS-1 — phone regex | **Fixed** (with one new minor, N2) | Own probes: 7-digit numbers, date ranges (`Acme 2020-2024`, `2024-01-15`), `1-555-0142` and `555 0142` all rejected; 7 separated 10-digit formats accepted. |
| ATS-2 — null parsed text | **Fixed** | Own probes: component with `text: null` renders the no-text message + single no-text flag; `null` and `""` render identically (flags block and counts compared). |

## Per-bug detail

### B1 — Cover-letter export paragraphs: fixed in code, live bytes still unproven
**Files:** `resume-builder-service/src/main/kotlin/com/epitomejob/resumebuilder/service/ExportService.kt`
- DOCX: the `summary` slot now goes through `addParagraphs`, which splits on blank lines (`\n\s*\n`) into separate paragraphs and renders single newlines inside a block as `<w:br/>` breaks via `addLine` (one run per line, `setText` called once per run). A literal `\n` can no longer sit inside a run.
- PDF: `wrap()` now splits on newlines first, so a `\n` never reaches `showText` as part of a word token; blank segments become blank lines (the `line()` loop advances `y` and draws nothing), and other C0 control characters are stripped.
- New tests (`ExportServiceTest.kt`) assert the right thing: the DOCX test checks the greeting/body/closing land in separate paragraphs, in order, with no paragraph containing `\n`; the PDF test checks the `%PDF-` header and that all three paragraphs survive text extraction.
- **Not proven here:** no Java/Maven on this VM, so the backend suite could not execute; no resume-builder service is running, so no real export bytes were produced. One real DOCX + one real PDF export on the Mac is still the final proof.
- Residual note (not a bug): a tab character (`\u0009`) is not stripped by the PDF control-character filter (the regex excludes `\u0009`); a tab in a summary would still reach WinAnsi Helvetica. Co-Pilot prose essentially never contains a tab, so this is a theoretical edge only.

### B2 — Sponsor alerts on page load: fixed
**File:** `web/src/components/sponsor-alerts.tsx:53-66` — a `useEffect` re-derives alerts from the `scanned` memo whenever the feed/profile arrive, merging only newly surfaced alerts and marking them seen (dismissed jobs are in the seen-set, so the scan never returns them). My probes used a different arrival pattern than the regression test (profile-null → profile → feed, two steps) plus a feed-refresh-adds-a-job case; both surfaced alerts with no click. Negative controls (non-sponsor job; sponsor job with no profile overlap) correctly produce no alert and no badge.

### B3 — Dismissed alerts stay dismissed: fixed
**File:** `web/src/components/sponsor-alerts.tsx:78-83` (`dismiss` now calls `markJobsSeen([id])`) and `:68-77` (`checkForNew` re-scans fresh from the seen-set instead of reusing the stale memo). My probes dismissed one of two alerts, then rerendered with a refreshed feed (fresh array instances, same ids) plus a brand-new sponsor job: the dismissed alert stayed gone, the other stayed, the new one appeared; a subsequent manual Check did not resurrect it. Dismissing both then checking leaves the list empty.

### B4 — Inline offer editor: fixed, one new minor (N1)
**Files:** `web/src/app/offers/page.tsx:256,270,284` — all three inline handlers now use the validating `parseMoney` (shared with the manual form). `parseMoney` returns `null` for blank / non-numeric / negative input, and `totalCash`/`formatMoney` render `null`/non-finite as `—`. My probes typed `abc`, `$`, `-5`, `$$$,,,`, `12.5x` and empty into base/bonus/signing-bonus: the document never contains `NaN`, total cash is `—` or a valid figure, and formatted input (`1,000,000` + `25,000`) stores and totals `$1,025,000`.
- **N1 (new minor):** `parseMoney` treats symbols-only input as `0`, not empty — `"$".replace(/[$,]/g, "")` is `""` and `Number("")` is `0`, which passes the finite-and-≥0 check. Typing `$` into a bonus field stores `0`, displays `0`, and the table shows `$0` for a bonus the user never entered a figure for. Same hole in the manual form: a base salary of `$` passes validation and creates a `$0` offer. Pure-function probe: `parseMoney("$")`, `parseMoney(",")`, `parseMoney("$$$,,,")`, `parseMoney("   $   ")` all return `0`. No corruption and no NaN — a fabricated `$0` figure in miniature, same spirit as B5. Fix: reject when the stripped string is empty.

### B5 — Salary-less tracker job: fixed
**Files:** `web/src/lib/jobs.ts:7-16` — `formatSalary` now returns `null` when both ends are null (and one-sided ranges otherwise); `web/src/lib/types.ts:104-107` — `Job.salaryMin/Max` are nullable, matching the backend `JobDto Int?`; `web/src/components/offers/offer-comparison.tsx:23-29` — `ListedRange` renders `Listed range: Not listed` for a null range. My probe (different from the regression test: max-only + salary-less side by side) renders `Listed range: $175k USD` and `Listed range: Not listed`, with no `$0k` anywhere. The jobs detail pane already handles null (`Salary not listed`); the jobs list renders a null range as an empty span, which is fine.

### ATS-1 — Phone regex: fixed, one new minor (N2)
**File:** `web/src/lib/ats-preview.ts:PHONE_RE` — `/(\+?1[\s.-]?)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]\d{4}/`. The area code is now required and the separator before the final 4 digits is mandatory, so a 7-digit number can no longer match. My probes: `555-0142`, `555 0142`, `1-555-0142`, `Acme 2020-2024`, `2024-01-15` all correctly report no phone; `(571) 555-0142`, `571.555.0142`, `+1 571-555-0142`, `571 555 0142`, `571-555-0142`, `1-800-555-0142`, `+1 (571) 555-0142` all correctly report a phone. M6 (date-range false positive) is closed by this fix.
- **N2 (new minor):** a bare 10-digit run with no separators (`5715550142`) still does not match — the separator before the final 4 digits is mandatory. Plausible in PDF-extracted text; the resume would show "Phone not found" even though a 10-digit number is present. Note this is pre-existing behaviour (the old regex also required a separator), not a regression from the fix. Fix: make the final separator optional (`[\s.-]?`) while keeping the area code required.

### ATS-2 — Null parsed text: fixed
**File:** `web/src/components/resumes/ats-preview.tsx:81-83` — `null` text is analysed via `analyzeParsedText(null)` (which coerces to `""` and returns the single `no-text` flag) instead of rendering an empty card. My probes: `text: null` renders the "No parsed text" message, the `no-text` flag text, `0 characters`, and `Phone not found`; the flags block and counts for `null` and `""` are byte-identical.

## Phase 1 360° spot-checks (§8) — 2+ per feature, own probes

All 18 spot-check probes passed. Each used different inputs from the author-written tests.

| Feature | Spot-checks (own probes) | Result |
|---|---|---|
| Daily plan | Score-0 sponsor match still ranks as a match (via `match`, score 0 kept); 7 sponsor matches cap at 5 | Pass |
| Clock view | Start 59 days ago renders singular "1 day left" (number + label are separate nodes); empty-date save shows the error and does not call `onSaveDate` | Pass |
| Tailor + apply | `diffLines` does not collapse duplicate lines (one `Alpha` removed, two `same`); `buildTailoredState` ignores an experience pick with index 99 (state unchanged) | Pass |
| Sponsor alerts | Whole-token: `Java` skill does not overlap a JavaScript-only job; `Remote (US)` — see M11 below | Pass / M11 open |
| Offer comparison | Zero base + $5,000 bonus totals to $5,000 (not null); total cash crowns a different winner than base salary (A $150k+0 vs B $140k+$30k) | Pass |
| ATS preview | Heading-like word inside a sentence ("My Experience spans ten years…") is not a heading; `linkedin.com/company/acme` is not a personal LinkedIn link | Pass |
| Interview prep | `validatePastedJD`: exactly 40 chars accepted, 39 rejected, whitespace-only rejected; numbered list inside a model answer — see M7 below | Pass / M7 open |
| Cover letter | Corrupt storage reads as `[]`, never throws; saving the same id twice replaces in place — still one letter, newest body | Pass |
| Resume builder | Whole-token: `Java` does not match `JavaScript`, `C++` matches; `validateBuilderFile` rejects a `.txt` file and an 11 MB PDF | Pass |

## Features 1–4 re-confirmation (§9)

- **Clock + daily plan, tailor + apply, ATS preview, interview prep:** no regressions. Full suite green (686 tests, including the 316 new 360° tests), plus the spot-check probes above, which re-prove the key behaviours with independent inputs. The one behaviour change in the fix round touching feature 7 (`formatSalary` nullable) is covered by B5 above and by the jobs list/detail pane handling null.

## New bugs found in this pass

- **N1 — `parseMoney` treats symbols-only input as `$0`.** `web/src/lib/offers.ts:parseMoney` — `Number("")` is `0` after stripping `$`/`,`, so `"$"`, `","`, `"$$$,,,"` all parse to `0`. Inline editor shows `0` and the table shows `$0` for a figure the user never entered; the manual form accepts `$` as a valid `$0` base salary. *Repro:* type `$` into any inline money field, or submit the manual form with base salary `$`. *Expected:* treated as empty/invalid. *Severity:* Minor — no corruption, no NaN, but a fabricated `$0` figure.
- **N2 — Bare 10-digit phone number not detected.** `web/src/lib/ats-preview.ts:PHONE_RE` — `5715550142` (no separators) does not match, so the resume shows "Phone not found". Pre-existing, not a fix-round regression. *Repro:* `analyzeParsedText("Alex\n5715550142\nSUMMARY\nHi.\n").hasPhone` is `false`. *Severity:* Minor — phones in resumes are almost always written with separators.

## Minors backlog (updated)

- **M1 — Invalid clock start date silently becomes "Active" with NaN days left.** Still open — re-verified this pass: `clockStatus("not-a-date")` returns `{state: "active", daysLeft: NaN, deadline: Invalid Date}` (JSON serialises the NaN as `null`). `web/src/lib/h1b-clock.ts:clockStatus`.
- **M2 — Future start date can be saved from the profile page.** Still open — the profile-page change in this tree is only a link to `/clock`; the zod schema is still `z.string().optional()` with no future check (`web/src/app/profile/page.tsx:38`).
- **M3 — The 7-day plan repeats the same job every day when there is only one sponsor job.** Still open (not re-probed; unchanged code).
- **M4 — Mock-interview job falls back to a non-sponsor match.** Still open (unchanged code, `daily-plan.ts:200`).
- **M5 — Confirm-apply overwrites the source resume before the apply succeeds.** Still open (unchanged code).
- **M6 — Phone regex matches date ranges.** **Fixed** by the ATS-1 fix (see above).
- **M7 — Model answers are truncated at numbered lists.** Still open — re-verified this pass: `parsePrepReply` on a reply whose model answer contains `1. …` / `2. …` lines returns the answer truncated to `"I rebuilt the service."`, the list items silently dropped (`web/src/lib/interview-prep.ts:199-201` — `QUESTION_START` matches `1. …` lines).
- **M8 — Grounding check flags ordinary phrases as ungrounded roles.** Still open (unchanged code).
- **M9 — Every cover-letter export creates a new builder document.** Still open (unchanged code).
- **M10 — Empty-options error asserts a specific cause.** Still open (unchanged code).
- **M11 — "Remote (US)" does not count as remote.** Still open — re-verified this pass: `profileOverlapsJob` on a `Remote (US)` job with no shared skills returns `null` (`web/src/lib/sponsor-alerts.ts:85`, exact `=== "remote"` match).
- **M12 — Manual offer form has no currency input.** Still open (unchanged code).
- **M13 — Clock page future-date check uses the UTC date.** Still open (unchanged code).
- **N1 — `parseMoney` symbols-only input parses as `0`.** New, see above.
- **N2 — Bare 10-digit phone number not detected.** New, see above.

## What was verified and holds up (no action)

- **F1:** frozen clock math, urgent/expired states, singular/plural day labels, empty-plan message, plan even after expiry.
- **F2:** LCS diff (duplicates, reordering, case-sensitivity), out-of-range picks ignored, approval-gated apply path untouched by the fix round.
- **F3:** `normalizeResumeText` null-text handling, 400/404 error surfacing, no invented scores.
- **F4:** pasted-JD length validation (40/39 boundary), unparsed replies returned as received, grounding citations.
- **F5:** cover-letter local save/load/delete round-trips, corrupt-storage tolerance.
- **F6:** seen-set honoured on load, dismiss persistence, no false alerts for non-matching jobs.
- **F7:** zero-base totals, winner rules (ties, single-figure, visa), no market row, currency formatting.

## Still untested (needs the Mac / live services)

1. Live cover-letter DOCX/PDF export bytes (B1) — code + test assertions verified; one real export of each format is still the final proof. Backend suite could not run here (no Java/Maven on this VM).
2. Live Coach generation for Interview Prep and Cover Letter (Ollama on the Mac) — wiring verified, generation quality not.
3. Live browser behaviour of all 7 features (login persistence, mobile widths, real feed data).
4. Backend `GET /resumes/{id}/text` live 400/404 paths — code-verified only.

## Shippable?

**Yes — with two caveats.** All five majors (B1–B5) and both ATS bugs are fixed as far as this VM can prove; the full web suite is green (686 tests), `tsc` and `next build` are clean, and my 33 independent adversarial probes pass. The two new findings (N1, N2) are minors and do not block shipping. Caveats: (1) B1's final proof is one real DOCX + one real PDF export on the Mac, since no export bytes could be produced here; (2) live-browser verification of all 7 features is still outstanding, as it was in the first pass.
