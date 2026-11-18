# QA Report — 7 Wow-Factor Features (web app) — 2026-11-18

**Tester:** Quester (chief QA) · **Branch:** `kotlin-migration` (head `a7822a2` + uncommitted work) ·
**Scope:** Features 1–7 below, web app only, uncommitted working tree. No product files modified, nothing committed, no GitHub issues filed (per task brief).

## Baseline (re-run, confirmed)

| Check | Result |
|---|---|
| Web Vitest suite | **23 files, 361 tests, all pass** (34.9 s) |
| `tsc --noEmit` | Clean, exit 0 |
| `next build` | Clean, exit 0 — `/clock`, `/cover-letter`, `/offers` all build as static routes |
| Adversarial sweep (temporary Vitest file, 20 tests) | **20/20 pass** — every pinned behaviour below was reproduced by a test, then the file was deleted |

**Not run here (cannot run on this VM):** live app in a browser, live Coach generation (needs Ollama on the Mac), live export byte-check (needs the resume-builder service). Where a verdict depends on one of those, it says so.

## Verdicts

| # | Feature | Verdict | Notes |
|---|---|---|---|
| 1 | 60-Day Clock + Daily Plan | **Pass with minors** | Frozen clock math correct (start day = 60 left, 14 left = urgent, day 61 = expired, UTC-midnight, DST-safe). Unset → setup prompt only, no fake countdown. Daily plan is derived from real matches/jobs/documents; with no data it has zero filler items (proven by test). Minors M1–M4. |
| 2 | One-Click Tailor + Apply | **Pass with minor** | Real Co-Pilot route (`requestCopilot`, one call per section); diff computed from real original vs tailored text via LCS; apply goes through the existing approval-gated path (`useApplyJob` → `applyForJob` → `POST /approvals` `APPLY_JOB`); nothing is sent until approved. FAILED/empty Co-Pilot sections are skipped with an honest note and keep their original text. Tracker flip on approval **verified end to end**: `web/src/app/approvals/page.tsx:44-53` parses `decided.payload.trackerId` on approve + `APPLY_JOB` and calls `updateTrackerEntry(..., {status: "APPLIED"})`, with a toast fallback telling the user to move it manually if that step fails; `applyForJob` (`lib/api.ts:543-556`) puts `trackerId` in the payload. The modal's "done" copy ("tracker moves to Applied automatically [on approval]") is accurate. Minor M5. |
| 3 | ATS Parse Preview | **Pass with minor** | Renders the real parser output verbatim (`<pre>{text}</pre>`); flags are only observable evidence (missing email/phone, absent/empty sections, U+FFFD lines); no scores, no invented ATS rules, and the no-confidence note is shown. Backend `GET /resumes/{id}/text` exists (`ResumeController.kt:48`), owner-only 404, and throws 400 `RESUME_NOT_READY` with the human-readable message "Resume text is not ready yet" when the text is blank — the BFF forwards status + body and the component shows that message honestly. Minor M6. |
| 4 | Interview Prep | **Pass with minors** | Questions come from the real endpoint (`GET /interviews/questions?jobId=`, backend `InterviewController.kt:27`); model answers come from the real Coach (`sendCoachMessage` → `POST /coach/chat` with `{message}` only — the full grounding prompt is the message body, no faked answers anywhere); grounding checks flag ungrounded employers/roles; pasted-JD validation (≥ 40 chars) works; 502/503/504 produce the honest "Could not reach the Coach — it needs Ollama running on your Mac" error. **Live Coach generation was NOT verified** — it needs Ollama on the Mac and cannot run here; wiring only. Minors M7–M8. |
| 5 | Cover Letter Generator | **Fail — 1 major (B1)** | Generation uses the real Co-Pilot endpoint with `active_section: "cover_letter"` — verified against `agent-brain/app/graph/copilot.py`: no allowlist on the section name, it is forced into the response, so the call is accepted. Inline editing works; save is localStorage and the UI says so ("Save on this device", toast "Saved on this device"). Export uses the real export route. **But the export content is broken (B1):** the letter is written into the builder document's `summary` slot and the export service renders it under a "Summary" heading as a single run — paragraph breaks are lost and a PDF export may fail outright (see B1). The UI does state the resume-layout caveat honestly; the paragraph-break loss is not stated. Minors M9–M10. |
| 6 | Sponsor-Job Alerts | **Fail — 2 majors (B2, B3)** | The rule is exactly as shown in the UI (`SPONSOR_ALERT_RULE` matches `sponsorAlertReason` + `findNewSponsorJobs`: sponsors H-1B AND backend match OR skills/location/headline overlap AND unseen). Seen-set marks only surfaced alerts. Alerts link to the real posting (`sourceUrl`). In-app-only is stated honestly in the UI footer. **But alerts never appear on page load (B2) and Dismiss doesn't stick (B3)** — both reproduced by component test. Minor M11. |
| 7 | Offer Comparison | **Fail — 2 majors (B4, B5)** | Manual entry + Tracker OFFER items both work; "Best" is highlighted only on money rows and visa=yes; location/work-type/benefits never name a winner; no market data anywhere; tracker jobs' listed ranges are labelled "Listed range:" as listings only. **But the inline editor corrupts figures to `$NaN` (B4) and a salary-less tracker job shows a fake `$0k – $0k` range (B5).** Minor M12. |

## Major bugs (fix round)

### B1 — Cover-letter export loses paragraph breaks; PDF export may fail outright
**Files:** `web/src/lib/cover-letter.ts:buildCoverLetterExportState` (letter → `summary` slot) ·
`resume-builder-service/src/main/kotlin/com/epitomejob/resumebuilder/service/ExportService.kt:59-62` (DOCX), `:165-168` + `:219-251` (PDF)
**Repro:** Generate a cover letter (greeting + 2–3 body paragraphs + closing, joined with `\n\n` by `letterFromOption`), click Export PDF or Download DOCX.
**Expected:** The exported document contains the letter's paragraphs as separate paragraphs.
**Actual (from code trace — live export NOT run here, needs the service):** the letter is one `summary` string. DOCX `addLine` puts it in a single `XWPFRun` via `run.setText(text)` — a literal `\n` in a run is not a line break in Word (needs `<w:br/>`), so paragraphs collapse. PDF `line()` → `wrap()` splits only on spaces (`text.split(" ")`), so the `\n\n` stays embedded in a word token and `showText` is fed a control character that WinAnsi Helvetica has no glyph for — at best a missing glyph, at worst PDFBox throws and the export 500s (`EXPORT_FAILED`). No existing export test covers a multi-line summary.
**Evidence:** `ExportService.kt:234-251` (`wrap` splits on `" "` only); `ExportServiceTest.kt` has no newline/multi-paragraph case.
**Severity:** Major — export is a headline capability of this feature and a cover letter without paragraphs is unusable; the PDF-failure variant would be a 500 on the user's click.

### B2 — Sponsor alerts never appear on page load; user must click "Check for new sponsor jobs"
**File:** `web/src/components/sponsor-alerts.tsx:29-31` (initial `useState`) vs `:38-43` (`scanned` memo)
**Repro (proven by test):** Open `/clock` with matching sponsor jobs in the feed. The component mounts while `jobs`/`matches`/`profile` are still loading (empty props from `useJobs`/`useMatches`/`useProfile`), so the one-time `useState` initializer scans an empty feed and `alerts` stays `[]`. When the feed arrives, only the `scanned` memo recomputes — `alerts` state is never re-derived. The page shows "No new sponsor jobs right now" and no badge until the user clicks "Check for new sponsor jobs".
**Expected:** Alerts populate once the feed/profile have loaded.
**Severity:** Major — the feature's core value (surfacing new sponsor jobs) is invisible on arrival; it looks like there are no alerts when there are.

### B3 — Dismissed sponsor alerts come back on the next check
**File:** `web/src/components/sponsor-alerts.tsx:72-74` (`dismiss`) vs `:49-59` (`checkForNew`/`markJobsSeen`)
**Repro (proven by test):** With an alert showing, click Dismiss → it disappears. Click "Check for new sponsor jobs" → the same alert reappears.
**Expected:** Dismissing an alert marks the job seen (or otherwise persists the dismissal) so it does not resurface.
**Actual:** `dismiss` only removes the alert from local state; the job is never added to the seen-set, so the next scan (which reads the seen-set) surfaces it again.
**Severity:** Major — Dismiss is a lie; users will dismiss the same alert repeatedly.

### B4 — Inline offer editor turns non-numeric input into `$NaN` in the comparison table
**File:** `web/src/app/offers/page.tsx` (Base salary / Bonus / Signing bonus inline `onChange` handlers) vs `web/src/lib/offers.ts:parseMoney` (manual form, validates)
**Repro (proven by test):** Add two offers, then type `abc` into an inline Base salary box. The handler does `Number(e.target.value.replace(/[$,]/g, ""))` → `NaN`, stores it, and the table renders `formatMoney(NaN)` = `"$NaN"`; Total cash becomes `NaN` too.
**Expected:** Non-numeric input is rejected (or treated as empty), as the manual form already does via `parseMoney` (which returns `null` for non-numbers).
**Actual:** The comparison table — the feature's core output — displays `$NaN`, and `bestMoneyIds` silently names no winner for that row.
**Severity:** Major — user-visible data corruption in the comparison output; the manual form proves the codebase already knows how to validate this.

### B5 — Tracker job with no listed salary shows a fake "Listed range: $0k – $0k USD"
**File:** `web/src/components/offers/offer-comparison.tsx: ListedRange` → `web/src/lib/jobs.ts:formatSalary`
**Repro (proven by test):** Compare a tracker OFFER whose job has `salaryMin: null, salaryMax: null` (both nullable per `web/src/lib/types.ts:28-29`). `formatSalary(null, null)` computes `Math.round(null / 1000)` = `0` → `"$0k – $0k"`, rendered as "Listed range: $0k – $0k USD".
**Expected:** No listed range shown when the posting states none (or "Not listed").
**Actual:** A fabricated $0 salary range is presented as the posting's own data.
**Severity:** Major — false salary data shown as fact, in a feature whose whole point is honest numbers.

## Minor bugs (backlog)

- **M1 — Invalid clock start date silently becomes "Active" with NaN days left.** `web/src/lib/h1b-clock.ts:clockStatus` — `clockStatus("not-a-date")` returns `{state: "active", daysLeft: NaN, deadline: Invalid Date}`. Only reachable via corrupt profile data, but the component then renders an "Active" badge with no countdown. *Repro:* `clockStatus("not-a-date", new Date("2026-11-17"))`.
- **M2 — Future start date can be saved from the profile page.** The clock page rejects future dates, but `web/src/app/profile/page.tsx:38` validates `h1bClockStartDate` as `z.string().optional()` with no future check, so a future date saved there makes the clock show 61 days left (`clockStatus("2026-11-18", 2026-11-17)` → `daysLeft: 61, state: "active"`). The guard should live with the field, not only on the clock page.
- **M3 — The 7-day plan repeats the same job every day when there is only one sponsor job.** `web/src/lib/daily-plan.ts:buildDailyPlan` rotates `sponsorJobs[offset % length]`; with 1 job, all 7 days show the identical "Apply to …" item. The week card claims "One sponsor application a day" — repeating one application 7 times is filler by repetition.
- **M4 — Mock-interview job falls back to a non-sponsor match.** `web/src/lib/daily-plan.ts` — `interviewJob = sponsorJobs[0]?.job ?? matches[0]?.job`; a non-sponsor match becomes the "Mock interview" job on the 60-day sponsor-sprint page.
- **M5 — Confirm-apply overwrites the source resume before the apply succeeds.** `web/src/components/jobs/tailor-apply-modal.tsx:handleConfirmApply` calls `updateBuilderDocument` (persists the tailored state, replacing the original) *before* `saveBuilderDocumentToProfile` and the approval request. If either later step fails, the user is back on the review step with their original resume text already replaced and no undo.
- **M6 — Phone regex matches date ranges.** `web/src/lib/ats-preview.ts:PHONE_RE` — `\d{3}[\s.-]\d{4}` matches the "020-2024" inside "Acme 2020-2024", so a resume with no phone number shows "Phone found" and no `no-phone` flag. *Repro:* `analyzeParsedText` on a resume containing "Acme 2020-2024" and no phone → `hasPhone: true`.
- **M7 — Model answers are truncated at numbered lists.** `web/src/lib/interview-prep.ts:parsePrepReply` — `QUESTION_START` matches `1. …` lines, so a model answer containing a numbered list is split into new blocks that lack a "Model answer:" line and are silently dropped. *Repro:* reply with "Model answer: … rebuilt the service.\n1. First I mapped …\n2. Then I shipped …" → parsed answer ends at "service.".
- **M8 — Grounding check flags ordinary phrases as ungrounded roles.** `web/src/lib/interview-prep.ts:checkGrounding` — "I worked as a Team player at Acme" flags `Team` as an ungrounded role. Heuristic false positive; the UI hedges ("It may be paraphrased"), so low impact.
- **M9 — Every cover-letter export creates a new builder document.** `web/src/components/cover-letter/cover-letter-editor.tsx:handleExport` calls `createBuilderDocument` on each export with no reuse/dedup, so repeated exports accumulate "Cover Letter — …" documents in Resume Builder. The UI discloses that one document is added; the accumulation is not.
- **M10 — Empty-options error asserts a specific cause.** `web/src/components/cover-letter/cover-letter-editor.tsx:handleGenerate` — when Co-Pilot returns no options it says "Co-Pilot asked for more detail before drafting — add a number, outcome, or detail…". That is the `REQUEST_METRICS` fallback's meaning, but the editor cannot distinguish it from any other empty-options cause.
- **M11 — "Remote (US)" does not count as remote.** `web/src/lib/sponsor-alerts.ts:profileOverlapsJob` — remote detection is `jobLocation === "remote"` exact match, so "Remote (US)", "Remote - US", etc. get no location overlap. *Repro:* job location "Remote (US)", profile in Reston, no shared skills → `null`.
- **M12 — Manual offer form has no currency input.** `web/src/app/offers/page.tsx` — `ManualOfferFields.currency` exists in the model and defaults to "USD", but the form never lets the user change it; a non-USD offer is compared as USD.
- **M13 — Clock page future-date check uses the UTC date.** `web/src/components/h1b-clock-view.tsx:saveDate` compares against `new Date().toISOString().slice(0, 10)` (UTC). In the US evening, a local-tomorrow date passes as "not future". Edge case; the frozen clock math itself is UTC-consistent.

## What was verified and holds up (no action)

- **F1:** unset → setup prompt only; deadline math incl. urgent (≤ 14 days) / expired; daily plan has no filler with no data; plan items link to real jobs/documents.
- **F2:** diff is real LCS over real texts; `tailorTargetsFor` skips empty sections; modal's skipped-section note is honest; approval gate is the existing path; tracker flip happens only on approval (traced, see verdict table); "Tailor + Apply" button is wired on the jobs page (`app/jobs/page.tsx:219`).
- **F3:** 400-not-ready shows the backend's human-readable "Resume text is not ready yet" (BFF forwards status + body verbatim via `proxyJson`); 404 owner-only; no invented scores anywhere.
- **F4:** `sendCoachMessage` sends `{message}` only — the prompt is the message; nothing is faked when the Coach is down. `buildResumeContext` refuses to generate with no resume on file.
- **F5:** the "New Cover Letter" card exists in Resume Builder (`resume-home.tsx:75`, routes to `/cover-letter`); export does render the letter as the document body under the contact header, and the editor states that next to the export buttons.
- **F6:** `findNewSponsorJobs` honours the seen-set (positive control passed); `checkForNew` marks only surfaced alerts seen.
- **F7:** ties share "Best"; all-equal rows name no winner; single-figure rows name no winner; total-cash note states the unstated-bonus-as-0 rule; equity/benefits are explicitly not converted to cash.

## Still untested (needs the Mac / live services)

1. Live Coach generation for Interview Prep and Cover Letter (Ollama on the Mac) — wiring verified, generation quality not.
2. Live cover-letter PDF/DOCX export bytes (B1) — needs the resume-builder service; code trace says paragraph breaks are lost and PDF may 500, but the exact failure mode needs one real export.
3. Live browser behaviour of all 7 features (login persistence, mobile widths, real feed data).
4. Backend `GET /resumes/{id}/text` live 400/404 paths — code-verified only.

## Shippable?

**No — not yet.** Five majors block it: B2 + B3 break Sponsor-Job Alerts' core loop, B4 + B5 put false/corrupt figures in Offer Comparison, and B1 breaks Cover Letter export. Features 1–4 are shippable as-is (minors only). Fix round should cover B1–B5; M1–M13 go on the backlog.
