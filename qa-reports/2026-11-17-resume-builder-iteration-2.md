# QA Report — Resume Builder, second iteration (Teal-gap close)

- **Date:** 2026-11-17
- **Tester:** Quester (chief QA)
- **Scope:** `web/src/app/resume-builder/page.tsx`, `web/src/components/resume-builder/designer-panel.tsx`, `web/src/lib/api.ts`, `web/src/lib/resume-builder.ts`, `web/tests/resume-builder.test.tsx` (modified); `web/src/components/resume-builder/builder-menu.tsx`, `job-matcher-panel.tsx`, `quick-tour.tsx`, `resume-home.tsx` (new)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, iteration-2 work uncommitted in the working tree
- **Method:** full web suite + `tsc --noEmit` + `next build` + dev-server SSR curl + hand-tracing of every flow against the real code paths + a temporary adversarial Vitest file (17 checks, deleted afterwards — no scratch file left in the repo). The author-written suite was **not** trusted on its own: it was re-probed where it only asserts the easy half of a claim.

## Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/) | **15 files, 217 tests, all pass** |
| `npx tsc --noEmit` | clean (exit 0) |
| `npx next build` | clean; `/resume-builder` builds |
| Dev server `GET /resume-builder` | **HTTP 200**, page server-renders (Suspense fallback, then client render) |

## Per-flow verdicts

| # | Flow | Verdict | Evidence |
|---|---|---|---|
| 1 | Home / resume list: action cards, Recent Resumes search + sort, open, "Match a job" deep-link; NO cover-letter card; NO Delete action | **PASS** (with a note) | `resume-home.tsx:52-115` renders exactly 3 action cards (New Resume / Start from job description / Start from template) + Import link; no cover-letter text anywhere in `src/app/resume-builder` or `src/components/resume-builder` (grep: only a comment explaining its absence, `resume-home.tsx:4-6`); no Delete UI; no `DELETE /resume-builder/documents` in `api.ts`. Author suite `ResumeHome` tests pass (cards, search, sort, open, Match-a-job handlers, empty state). My adversarial checks confirmed: recent sort puts null-`updatedAt` last, title sort is case-insensitive A–Z, search is case-insensitive substring, `normalizeBuilderDocuments` tolerates `null`/`{}`/`{documents: null}`. Note: home doc card key is `doc.id ?? doc.title ?? "doc"` (`resume-home.tsx:225`) — two malformed docs with no id/title would collide on React keys (cosmetic, not filed as a bug). |
| 2 | Quick Tour: first-visit welcome modal, shown once via localStorage, 4 steps, re-openable from Menu, does not block editor, does not reappear | **PASS** | `quick-tour.tsx:16-20` 4 steps; welcome card "Ready to Improve Your Resume!" with Start Quick Tour / Skip the Tour. Page-level adversarial test (my file, since deleted): first visit with the seen flag absent → modal appears; Skip → `localStorage["epitomejob:builder-tour-seen"] === "1"` and modal gone. `closeTour` (`page.tsx`) sets the flag on every close path (Skip, Done, mid-tour Skip). Re-open via Menu sets `showTour` true (`page.tsx:1241`). The modal unmounts on close so the step resets to welcome on every fresh open. Observation (not a bug): on a first visit the modal overlays the editor even on a `?documentId=` deep link until dismissed — but it is dismissible in one click and never returns. |
| 3 | Job Matcher: paste/search, gauge + skills table (Hard/Soft, matched/missing), deterministic scores, empty-JD prompt, no-keyword message, pick-a-job fills JD | **FAIL** — 2 major bugs (B-2, B-3 below) | The plumbing is correct and proven: empty JD → prompt card, no keywords → "No keywords found…" message, pick-a-job fills the JD (`job-matcher-panel.tsx`, author tests pass), scoring is deterministic (same input → identical `JobMatch`, verified). But the keyword extraction drops short tech terms (B-2) and matches by substring, so a JavaScript-only resume "matches" the keyword `java` (B-3). The gauge/table render from the (wrong) computed data, so the UI looks right while the numbers are wrong. |
| 4 | Content section controls: include/exclude checkbox → live preview, + adds entry and opens section, accordion one-at-a-time, "Improve with AI" expands its section | **FAIL** — 1 major bug (B-1 below) | Checkbox wiring, add-entry-open, single-`expandedSection` accordion, and `onImprove` expanding the section (`page.tsx:1068-1073`) are all correct in code and covered by the author suite ("add-entry opens the section, and unchecking a section hides it from the preview" passes). But "hides it from the preview" is only half true: the section's *content* is removed while its `<h2>` heading stays in the preview (B-1). The author test at `tests/resume-builder.test.tsx:808-824` asserts only that content is gone — it never checks the heading. |
| 5 | Designer controls: Location Alignment (Left/Right) and Skills Layout (Comma / Comma Separated List / Columns) wired to live preview; Page Setup / margins still work | **PASS** | `designToCss` (`resume-builder.ts:262-289`) emits `display: block` + inline dates for Left and `justify-content: space-between` for Right; skills list/columns emit `.skills-list` / `.skills-cols` CSS. End-to-end through `renderTemplateHtml`: list layout renders `<ul class="skills-list">`, columns renders `<div class="skills-cols">`, comma renders `Java, Spring Boot, Kafka` with neither class (my adversarial checks, all passed). Preview applies it via one override `<style data-design>` (`resume-preview.tsx:33-40`). Margins: `MarginStepper` clamps 0–96px and `designToCss` emits the padding; author DesignerPanel test covers font/line-height/accent/sections/margins. |
| 6 | Menu dropdown: Import, Duplicate (disabled with no content), Quick Tour, Back to All Resumes; no dead/fake item | **PASS** | `builder-menu.tsx:60-104`: exactly 4 items, each wired to a real handler; no Delete/LinkedIn/fake entry. **Gap in the author suite: BuilderMenu had zero test coverage** — I covered it with my temporary file (since deleted): all 4 items fire their handler and close the menu; Duplicate is `disabled` when `duplicateDisabled` and cannot be clicked; Escape closes the menu. Page wiring verified by hand: `duplicateDisabled={!hasContent}` (`page.tsx:1238`), `hasContent` checks full name / summary / experience / education / skills (`page.tsx:887-892`); Import clicks the hidden file input → `handleFile` → parse → template library; Duplicate POSTs a real copy via `createBuilderDocument` (`page.tsx:862-881`); Back goes home and invalidates the documents query. Minor: `hasContent` ignores contact fields other than full name (email/phone/LinkedIn alone do not enable Duplicate) — see M-5. |
| 7 | Cross-cutting: no Teal branding, no new paid dependencies, no backend API contract change, graceful empty/error states, home-first flow does not break entry/upload | **PASS** | "Teal" appears only in code comments and as one accent-swatch label ("Teal" `#0f766e`, `resume-builder.ts:254`) — no Teal logo/name in any UI string. No `package.json` change (no new deps, paid or otherwise). `api.ts` diff adds only `GET /resume-builder/documents` + `useBuilderDocuments`; no existing call signature changed. Empty/error states: home shows "Could not load your resumes…" on list failure, "Loading resumes…", "No resumes yet" / "No resumes match that search"; Job Matcher shows "No jobs match that search."; entry/upload flow unchanged (upload → parse → template library, `page.tsx:726-795`). Dev-server SSR returns 200. |

## Bugs found

### B-1 — MAJOR: Hiding a section removes its content but leaves its heading in the live preview
- **File:line:** `web/src/lib/resume-builder.ts:560-575` (`applyHiddenSections` + `templateBody`, lines 177-190)
- **Steps to reproduce:** (1) Open any resume with Experience/Education/Skills filled in. (2) In Content mode, uncheck "Include Experience section" (or use the Designer → Sections tab to hide any section). (3) Look at the live preview.
- **Expected:** the Experience section — heading and content — disappears from the preview (the feature's own claim: "excluded sections hidden"; Designer tab copy: "Hidden sections stay in your document — they are only hidden from the page").
- **Actual:** the bullets/roles vanish but `<h2>Experience</h2>` remains, leaving an orphan empty heading. Same for Summary, Education, Skills.
- **Evidence:** adversarial test `renderTemplateHtml(BUILTIN_TEMPLATES[0], sampleBuilderState(), { hideSections: ["experience","education","skills","summary"] })` → output still contains `<h2>Experience</h2>`, `<h2>Education</h2>`, `<h2>Skills</h2>`, `<h2>Summary</h2>` (Vitest `AssertionError: expected '…' not to contain '<h2>Experience</h2>'`). Root cause: `templateBody()` hard-codes the `<h2>` headings outside the `{{tokens}}`; `applyHiddenSections` only empties the token values. The author test (`tests/resume-builder.test.tsx:808-824`) asserts only that content strings are gone, so it never caught this. The same defect applies to backend templates whose HTML carries its own section headings — hiding a section empties the `{{experienceHtml}}` token but cannot remove the template's heading.
- **Suggested fix:** make the hidden-section handling heading-aware (e.g. strip the heading block for a hidden section in `renderTemplateHtml`, or render headings as part of the section tokens).

### B-2 — MAJOR: Job Matcher (and Analyzer keyword check) silently drops short tech keywords — aws, sql, api, git, c#, go
- **File:line:** `web/src/lib/resume-builder.ts:395-407` (`keywordsFromJobDescription`, the `word.length < 4` filter)
- **Steps to reproduce:** (1) Open Job Matcher. (2) Paste: "We need AWS, SQL, API design and Git experience for this role." (3) Read the match score and skills table.
- **Expected:** `aws`, `sql`, `api`, `git` appear as keywords to match — they are among the most common terms in real job descriptions.
- **Actual:** extracted keywords are only `['design', 'need']`. AWS/SQL/API/Git are discarded by the `length < 4` rule, so the score is computed over junk words and the skills table never shows the terms that matter. A JD consisting mostly of short tech terms yields a near-empty or null keyword set ("No keywords found in that job description").
- **Evidence:** adversarial test — `keywordsFromJobDescription("We need AWS, SQL, API design and Git experience for this role.")` returned `['design', 'need']` (`AssertionError: expected [ 'design', 'need' ] to include 'aws'`). Same extraction feeds `analyzeResume`'s keyword check (`resume-builder.ts:478`), so the Analyzer has the identical blind spot (my AWS-only JD check passed only because `cloud`/`infrastructure` are ≥ 4 chars — `aws` itself was still ignored).
- **Suggested fix:** keep the ≥ 4-char rule for generic words but allowlist known short tech terms (`aws`, `sql`, `api`, `git`, `c#`, `go`, `ml`, `ai`, `css`, …) — ideally the same `TECH_TERMS` set that already exists for classification.

### B-3 — MAJOR: Keyword matching is substring-based, so a JavaScript-only resume "matches" the keyword `java`
- **File:line:** `web/src/lib/resume-builder.ts:540-560` (`matchJobDescription`, `haystack.includes(k)`) — also `analyzeResume` keyword check, line ~478
- **Steps to reproduce:** (1) Resume with skills `JavaScript, React` and summary "Frontend engineer working in JavaScript and React." (no Java anywhere). (2) Job Matcher JD: "Looking for a Java developer with Spring experience." (3) Read the skills table.
- **Expected:** `java` → Missing.
- **Actual:** `java` → Matched (score inflated: 1 of 3 instead of 0 of 3 for that keyword), because `"javascript".includes("java")` is true. Same class of false positive: `react` matches "reaction"/"reactive", `rust` matches "trust", `go` would match "going" (currently masked by B-2's length filter).
- **Evidence:** adversarial test — `matchJobDescription(state, "Looking for a Java developer with Spring experience.")`, `java` row status was `'matched'` (`AssertionError: expected 'matched' to be 'missing'`). This directly contradicts the "counted, never guessed" claim: the count is wrong.
- **Suggested fix:** match on word boundaries (tokenise the resume haystack the same way the JD is tokenised and compare token sets) instead of `String.includes`.

## Minor issues / observations (not blocking)

- **M-1 — Job Matcher fires a wasted `GET /jobs?size=0` on mount.** `JobMatcherPanel` calls `useJobs({ size: 0 })` when there is no search query (`job-matcher-panel.tsx:96-98`), and `useJobs` has no `enabled` guard (`api.ts:421-427`), so every Job Matcher open hits the backend even before the user types. `buildJobsQuery` (`jobs.ts:87-106`) does emit `?size=0`.
- **M-2 — Job search has no debounce.** Each keystroke in "Search titles or companies" fires a new jobs request (`job-matcher-panel.tsx:96-98`).
- **M-3 — "New Resume" creates a backend document on every click, even if abandoned.** `handleNewResume` (`page.tsx`, ~line 835) POSTs an "Untitled resume" document immediately; closing without editing leaves it in the Recent list. (The swallowed POST failure — editor still opens locally — is by design and fine.)
- **M-4 — Multi-word `TECH_TERMS` can never classify a keyword.** `TECH_TERMS` contains "spring boot", "machine learning", "power bi", "google cloud", "artificial intelligence" (`resume-builder.ts:512-528`), but keywords are single tokens, so those JD keywords are always classified "Soft Skill". (Single-word entries such as "spring" work.)
- **M-5 — Duplicate's "has content" ignores most contact fields.** `hasContent` (`page.tsx:887-892`) counts only full name / summary / experience / education / skills; a document with only email/phone/LinkedIn still has Duplicate disabled.
- **M-6 — Inconsistent no-template entry.** Opening a home document with no `templateId` goes straight to the editor with the Modern fallback (`handleOpenDocument`, `page.tsx`), while the `?documentId=` load path opens the Template Library instead. Cosmetic.
- **Coverage gap (fixed by probing, not a product bug):** `BuilderMenu` had no tests in the author suite. My temporary tests proved all four items, the disabled Duplicate, and Escape-to-close; recommend adding permanent BuilderMenu tests.
- **Note:** the first-visit tour overlays the editor even on a `?documentId=` deep link until dismissed — acceptable, one click to skip, never reappears.

## What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, tour modal, gauge rendering, or click-through in a real browser. All UI behaviour above is from jsdom tests + code tracing.
- **No backend / no Docker on this VM:** the documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end (home list from a live backend, upload + parse, Duplicate creating a real copy, Job Matcher search against live jobs, export download) is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.
- **No real user data:** all flows used sample/seeded state, not a real resume or JD.

## Re-test (2026-11-17, after the author's fixes)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, fixes uncommitted in the working tree
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (26 checks, deleted afterwards — no scratch file left, no product file modified). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks, including different inputs and angles from the author's tests (e.g. M-5 with phone-only instead of email-only, M-6 with a `classic` template instead of no template, M-3 asserting first Save *does* create the document).

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 232 tests, all pass** (was 217 before the fix; +15 author regression tests) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **20 of 26 passed** — all 20 core re-probe checks passed; the 6 failures are the new findings N-1..N-4 below, all in the extra-probes section |

### Per-bug verdicts — all 9 FIXED

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| B-1 | **FIXED** | `renderTemplateHtml(BUILTIN_TEMPLATES[0], sampleBuilderState(), { hideSections: ["experience","education","skills","summary"] })` contains **no** `<h2>Experience</h2>` / `<h2>Education</h2>` / `<h2>Skills</h2>` / `<h2>Summary</h2>`, keeps "Alex Morgan". Same result on the real backend template shape (verbatim section markup from `resume-builder-service/src/main/resources/templates/modern.html`: `<h2>Summary</h2><p>{{summary}}</p>`, `<h2>Experience</h2>{{experienceHtml}}`, `<h2>Skills</h2><p>{{skillsHtml}}</p>`), on all 3 built-in templates, under list and columns skills layouts, and when hiding only one section (other three headings kept). Non-hidden empty section still shows its heading (`emptyBuilderState()` renders all four `<h2>`s) — unchanged behaviour confirmed. |
| B-2 | **FIXED** | `keywordsFromJobDescription("We need AWS, SQL, API design and Git experience for this role.")` includes `aws`, `sql`, `api`, `git`. `"C# and .NET developer"` yields `c#`, `net`, `developer`. `go`/`ml`/`ai`/`css`/`sql` all survive; long-word extraction unchanged (`senior`, `engineer`, `leadership`, `building`, `distributed`, `systems`); short non-tech words still dropped (`keywordsFromJobDescription("The cat sat on the mat")` → `[]`). `C++` also survives (`c++` is in `TECH_TERMS` and `+` is in the tokenizer's kept set). |
| B-3 | **FIXED** | JavaScript-only resume (skills `JavaScript, React`; summary "Frontend engineer working in JavaScript and React.") vs JD "Looking for a Java developer with Spring experience." → `java` Missing, `score 0`, `totalKeywords 3`, `matchedKeywords 0`; `analyzeResume` keyword check also `0 of 3`, not passed. Real matches still work: "Spring Boot" matches `spring` and `boot`; `containsKeyword("…reaction…", "react")` false; `containsKeyword("I trust this team.", "rust")` false; `containsKeyword("C# developer", "c#")` true; `containsKeyword("…going…", "go")` false, `("Go developer", "go")` true; `java` does not match `JavaScript`, `javascript` does. |
| M-1 | **FIXED** | Mounted `JobMatcherPanel` with an empty query: **0** `GET /api/jobs` calls after mount + 500 ms settle (mocked `fetch` asserted). |
| M-2 | **FIXED** | Three rapid `fireEvent.change` values (`"J"` → `"Ja"` → `"Jav"`) produced exactly **1** `/api/jobs` call, for `query=Jav` only. Clearing the query fired nothing further (still 1 call after 500 ms). |
| M-3 | **FIXED** | Clicking New Resume opened the editor (`Export PDF` visible, `Template: Modern`) with **0** `POST /api/resume-builder/documents` calls; clicking Save then POSTed exactly once — creation is deferred to first Save via `ensureDocumentId`, not removed. |
| M-4 | **FIXED** (with a gap — see N-3) | `matchJobDescription(state, "Machine learning and Power BI experience")` classifies `machine`, `learning`, `power` as Hard Skill. |
| M-5 | **FIXED** | With **only a phone number** filled (author's test used email), Duplicate is enabled in the Menu. `hasContent` (`page.tsx:873-885`) now includes title/email/phone/location/linkedin. |
| M-6 | **FIXED** | `?documentId=doc-2` with `templateId: "classic"` goes straight to the editor (`Export PDF` visible, `Template: Classic`, no "Choose a template" modal) — same as the no-template case. Load path sets `setStep("editor")` (`page.tsx:725`). |

### New bugs found in the fixes

- **N-1 — MINOR (latent): B-1's heading strip only recognises two body shapes, so a differently-wrapped section keeps its orphan heading.** `web/src/lib/resume-builder.ts:803-820` (regex at line 814) strips `<h2>Label</h2>` only when followed by an empty `<p></p>` or `<div class="skills"></div>`. Probes: template `<h2>Skills</h2><div class="skills-list">{{skillsHtml}}</div>` → output still contains `<h2>Skills</h2><div class="skills-list"></div>`; template `<h2>Experience</h2><section>{{experienceHtml}}</section>` → still contains `<h2>Experience</h2><section><p></p></section>`; template `<h2>EXPERIENCE</h2>{{experienceHtml}}` → heading survives (label match is case-sensitive). **Not live today:** all 3 built-in templates and all 3 backend templates (`resume-builder-service/src/main/resources/templates/{modern,classic,minimal}.html`) use only the two recognised shapes, so no shipped template hits this. It will bite the first template that wraps a section differently or uppercases its headings. Suggested fix: strip the heading whenever the section body is empty after substitution, regardless of the wrapper element, and match the label case-insensitively.
- **N-2 — MINOR: B-2 still drops "CI/CD" — the tokenizer splits it before the allowlist can save it.** `web/src/lib/resume-builder.ts:371,376,537-555`. `keywordsFromJobDescription("CI/CD pipeline engineer")` returns only `['engineer', 'pipeline']`: `/` is not in the tokenizer's kept set (`/[^a-z0-9+#.]+/`), so `CI/CD` becomes `ci` + `cd`, each 2 chars and neither in `TECH_TERMS`, so both are dropped — even though `TECH_TERMS` contains both `"ci/cd"` and `"cicd"` (dead entries: no keyword can ever equal them). Same shape of dead entry: `".net"` can never occur (leading `.` is stripped; the live token is `"net"`, which is also listed). Expected: `ci/cd` (or `cicd`) appears as a keyword — CI/CD is as common in JDs as AWS/SQL. Suggested fix: keep `/` in the tokenizer, or normalise `ci`+`cd` / add `ci`/`cd` handling.
- **N-3 — MINOR: M-4 is half-fixed — "bi" from "Power BI" never becomes a keyword at all.** `web/src/lib/resume-builder.ts:376` (extraction) vs `:557-565` (classification). `keywordsFromJobDescription("Machine learning and Power BI experience")` returns `['learning', 'machine', 'power']` — no `bi`. The `< 4` filter checks `TECH_TERMS` only, and `"bi"` is not in it; it exists only in `TECH_WORDS` (derived from `"power bi"`), which `skillTypeOf` checks but the extractor never consults. So the skills table shows `machine`/`learning`/`power` as Hard but silently drops the `bi` half of "Power BI", undercounting that skill. Expected: `bi` appears as a keyword (Hard Skill). Suggested fix: let the extraction allowlist consult `TECH_WORDS` too (or add `"bi"` to `TECH_TERMS`).
- **N-4 — MINOR: B-3's whole-token match has no plural handling, so "APIs" in a resume does not match keyword "api".** `web/src/lib/resume-builder.ts:397-400` (`containsKeyword`). Resume skills `["REST APIs", "GraphQL"]` vs JD "Looking for API experience." → `api` is Missing (expected Matched). Whole-token strictness traded B-3's false positives for false negatives on plurals — common in real resumes/JDs (`APIs`, `services` vs `service`, `tests` vs `test`, `microservices` vs `microservice`, and the reverse direction: JD keyword `apis` vs resume `API`). Expected: a trailing-`s` plural counts as a match (or documented stemming). Suggested fix: allow an optional trailing `s` in `containsKeyword` (e.g. `keyword` + `s?` before the `(?!\w)` check, with care for keywords already ending in `s`).

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, tour modal, gauge, or click-through in a real browser. All UI behaviour above is from jsdom tests + code tracing.
- **No backend / no Docker on this VM:** documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end against a live backend is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.

### Recommendation

**Ship B-1/B-2/B-3/M-1..M-6 as fixed** — all 9 original bugs are confirmed fixed by independent re-probes, the full suite is green (232 tests), and `tsc` is clean. The 4 new findings (N-1..N-4) are all minor: N-1 is latent (no shipped template hits it), N-2/N-3/N-4 are real but narrow scoring gaps on specific terms (`CI/CD`, `bi`, plurals like `APIs`). None blocks shipping; file them as follow-ups, with N-2/N-4 the most worth fixing next since `CI/CD` and `APIs` appear in a large share of real JDs/resumes.

## Recommendation

**Do not ship as-is.** B-1, B-2 and B-3 are all in the two headline claims of this iteration ("excluded sections hidden", "scores computed deterministically from the resume + JD, no invented scores"). B-2 and B-3 make the Job Matcher / Analyzer scores wrong on ordinary real-world input (any JD mentioning AWS/SQL/API/Git, any JavaScript resume against a Java JD). Fix B-1/B-2/B-3, add permanent regression tests for all three (plus BuilderMenu coverage), then re-test — I will re-run the adversarial checks against the fix.

## Re-test 2 (N-1..N-4)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, N-1..N-4 fixes uncommitted in the working tree
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (26 checks, deleted afterwards — no scratch file left, no product file modified). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks, including different inputs and angles from the author's tests (e.g. N-1 mixed-case label + heading attributes + uppercase `<H2>` tag, N-2 `front-end/back-end` and `a/b`, N-4 `css`/`ios`/`aws` singular-form probes). One of my own 26 checks initially failed on a wrong expectation of mine, not the code: I asserted `containsKeyword("microservices architecture", "service")` is true, but whole-token matching (B-3) correctly says false — `service` is not a standalone token inside the compound `microservices`, same as `java` vs `JavaScript`. Corrected to `false` and re-ran: 26/26 pass.

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 237 tests, all pass** (was 232 after the first fix round; +5 author regression tests) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **26 of 26 passed** |

### Per-bug verdicts — all 4 FIXED

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| N-1 | **FIXED** | (a) `<h2>Skills</h2><div class="skills-list">{{skillsHtml}}</div>` with skills hidden → heading AND wrapper gone, Summary heading kept. (b) `<h2>Experience</h2><section>{{experienceHtml}}</section>` with experience hidden → heading AND `<section>` gone. (c) `<h2>EXPERIENCE</h2>` with experience hidden → heading gone. Non-hidden section keeps its heading (content `Meridian Care` present); hiding skills does not strip the Experience heading; mixed-case `ExPeRiEnCe` with `<H2 class="sec">` also stripped; bare empty `<p></p>` after a heading stripped; whitespace + empty `<p>` inside a wrapper stripped; built-in template with all four sections hidden loses all four `<h2>`s and keeps `Alex Morgan`. |
| N-2 | **FIXED** | `keywordsFromJobDescription("CI/CD pipeline engineer")` returns exactly `['ci/cd', 'engineer', 'pipeline']` — no bare `ci`/`cd` fragments. `and/or` is not a keyword (`"and/or"` alone → `[]`); `site/remote` is not a keyword; `front-end/back-end` and `a/b` also dropped. `CI/CD.` (trailing period) and lowercase `ci/cd` both yield `ci/cd`; `CICD` still yields `cicd`. |
| N-3 | **FIXED** | `keywordsFromJobDescription("Machine learning and Power BI experience")` includes `bi` alongside `machine`, `learning`, `power`. `bi` is classified Hard Skill in `matchJobDescription`; bare `"BI developer needed"` also yields `bi`; generic short words still dropped (`"The cat sat on the mat"` → `[]`). |
| N-4 | **FIXED** | Resume skills `["REST APIs", "GraphQL"]` vs JD `"Looking for API experience."` → `api` Matched. Reverse direction works: keyword `apis` matches resume `API`. `service` matches standalone `services` and vice versa; `microservices` matches `microservice`. No regression: `java` does not match `JavaScript`, `test` does not match `testing`, `react` does not match `reaction`, `rust` does not match `trust`, `go` does not match `going`. |

### New bugs found in the fixes

- **N-5 — MINOR: N-4's singular-form fallback creates new false positives for short tech acronyms ending in "s".** `web/src/lib/resume-builder.ts:403-411` (`containsKeyword`, line 407: any keyword ending in `s` also tries its singular). Probes: `containsKeyword("CS degree from State", "css")` → **true** (singular `cs` matches "CS" — Computer Science); `containsKeyword("io streams", "ios")` → **true** (singular `io` matches I/O); `containsKeyword("aw shucks", "aws")` → **true** (singular `aw`). The `css`/`CS` collision is the realistic one: a resume saying "B.S. in CS" / "CS degree" falsely matches a JD keyword `css`, inflating the match score — the same false-positive class B-3 was fixed for, reintroduced in miniature. `ios`/`io` needs a Go-flavoured resume (rarer); `aws`/`aw` needs the interjection "aw" (negligible in real resumes). **Suggested fix:** only try the singular form for keywords longer than 3 chars — that keeps the intended plurals (`apis`→`api`, `services`→`service`, `microservices`→`microservice`) while excluding exactly the colliding 3-letter acronyms (`css`, `ios`, `aws`).
- **N-6 — MINOR (limitation, not wrong behaviour): `-es` plurals are still not matched.** `containsKeyword("classes in Java", "class")` → false; same shape for `status`/`statuses`. The N-4 fix only tolerates a trailing `s`, so `class` does not match its plural `classes`. Under-matching only, and `class`/`status` are rarer as JD keywords than `api`/`service`, so lower priority than N-5. Note as a known limitation, or extend the plural handling to `-es` if the fix for N-5 touches this code anyway.

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, tour modal, gauge, or click-through in a real browser. All UI behaviour above is from jsdom tests + code tracing.
- **No backend / no Docker on this VM:** documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end against a live backend is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.

### Recommendation

**Ship N-1..N-4 as fixed** — all four are confirmed fixed by independent re-probes, the full suite is green (237 tests), and `tsc` is clean. The 2 new findings are both minor: N-5 is a narrow false-positive on 3-letter `s`-ending acronyms (`css` vs "CS" the only realistic collision) and N-6 is an under-matching limitation on `-es` plurals. Neither blocks shipping; file them as follow-ups, with N-5 worth fixing next since it partially reopens the B-3 false-positive class.

## Re-test 3 (N-5, N-6)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, N-5/N-6 fixes uncommitted in the working tree (`web/src/lib/resume-builder.ts`, `containsKeyword`, lines 409-432)
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (36 granular one-assertion-per-`it` checks, deleted afterwards — no scratch file left, no product file modified). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks, including different inputs and angles from the author's tests (exact-acronym matches, `microservices` both ways, `watch`/`glass` `-es` stems, 3-char `-es` stems, and the task-flagged `status` vs `statu` subtlety). My temp file: **27 of 36 passed; the 9 failures are all my own adversarial probes, and they split into two distinct new findings (N-7, N-8) below** — none of the author's specified probes failed.

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 239 tests, all pass** (was 237 after re-test 2; +2 author regression tests for N-5/N-6) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **27 of 36 passed** (9 failures = N-7 and N-8, see below) |

### Per-bug verdicts

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| N-5 | **FIXED** | `containsKeyword("CS degree from State", "css")` false; `("io streams", "ios")` false; `("aw shucks", "aws")` false. Intended plurals still work: `("API experience", "apis")` true, `("REST services", "service")` true, `("REST APIs", "api")` true, `microservices` ↔ `microservice` both ways true. The length gate did not break exact matches: `("CSS and HTML", "css")`, `("AWS certified", "aws")`, `("iOS developer", "ios")` all true; `("B.S. in CS", "css")` false. |
| N-6 | **FIXED** | `containsKeyword("classes in Java", "class")` true; `("Java class", "classes")` true; `("status updates", "statuses")` true; `("statuses page", "status")` true. No regression: `("testing the app", "test")` false, `("JavaScript and React", "java")` false, `("reaction time", "react")` false. Other `-es` stems also work both ways where the stem is > 3 chars: `watch` ↔ `watches`, `glass` ↔ `glasses` all true. |

### New bugs found in the fixes

- **N-7 — MINOR: the singular-strip still fires on words that are already singular, so `containsKeyword` matches truncated nonsense stems — including the exact case the fix was asked to check.** `web/src/lib/resume-builder.ts:416` (`forms.push(base.slice(0, -1))`) runs for *any* > 3-char word ending in `s`, with no check that the word is actually a plural. Probes (all returned **true**, expected false): `containsKeyword("statu", "status")` → **true** — the task-flagged subtlety **fails**; `("clas", "class")` → true; `("classe", "classes")` → true; `("statuse", "statuses")` → true; `("glas", "glass")` → true. Two of the stems are real words, not just nonsense: `("Bos and colleagues", "boss")` → **true** (`Bos` is a surname — a resume naming a colleague/manager "Bos" would falsely match a JD keyword `boss`); `("pas the salt", "pass")` → true (`pas` is a real word, though unlikely in a resume). `("hello world", "status")` / `("hello world", "class")` are correctly false, so this is specifically the stripped-stem form matching, not a general substring leak. This is residual from the N-4 fix, not newly introduced by this round — the N-5 length gate only excluded 3-char words, it never distinguished plural `s` from singular `s` — but it is the same false-positive class as N-5 and the explicitly-named `statu` check does not pass. Real-world impact is negligible (`statu`/`clas`/`classe`/`statuse`/`glas` never occur in real resumes; `Bos`/`pas` are rare), so minor. **Suggested fix:** only strip a trailing `s` when the result is a plausible singular — e.g. strip `s` only if the word does not already end in `s` preceded by a stem that takes `-es` (i.e. for `class`/`status`/`glass`/`boss`/`pass`, do not generate the `-1` form at all; the `-es` handling already covers their plurals), and for `-es` plurals strip `es` only via the gated stem check, never strip a single `s` off an `-es` plural (`classes` → `class`, never `classe`).
- **N-8 — MINOR (limitation, not wrong behaviour): 3-char `-es` stems match only one way — the `> 3` length gate also gates the `-es` branch.** `web/src/lib/resume-builder.ts:413,424`. Probes: `containsKeyword("boxes of cables", "box")` → **false** (expected true) while the reverse `("box of cables", "boxes")` → true; `("buses and trains", "bus")` → **false** while `("bus stop", "buses")` → true. Cause: `box`/`bus` are 3 chars, so the whole `if (base.length > 3)` block — including the `else if (/(?:x|z|ch|sh)$/)` `-es` form — is skipped for the singular keyword, while the 5-char plural keyword enters the block and recovers the singular via its stem check. Under-matching only, and `box`/`bus`/`tax` are rare as JD keywords (rarer than `class`/`status`), so lower priority than N-7. **Suggested fix:** apply the `-es` form (`base + "es"`) for stems ending in s/x/z/ch/sh regardless of the `> 3` gate (keep the singular-strip gated, since that is what N-5 needed), or lower the `-es` gate to `>= 3`.

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, tour modal, gauge, or click-through in a real browser. All behaviour above is from jsdom tests + code tracing.
- **No backend / no Docker on this VM:** documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end against a live backend is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.

### Recommendation

**Ship N-5 and N-6 as fixed** — every probe the fixes were specified against passes with my own independent checks, the full suite is green (239 tests), and `tsc` is clean. The 2 new findings are both minor: N-7 is a narrow false-positive on truncated stems (`statu`/`clas`/`classe`/`statuse`/`glas`, plus the real words `Bos`/`pas`) that essentially never occur in real resumes, and N-8 is one-way under-matching on 3-char `-es` stems (`box`/`bus`). Neither blocks shipping; file them as follow-ups, with N-7 worth fixing next since it is the same false-positive class as N-5 and the explicitly-flagged `status` vs `statu` check still fails.

## Re-test 4 (N-7, N-8)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, N-7/N-8 fixes uncommitted in the working tree (`web/src/lib/resume-builder.ts`, `containsKeyword`, lines ~409-440; `does` added to `KEYWORD_STOPWORDS`, line 361)
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (32 granular one-assertion-per-`it` checks, deleted afterwards — no scratch file left, no product file modified, nothing committed, no GitHub issues filed for this run per the pipeline instruction). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks. My temp file: **24 of 32 passed; the 8 failures are all my own adversarial probes in one new finding (N-9) below** — none of the author's specified probes failed.

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 241 tests, all pass** (was 239 after re-test 3; +2 author regression tests for N-7/N-8) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **24 of 32 passed** (8 failures = N-9, see below) |

### Per-bug verdicts — both FIXED

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| N-7 | **FIXED** | All 7 nonsense-stem probes now FALSE: `("statu","status")`, `("clas","class")`, `("classe","classes")`, `("statuse","statuses")`, `("glas","glass")`, `("Bos and colleagues","boss")`, `("pas the salt","pass")`. The task-flagged `statu` vs `status` subtlety now passes. Real forms still match: `("status report","status")`, `("statuses page","status")`, `("class project","class")`, `("classes in Java","class")` all TRUE. |
| N-8 | **FIXED** | All 4 3-char `-es` stem probes now TRUE both ways: `("boxes of cables","box")`, `("box of cables","boxes")`, `("buses and trains","bus")`, `("bus stop","buses")`. |

### Supporting checks (all passed)

- **N-5 still holds:** `containsKeyword("CS degree from State", "css")` FALSE.
- **Regression spot-checks (6/6):** `("testing the app","test")` FALSE, `("JavaScript and React","java")` FALSE, `("reaction time","react")` FALSE, `("API experience","apis")` TRUE, `("REST services","service")` TRUE, `("REST APIs","api")` TRUE.
- **`does` stopword:** `keywordsFromJobDescription("What does the engineer do every day? The role does require leadership.")` contains `leadership` and does **not** contain `does`.
- **Control probe (my expectation confirmed, not a bug):** `cactus` ↔ `cactuses` matches both ways — the `-es` handling already covers a stem ending in `us` (`cactuses` → `cactus` via the stem-ends-in-`s` branch).

### New bug found in the fix

- **N-9 — MINOR (limitation, not wrong behaviour): `-es` plurals whose stem ends in "o" match neither way — the `-es` handling only knows stems ending in s/x/z/ch/sh.** `web/src/lib/resume-builder.ts:409-440` (`containsKeyword`). Probes (all returned **false**, expected true): `containsKeyword("tomatoes in the garden", "tomato")`, `("tomato soup", "tomatoes")`, `("potatoes and carrots", "potato")`, `("potato salad", "potatoes")`, `("heroes of the team", "hero")`, `("hero developer", "heroes")`, `("echoes in the hall", "echo")`, `("echo command", "echoes")` — 8/8 failed. Root cause, traced in the code: (a) singular direction — `tomato`/`hero`/`echo` end in `o`, so neither the `endsWith("s")` branch nor the `/(?:x|z|ch|sh)$/` branch adds the `+ "es"` form, so the plural is never generated; (b) plural direction — `tomatoes` strips `es` to stem `tomato`, which fails the `/(?:s|x|z|ch|sh)$/` stem test, so the code falls through to the regular-plural branch and pushes `base.slice(0, -1)` = `"tomatoe"` — itself a nonsense stem of exactly the N-7 kind (`tomatoe`/`heroe`/`echoe`, cf. `classe`), which matches nothing real. Under-matching only (no false positives). Real-world impact is very low: o-stem `-es` plurals are rare as JD keywords — `echo` is the only tech-plausible one (Amazon Echo / `echo` command); `tomato`/`potato`/`hero` essentially never appear in resumes or JDs. Same limitation class as N-6/N-8, narrower than both. **Suggested fix:** in the `-es` branch, also accept a stem ending in `o` (add `o` to the stem test, or treat `base + "es"` for stems ending in `o` the same as s/x/z/ch/sh), and make the plural→singular strip use the `es`-stripped stem whenever it is a plausible word rather than falling back to a single-`s` strip that manufactures `tomatoe`.

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, tour modal, gauge, or click-through in a real browser. All behaviour above is from jsdom tests + code tracing.
- **No backend / no Docker on this VM:** documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end against a live backend is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.

### Recommendation

**Ship N-7 and N-8 as fixed** — every probe the fixes were specified against passes with my own independent checks, the full suite is green (241 tests), and `tsc` is clean. The 1 new finding (N-9) is minor: one-way-under-matching in both directions, but only for o-stem `-es` plurals (`tomato`/`potato`/`hero`/`echo`), which are vanishingly rare as JD keywords. It does not block shipping; file it as a follow-up. Note the fix for N-9 should also remove the `tomatoe`-style nonsense stem the current fallback generates, since that is the same defect class as N-7.

## Re-test 5 (N-9)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, N-9 fix uncommitted in the working tree (`web/src/lib/resume-builder.ts`, `containsKeyword`, lines ~409-450)
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (44 granular one-assertion-per-`it` checks, deleted afterwards — no scratch file left, no product file modified, nothing committed, no GitHub issues filed for this run per the pipeline instruction). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks. My temp file: **33 of 44 passed; the 11 failures are all my own adversarial probes, and they split into three distinct new findings (N-10, N-11, N-12) below** — none of the author's specified probes failed. All 24 task-specified probes passed (8 N-9 cases both directions, 3 nonsense stems, 3 length-guard probes, 10 regression spot-checks).

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 243 tests, all pass** (was 241 after re-test 4; +2 author regression tests for N-9) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **33 of 44 passed** (11 failures = N-10, N-11, N-12, see below) |

### Per-bug verdict — N-9 FIXED

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| N-9 | **FIXED** | All 8 o-stem cases now TRUE both ways: `("tomatoes in the garden","tomato")`, `("tomato soup","tomatoes")`, `("potatoes in the garden","potato")`, `("potato soup","potatoes")`, `("heroes of the story","hero")`, `("hero of the story","heroes")`, `("echoes in the hall","echo")`, `("echo in the hall","echoes")`. All 3 nonsense stems now FALSE: `("tomatoe","tomatoes")`, `("heroe","heroes")`, `("echoe","echoes")` — no `tomatoe`/`heroe`/`echoe` form is generated any more. Length guard holds: `("goes above and beyond","go")` FALSE, `("go developer","goes")` FALSE, `("do the work","does")` FALSE. Regressions hold: N-7 `("statu","status")`, `("clas","class")`, `("Bos and colleagues","boss")` all FALSE; N-8 `("boxes of cables","box")`, `("buses and trains","bus")` TRUE; N-5 `("CS degree from State","css")` FALSE; B-3 `("testing the app","test")`, `("JavaScript and React","java")`, `("reaction time","react")` FALSE; plurals `("API experience","apis")`, `("REST services","service")` TRUE. |

### My own probes — what I chose and what happened

I probed o-stems that take only `-s` (`photo`/`photos`, `piano`/`pianos`): the plain `-s` plurals still match both ways (all 4 TRUE), so the new `+"es"` form does not break real matching — but it does over-generate (see N-12). I then probed the `-es` strip's fallback paths and found the strip assumes every word ending in `es` is stem + `es`, which is false for singulars ending in `e` whose plural is just `+s` (see N-10/N-11). One of my own expectations also turned out to be wrong in the other direction: I asserted `containsKeyword("doses of medicine", "dose")` is true as a control and it passed — the reverse direction works, confirming N-10 is one-way under-matching, not a two-way break.

### New bugs found in the fix

- **N-10 — MINOR: the `-es` strip misfires on singulars ending in "e" whose plural is just `+s` — `shoe`/`shoes`, `dose`/`doses`, `canoe`/`canoes`, `oboe`/`oboes` match only one way, and the strip manufactures nonsense stems.** `web/src/lib/resume-builder.ts:436-441` (`containsKeyword`, singular fallback). Probes (plural keyword → singular haystack, all returned **false**, expected true): `containsKeyword("shoe store", "shoes")`, `("canoe trip", "canoes")`, `("oboe player", "oboes")`, `("dose of medicine", "doses")`. Root cause, traced in the code: the plural ends in `es`, so the code strips `es` and takes the remainder as the singular — `shoes` → `sho`, `canoes` → `cano`, `oboes` → `obo`, `doses` → `dos` (the `s`-stem branch fires for `dos`) — instead of stripping one `s` to the true singular `shoe`/`canoe`/`oboe`/`dose`. The reverse direction still works (`("shoes on sale","shoe")`, `("canoes on the lake","canoe")`, `("doses of medicine","dose")` all TRUE via the `s?` suffix on the singular form), so this is one-way under-matching. It does NOT affect the N-9 words: `tomato`/`potato`/`hero`/`echo` genuinely are stem + `es`. Short `-oe` stems escape it by accident — `toes` → stem `to` (2 chars) fails the o-gate and falls through to the single-`s` strip, giving the correct `toe` (`("toe injury","toes")` TRUE) — so only stems of 3+ chars before the `e` misfire. The nonsense stems are also live false positives: `("Cano is my guide", "canoes")` → **true** (`Cano` is a surname) and `("DOS operating system", "doses")` → **true** (`dos` matches DOS the OS — the one real tech term in this set). Real-world impact is very low: `shoe`/`dose`/`canoe`/`oboe` essentially never appear as JD keywords, and `doses` as a keyword is near-impossible — but `DOS` colliding with `doses` shows the nonsense-stem class (N-7) is still alive in this branch. **Suggested fix:** when stripping `es`, also try the single-`s` strip (`base.slice(0, -1)`) as a candidate form — for `shoes` that yields the true `shoe`; for `classes` it would yield the N-7 nonsense `classe`, so gate it: only add the single-`s` form when the `es`-stripped stem ends in `o` (the `-oe` singular shape: `sho`/`cano`/`obo`) or when the stem ends in `s` and the word is not a known `-es` stem — or, more simply, push `stem + "e"` alongside `stem` when the stem ends in `o`/`s` and the result is a plausible word.
- **N-11 — MINOR: the `-es` fallback still manufactures nonsense stems `doe`/`goe` for `does`/`goes` — the o-gate refuses the stem but the else-branch strips a single `s` anyway.** `web/src/lib/resume-builder.ts:441-443` (`containsKeyword`, `else if (!/(?:ss|us)$/.test(base)) forms.push(base.slice(0, -1))`). Probes (returned **true**, expected false): `containsKeyword("doe a deer, a female deer", "does")`, `("goe to the store", "goes")`. Root cause: for `does`/`goes` the `es`-stripped stem is `do`/`go` (2 chars), which correctly fails the o-gate (so `do`/`go` are never pushed — the task-specified `("do the work","does")` and `("go developer","goes")` probes pass), but the code then falls through to the regular-plural branch and pushes `base.slice(0, -1)` = `doe`/`goe`. `doe` is a real English word, so this is a genuine false positive of the N-7 class, not just a nonsense string. Real-world impact is negligible: `does` is a stopword (`KEYWORD_STOPWORDS`, line ~361) so it can never become a JD keyword, and `goes` as a keyword is essentially impossible — a resume would have to contain the literal word `doe`/`goe` next to a JD keyword `does`/`goes`. Same fix shape as N-10: when the `es`-stem test fails for a 2-char `o` stem, push nothing instead of falling through to the single-`s` strip. **Suggested fix:** skip the single-`s` fallback when the word ends in `es` and its `es`-stripped stem is a 2-char `o` stem (`do`/`go`).
- **N-12 — MINOR (negligible, over-generation only): the new `+"es"` form is also generated for o-stems that take only `-s`, so the misspellings `photoes`/`pianoes` match.** `web/src/lib/resume-builder.ts:429-434` (`containsKeyword`, singular → plural `+"es"` branch). Probes (returned **true**, expected false): `containsKeyword("photoes are great", "photo")`, `("pianoes are great", "piano")`. Root cause: the code cannot tell `tomato` (takes `-es`) from `photo` (takes `-s`) without a wordlist, so it generates `photoes`/`pianoes` for every o-stem longer than 2 chars. My expectation here was stricter than the code can reasonably meet without a dictionary — `photoes`/`pianoes` never occur in real resumes or JDs, so there is no realistic false positive, and the real plurals are unaffected (`photo` ↔ `photos`, `piano` ↔ `pianos` all TRUE both ways). Recording it for completeness, not as a fix demand: fixing it properly would need an allowlist of `-es` o-stems (`tomato`, `potato`, `hero`, `echo`, `torpedo`, `veto`, …) vs `-s` o-stems (`photo`, `piano`, `radio`, `video`, `studio`, …).

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, tour modal, gauge, or click-through in a real browser. All behaviour above is from Vitest + code tracing.
- **No backend / no Docker on this VM:** documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end against a live backend is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.

### Recommendation

**Ship N-9 as fixed** — every probe the fix was specified against passes with my own independent checks, the full suite is green (243 tests), and `tsc` is clean. The 3 new findings are all minor: N-10 is one-way under-matching on `-e` singulars (`shoe`/`dose`/`canoe`/`oboe`) plus two live nonsense-stem matches (`Cano`, `DOS`) that essentially never occur as JD keywords; N-11 is the same N-7-class residue on `does`/`goes`, unreachable in practice because `does` is a stopword; N-12 is over-generation that only matches misspellings. None blocks shipping; file them as follow-ups, with N-10 worth fixing next since its `DOS` collision is the only one involving a real tech term, and its fix (also try the single-`s` strip for `-oe` stems) is the same code path N-11's fix touches.

## Re-test 6 (N-10, N-11)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, N-10/N-11 fixes uncommitted in the working tree (`web/src/lib/resume-builder.ts`, `containsKeyword`, lines ~409-475; the `ss`/`us` stem gate and the 2-char o-stem block are new this round)
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (47 granular one-assertion-per-`it` checks, deleted afterwards — no scratch file left, no product file modified, nothing committed, no GitHub issues filed for this run per the pipeline instruction). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks. My temp file: **33 of 47 passed; the 14 failures are all my own adversarial probes, and they split into three distinct new findings (N-13, N-14, N-15) below** — none of the author's specified probes failed. All 28 task-specified probes passed (6 N-10 + 4 N-11 + 17 regression spot-checks + the `canoes`→`canoe` trade-off confirmation).

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 245 tests, all pass** (was 243 after re-test 5; +2 author regression tests for N-10/N-11) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **33 of 47 passed** (14 failures = N-13, N-14, N-15, see below) |

### Per-bug verdicts — both FIXED

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| N-10 | **FIXED** | All 5 under-matching probes now TRUE: `("shoe store","shoes")`, `("canoe trip","canoes")`, `("oboe player","oboes")`, `("dose of medicine","doses")`, `("case study","cases")`. The live false positive is gone: `("DOS operating system","doses")` FALSE. The author's trade-off claim verified: `("canoe trip","canoes")` TRUE (the `canoe` form is generated), and the residual confirmed present — `("Cano is my guide","canoes")` still TRUE, exactly as the author documented. |
| N-11 | **FIXED** | All 4 probes FALSE as required: `("doe a deer, a female deer","does")`, `("goe to the store","goes")`, `("goes above and beyond","go")`, `("do the work","does")`. No `doe`/`goe` form is generated any more. Exact matches still work (`("goes to work","goes")` TRUE — my control). |

### Regression spot-checks (17/17 passed)

N-9: `("tomatoes in the garden","tomato")`, `("tomato soup","tomatoes")`, `("heroes of the story","hero")`, `("echoes in the hall","echo")` all TRUE. N-7: `("statu","status")`, `("clas","class")`, `("Bos and colleagues","boss")` all FALSE. N-8: `("boxes of cables","box")`, `("buses and trains","bus")` TRUE. N-5: `("CS degree from State","css")` FALSE. B-3: `("testing the app","test")`, `("JavaScript and React","java")`, `("reaction time","react")` FALSE. Plurals: `("API experience","apis")`, `("REST services","service")`, `("statuses page","status")`, `("Java class","classes")` all TRUE.

### Judgement on the documented "cano"/"tomatoe" trade-off — ACCEPTABLE, with two caveats

I agree the trade-off is acceptable. The reasoning holds: dropping the single-`s` strip for long o-stems would break the real match `canoes` → `canoe`, and the cost is matching nonsense/misspelled text (`tomatoe`) and a surname (`Cano`) that can only fire when the JD keyword is literally `canoes`/`tomatoes` — practically impossible as JD keywords. Caveats: (1) the trade-off is wider than the comment admits — it is not only `tomatoe`: my probes show `("heroe of the story","heroes")` and `("echoe in the hall","echoes")` are now **TRUE** as well, i.e. all three nonsense stems Re-test 5 had confirmed FALSE are back. The comment names only `tomatoe`; `heroe`/`echoe` are the same accepted class and should be named in the comment too. All three are misspellings that never occur in real resumes/JDs, so this does not change the verdict. (2) The `cano` residual is the only one involving a real word, and it is bounded the same way (`canoes` as a keyword). Accept and move on.

### My own probes — what I chose and what happened

I probed three angles that could break the new fix: (a) other e-word plurals (`house`/`houses`, `license`/`licenses`) — `licenses` passed but `houses` failed, which exposed N-13; (b) 2-char o-stems other than `do`/`go` (`toe`/`foe`/`woe`) — all failed, which exposed N-14, a genuine regression from Re-test 5; (c) irregular plurals (`crisis`/`crises`, `wife`/`wives`) — all failed, pre-existing limitation, recorded as N-15. One of my own expectations was also confirmed in the other direction: the reverse direction `("toes on feet","toe")` still passes (base form + `s?` covers it), proving N-14 is one-way only.

### New bugs found in the fixes

- **N-13 — MINOR: the new `ss`/`us` stem gate misfires on EVERY plural of a word ending in "-use"/"-ouse" — `houses`/`mouses`/`spouses`/`reuses`/`excuses` no longer match their singulars.** `web/src/lib/resume-builder.ts:439-443` (`containsKeyword`, `else if (/(?:ss|us)$/.test(stem))`). Probes (all returned **false**, expected true): `containsKeyword("house for sale", "houses")`, `("mouse pad", "mouses")`, `("spouse and kids", "spouses")`, `("reuse the code", "reuses")`, `("excuse the delay", "excuses")`. Root cause, traced in the code: the gate was added to distinguish a true `-es` plural (`buses` → stem `bus`, `classes` → stem `class`) from a truncated e-word (`doses` → stem `dos`, `cases` → stem `cas`). But the test `/(?:ss|us)$/` matches any stem ending in `u`+`s`, including truncated e-words whose singular ends in `-ouse`/`-use`: `houses` strips to `hous`, which ends in `us`, so the code pushes the nonsense stem `hous` and never reaches the else branch that would push the true singular `house`. The intended cases still work — `("bus stop","buses")` and `("status page","statuses")` TRUE (my controls) — so this is specifically the `us` half of the test over-matching; the `ss` half is sound (`classes` → `class` is correct, and the else branch would otherwise have pushed the N-7 nonsense `classe`). The same ambiguity the o-stem branch solved by pushing both ways was not applied here. Real-world impact is low: `house`/`mouse`/`spouse`/`reuse`/`excuse` are essentially never JD keywords (`reuse` is the most tech-plausible and still rare as a keyword), so this is under-matching only, no false positive. **Suggested fix:** in the `ss`/`us` branch, also push the single-`s` strip (`base.slice(0, -1)`) — the generated `hous`/`mous`/`spous` stems are harmless nonsense of the already-accepted `cano`/`tomatoe` class and never occur in real text — or narrow the gate so `us` only fires when the stem is a known s-word (`bus`, `cactus`, `status`, …). Note `licenses` → `license` still works (stem `licens` does not end in `us`), so the else branch is fine; only the `us` test is over-broad.
- **N-14 — MINOR (regression from Re-test 5): the blanket "push nothing for a 2-char o-stem" rule also kills real 2-char o-stem nouns — `toe`/`foe`/`woe` no longer match their plurals.** `web/src/lib/resume-builder.ts:444-457` (`containsKeyword`, `if (stem.length > 2)` guard in the o-stem branch). Probes (all returned **false**, expected true): `containsKeyword("toe injury", "toes")`, `("foe of the realm", "foes")`, `("woe is me", "woes")`. In Re-test 5, `("toe injury","toes")` was **TRUE** — the old fall-through single-`s` strip produced the correct `toe` — so this round's fix regressed it. Root cause: the fix blocks *every* 2-char o-stem to stop the verbs `do`/`go`, but `to`/`fo`/`wo` are truncated e-word nouns (`toe`/`foe`/`woe`), not verbs. The reverse direction is unaffected (`("toes on feet","toe")` TRUE via the base form's `s?`), so this is one-way under-matching. Real-world impact is negligible (`toe`/`foe`/`woe` never appear as JD keywords), but the fix is a one-line narrowing and the regression is avoidable. **Suggested fix:** push nothing only when the stem is exactly `do` or `go` (or, equivalently, only for 2-char o-stems that are verbs), and push the single-`s` strip for any other 2-char o-stem — that keeps N-11 fixed (`doe`/`goe` still never generated) while restoring `toe`/`foe`/`woe`.
- **N-15 — MINOR (limitation, not wrong behaviour, pre-existing): irregular plurals still match neither way — `crisis`/`crises` and `wife`/`wives`.** `web/src/lib/resume-builder.ts:430-470` (`containsKeyword`, `-es` strip). Probes (all returned **false**, expected true): `containsKeyword("crisis management", "crises")`, `("crises in the project", "crisis")`, `("wife and kids", "wives")`. Root cause: the `-es` strip assumes plural = stem + `es` (`crises` → stem `cris` → else branch pushes nonsense `crise`, never `crisis`; `wives` → stem `wiv` → pushes nonsense `wive`, never `wife`). This predates this round — the strip never handled `-is`→`-es` or `-f`→`-ves` — so it is not a regression, just the next uncovered irregular class after N-6/N-8/N-9/N-10. Under-matching only, no false positives. Real-world impact is very low: `crisis`/`wife` are essentially never JD keywords (and `crisis` as a keyword would more likely appear in its singular form, which matches exactly). **Suggested fix:** only if this stemming ever gets a wordlist — special-case `crises`→`crisis` and `-ves`→`-fe`/`-f` (`wives`→`wife`, `wolves`→`wolf`, `knives`→`knife`); not worth hand-rolling case by case without one.

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, tour modal, gauge, or click-through in a real browser. All behaviour above is from Vitest + code tracing.
- **No backend / no Docker on this VM:** documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end against a live backend is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.

### Recommendation

**Ship N-10 and N-11 as fixed** — every probe the fixes were specified against passes with my own independent checks, the full suite is green (245 tests), and `tsc` is clean. The 3 new findings are all minor: N-13 is a systematic but narrow under-match on `-use`/`-ouse` plurals (`houses`/`mouses`/`spouses`/`reuses`/`excuses`) caused by the new `us` stem test — the most worth fixing next, since the fix is the same both-ways push the o-stem branch already uses; N-14 is a one-line-narrowing regression on `toe`/`foe`/`woe` (block only `do`/`go`, not all 2-char o-stems); N-15 is a pre-existing irregular-plural limitation (`crisis`/`crises`, `wife`/`wives`) that needs a wordlist to fix properly and is not worth hand-rolling. None blocks shipping.

## Re-test 7 (N-13, N-14)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, N-13/N-14 fixes uncommitted in the working tree (`web/src/lib/resume-builder.ts`, `containsKeyword`, lines ~409-475; the `ss`/`us` branch now pushes both the stem and the single-`s` strip, and the 2-char o-stem block is narrowed to exactly `do`/`go`)
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (44 granular one-assertion-per-`it` checks, deleted afterwards — no scratch file left, no product file modified, nothing committed, no GitHub issues filed for this run per the pipeline instruction). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks. My temp file: **44 of 44 passed** — zero failures, no new findings. All 32 task-specified probes passed.

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 247 tests, all pass** (was 245 after re-test 6; +2 author regression tests for N-13/N-14) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **44 of 44 passed** |

### Per-bug verdicts — both FIXED

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| N-13 | **FIXED** | All 5 `-use`/`-ouse` plurals now TRUE: `("house for sale","houses")`, `("mouse pad","mouses")`, `("spouse and kids","spouses")`, `("reuse the code","reuses")`, `("excuse the delay","excuses")`. True `-es` plurals still work: `("bus stop","buses")`, `("status page","statuses")`, `("class project","classes")` all TRUE. |
| N-14 | **FIXED** | All 3 nouns now TRUE: `("toe injury","toes")`, `("foe of the realm","foes")`, `("woe is me","woes")` — `toe`/`toes` is back to its Re-test 5 behaviour, so the regression is repaired. The verb block still holds: `("doe a deer, a female deer","does")`, `("goe to the store","goes")`, `("do the work","does")`, `("goes above and beyond","go")` all FALSE. |

### Regression spot-checks (17/17 passed)

N-10: `("shoe store","shoes")` TRUE, `("dose of medicine","doses")` TRUE, `("DOS operating system","doses")` FALSE. N-9: `("tomatoes in the garden","tomato")`, `("tomato soup","tomatoes")`, `("heroes of the story","hero")` all TRUE. N-7: `("statu","status")`, `("clas","class")`, `("Bos and colleagues","boss")` all FALSE. N-8: `("boxes of cables","box")`, `("buses and trains","bus")` TRUE. N-5: `("CS degree from State","css")` FALSE. B-3: `("testing the app","test")`, `("JavaScript and React","java")`, `("reaction time","react")` FALSE. Plurals: `("API experience","apis")`, `("REST services","service")` TRUE.

### Judgement on the documented "classe"/"statuse" trade-off change — ACCEPTABLE

I agree this is acceptable, and I verified the trade-off is real rather than taking the author's word for it: my own probes confirm `("classe in session","classes")` and `("statuse update","statuses")` are now **TRUE**, i.e. the nonsense forms are genuinely generated, exactly as the updated N-7 test documents. It is the same accepted class as `tomatoe`/`heroe`/`echoe` (Re-test 6) and `cano` — forms that never occur in real resumes or JDs, and that can only fire when the JD keyword is literally `classes`/`statuses`/`houses`. The alternative (dropping the single-`s` strip for the `ss`/`us` branch) would re-break the real matches `houses` → `house` and `reuses` → `reuse` that this round fixes, so the both-ways push is the right call. Two observations, neither changing the verdict: (1) the code comment already names `classe`/`statuse` and the `hous` surname residual — the documentation is accurate this time, unlike the Re-test 6 comment which omitted `heroe`/`echoe`; (2) my probe confirms the `hous` residual is live — `("hous for sale","houses")` is TRUE — and the same branch necessarily generates `mous`/`spous`/`reus`/`excus` for the other fixed words (same code path, confirmed by reading, not individually probed). All are in the same harmless class: they only fire on JD keywords like `houses`/`mouses`, which are practically impossible.

### My own probes — what I chose and what happened

I probed three angles that could break the new fix, and **all 12 passed** — no new bug found: (a) other `-use`/`-ouse` words beyond the task's five — `abuse`/`abuses`, `loose`/`looses`, `blouse`/`blouses` all TRUE; (b) a 2-char o-stem not in the task's set — `hoe`/`hoes` TRUE via the single-`s` strip (`("hoe the garden","hoes")`), confirming the narrowing is general and not special-cased to `to`/`fo`/`wo`; (c) controls that the narrowing did not over-loosen the verb block — exact matches `("goes to work","goes")` and `("does the work","does")` still TRUE, `("bosses in charge","boss")` TRUE (ss plural reverse), `("cactus in the desert","cactuses")` TRUE (us-stem control), `("vases on the table","vase")` TRUE (e-word whose stem `vas` does not end in `ss`/`us`, so the else branch is untouched). The trade-off probes (`classe`, `statuse`, `hous`) are reported in the judgement above, not as bugs.

### New bugs found

**None.** This is the first re-test round in this chain with zero new findings from my adversarial probes.

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, tour modal, gauge, or click-through in a real browser. All behaviour above is from Vitest + code tracing.
- **No backend / no Docker on this VM:** documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end against a live backend is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.
- **N-15 not re-probed:** irregular plurals (`crisis`/`crises`, `wife`/`wives`) were accepted as a limitation in the task brief and are not being fixed; I did not re-probe them this round.

### Recommendation

**Ship N-13 and N-14 as fixed** — every probe the fixes were specified against passes with my own independent checks, all 17 regression spot-checks hold, my 12 extra adversarial probes found nothing, the full suite is green (247 tests), and `tsc` is clean. The `classe`/`statuse` trade-off change is acceptable (see judgement above). No new bugs to file. The `containsKeyword` stemming chain (B-3 → N-4 → N-5/N-6 → N-7/N-8 → N-9 → N-10/N-11 → N-13/N-14) has now had seven consecutive fix rounds, each verified clean except for the minor findings each round surfaced; with this round finding nothing new, the stemming is at a reasonable stopping point — the remaining known limitations (N-12 over-generation on `photoes`/`pianoes`, N-15 irregular plurals) both need a wordlist to fix properly and are not worth hand-rolling.

## Architect-fix re-test (P1 #1–#2, P2 #3–#5, P3 #6–#8/#10, Cosmetic #11–#16)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, iteration-2 + architect fixes uncommitted in the working tree
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (34 granular one-assertion-per-`it` checks, deleted afterwards — no scratch file left, no product file modified, nothing committed, no GitHub issues filed for this run per the pipeline instruction). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks, including different inputs and angles from the author's tests (e.g. #1 on the real backend `minimal.html` shape with no header wrapper, #4 with a title-only keyword, #6 with the `C` vs `C++`/`C#` distinction the task flagged, #3 at page level through all three entry paths). My temp file: **33 of 34 passed; the 1 failure is my own adversarial probe, a new finding (N-17) below** — none of the author's specified probes failed. Two of my checks initially failed on my own harness, not the code (a page test that clicked before the doc list loaded, and a `#8` assertion that matched two elements) — both fixed in the test and re-run green.

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 252 tests, all pass** (was 247 after re-test 7; +5 author regression tests for the architect fixes) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **33 of 34 passed** (1 failure = N-17, see below) |

### Per-fix verdicts

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| P1 #2 (contact hide) | **FIXED** | Built-in template, `hideSections: ["contact"]`: no `·  ·  ·`, no `<h1` at all, no "Alex Morgan", no contact email; summary ("Software engineer with 8 years") and Experience ("Meridian Care") kept. Backend-shaped templates: the exact `modern.html`/`classic.html` header-div shape (`<div class="header"><h1>{{fullName}}</h1><p>{{email}} \| {{phone}} \| {{linkedin}} \| {{location}}</p></div>`) → no `\|  \|  \|`, no empty `<h1`, summary kept; the `minimal.html` shape (`<h1>` + `<p class="contact">`, no header wrapper) → same, so both backend header variants are covered. Normal render (nothing hidden) still shows the name and contact line on both shapes. |
| P1 #1 (wording) | **FIXED** (wording check only, as specified — no behaviour change claimed) | The misleading sentence "what you see is what the export is built from" is gone from the `renderTemplateHtml` doc comment (grep: absent); the comment now states the opposite — "the preview is not what the export is built from: hidden sections and Designer choices are preview-only until the backend honours them" (`resume-builder.ts:879-888`). The Designer panel now says "Preview only — exports use the template's own styling and include all sections. (The backend does not store design or visibility yet.)" (`designer-panel.tsx:186-189`). Export/Save-to-profile still ignore Designer settings and hidden sections — that is the documented out-of-scope backend limitation, not re-probed as a behaviour. |
| P2 #3 (reset per document) | **FIXED, with a gap — see N-17** | Page-level, with mocked fetch: opened Doc One, set Designer font to Serif and hid Experience (Designer → Sections), went Back to All Resumes, opened Doc Two → font back to `sans`, Experience checkbox checked again. Same via New Resume → font `sans`, Experience checked. The `?documentId=` load path calls `resetPreviewSettings()` in its fetch `.then` (`page.tsx:746`) — verified by code reading, not by a page test (a fresh mount starts from defaults, so a load-path reset is vacuous to observe in jsdom; the call itself is present and in the right place). |
| P2 #4 (shared haystack) | **FIXED** | Sample state + JD "Looking for a Java developer with Spring Boot and Kafka experience.": `matchJobDescription` → score 80, 4 of 5 matched; `analyzeResume` keyword check → count 4, total 5 — identical. Title-only keyword: state with only title "Rust Engineer" vs JD "Rust developer needed" → both report 1 of 3, `rust` Matched in the skills table and absent from the Analyzer's missing list. |
| P2 #5 (stopwords) | **FIXED** | `keywordsFromJobDescription("1 year of experience, 2 days a week, senior roles, many jobs available.")` contains none of `year`/`days`/`roles`/`jobs`; `senior` survives. "Looking for a Java developer" still yields `java` and `developer`. |
| P3 #6 (c/r/node.js) | **FIXED, with a new finding — see N-16** | `keywordsFromJobDescription("C developer with R and Node.js")` yields `c`, `r`, `node.js`, `developer`. `containsKeyword("C programming", "c")` TRUE. `terraform` appears exactly once in `TECH_TERMS` (grep count 1, `resume-builder.ts:659`). |
| P3 #7 (SQL compounds) | **FIXED** | `containsKeyword("MySQL and PostgreSQL", "sql")`, `("SQLite database", "sql")`, `("NoSQL store", "sql")` all TRUE. Reverse does NOT match: `("SQL queries", "mysql")` FALSE, `("SQL Server", "mysql")` FALSE. |
| P3 #8 (Top 12 label) | **FIXED** | Component-level (`JobMatcherPanel`, mocked fetch): a 29-word JD (20 keywords after the limit) shows "Top 12 skills" and "Showing the top 12 of 20 job keywords." A ≤ 12-keyword JD shows plain "Skills" with no "Top 12" label. (Label is `Top ${match.skills.length} skills`, `job-matcher-panel.tsx:192-200` — correct here because `skills.length` is capped at the 12-item limit; it reads "Top 12" exactly when the table is truncated.) |
| P3 #10 (pinned trade-offs) | **FIXED** | `containsKeyword("tomatoe", "tomatoes")` TRUE and `("classe", "classes")` TRUE, as the updated N-9/N-7 tests document; both pass in the suite (252 tests green). |
| Cosmetic #11–#16 | **FIXED** (2 spot-checked as specified) | (a) Mode tab writes back to `?mode=`: page-level — opening a doc and clicking the Designer tab calls `router.replace` with a URL containing `mode=designer` (`page.tsx:773-782`). (b) `templateLayoutOf` returns "Two column" for a `grid-template-columns: 200px 1fr` template and `templateStyleOf` returns "Other" for an unrecognised id/name. The other four were verified by reading, not probed: doc-card key now includes the index (`` `${doc.id ?? "noid"}-${index}` ``, `resume-home.tsx:221`); skills input keeps a trailing comma via draft state (`page.tsx:552-557`); `useBuilderDocuments` takes an `enabled` option and the page passes `enabled: step === "home"` (`api.ts:685-691`, `page.tsx:686`); "terraform" duplicate removed (single occurrence, see #6). |
| Regression spot-checks (#11) | **ALL HOLD** | N-7 `("statu","status")`, `("clas","class")` FALSE; N-9 `("tomatoes in the garden","tomato")` TRUE; N-10 `("shoe store","shoes")` TRUE, `("DOS operating system","doses")` FALSE; N-13 `("house for sale","houses")`, `("reuse the code","reuses")` TRUE; N-14 `("toe injury","toes")` TRUE, `("doe a deer, a female deer","does")` FALSE; B-3 `("testing the app","test")`, `("JavaScript and React","java")` FALSE; N-5 `("CS degree from State","css")` FALSE. |

### New bugs found in the fixes

- **N-16 — MINOR: `containsKeyword("C++ programming", "c")` is TRUE (and `("C# developer", "c")` is TRUE) — the single-letter language `c` matches its sibling languages.** `web/src/lib/resume-builder.ts:442-470` (`containsKeyword`, the `(?<!\w)form(?!\w)` lookaround). The task flagged this exact case and asked me to judge it: the lookaround treats `+` and `#` as non-word characters, so they count as boundaries — `c` followed by `+` or `#` matches. That is the same false-positive class B-3 was fixed for (`java` vs `JavaScript`), reintroduced in miniature for `c`: a C++-only (or C#-only) resume "matches" a JD keyword `c`, inflating the score. The extraction side is fine (`"C developer with R and Node.js"` correctly yields `c` as a keyword; `c++`/`c#` are separate keywords). Real-world impact is narrow but real: `c` is a genuine JD keyword and C++ resumes are common, so a C++-only candidate can falsely match a C JD. **Suggested fix:** for the keyword `c` (or generally), require the character after the match not be `+` or `#` — e.g. a negative lookahead `(?![+#])` on the trailing side — while keeping `c#`/`c++` matching their own keywords exactly.
- **N-17 — MINOR: P2 #3's reset misses the "Start from template" / "Start from job description" entry path — a new blank resume inherits the previous document's design and hidden sections.** `web/src/app/resume-builder/page.tsx:819-830` (`handleBuildFresh` — no `resetPreviewSettings()` call; `handleSelectTemplate`, lines 937-968, does not reset either). Steps to reproduce: (1) Open any document, Designer → set Font to Serif, Designer → Sections → uncheck Experience. (2) Menu → Back to All Resumes. (3) Click "Start from template", pick Modern, "Use Modern". **Expected:** the blank resume opens with the default design (Sans) and all sections visible — the fix's own claim ("reset them whenever a different document is opened so one document's choices never leak into the next"). **Actual:** the new resume opens with Font = Serif (my page-level probe: `AssertionError: expected 'serif' to be 'sans'`). The hidden-Experience leak follows from the same missing call (traced in code, not separately asserted — the font assertion failed first). "Start from job description" calls the same `handleBuildFresh` (`page.tsx:872`), so it leaks identically. **Suggested fix:** call `resetPreviewSettings()` in `handleBuildFresh` (covers both entry cards) — one line, same as `handleOpenDocument`/`handleNewResume`. Note: the upload/parse path (`handleFile`, `page.tsx:793-817`) also does not reset; I did not probe it, but it is the same shape and worth checking in the same fix.

### My own probes — what I chose and what happened

I probed two angles that could break the new fixes: (a) the `C` vs `C++`/`C#` distinction the task flagged — both match, which is N-16 above; (b) the entry paths P2 #3 did *not* name — "Start from template" (and, by the same code, "Start from job description") — which exposed N-17. A third angle passed clean: the backend `minimal.html` header shape (no `<div class="header">` wrapper) hides contact correctly, so fix #2 is not limited to the header-div shape.

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, Designer panel copy, gauge, or click-through in a real browser. All UI behaviour above is from jsdom tests + code tracing.
- **No backend / no Docker on this VM:** the documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end (including the actual export ignoring Designer settings — the P1 #1 backend limitation) is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.
- **?documentId= reset verified by code reading only** (see P2 #3 row): the call is present in the load path, but a fresh jsdom mount cannot observe a reset from a prior document's settings on that path.

### Recommendation

**Ship the architect fixes as fixed, except N-17 should be fixed first if the reset is meant to be complete.** All 11 architect items verify with my own independent probes, the full suite is green (252 tests), and `tsc` is clean. The 2 new findings are both minor: N-16 is a narrow B-3-class false positive confined to keyword `c` vs `C++`/`C#`; N-17 is a real leak on one entry path ("Start from template" / "Start from job description") with a one-line fix, and it directly contradicts the fix's stated per-document reset claim — worth one more round rather than shipping with a known hole in the feature just fixed. The remaining known limitations (N-12 over-generation on `photoes`/`pianoes`, N-15 irregular plurals) still need a wordlist to fix properly and are not worth hand-rolling.

## Re-test 8 (N-16, N-17)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, N-16/N-17 fixes uncommitted in the working tree (`web/src/lib/resume-builder.ts`, `containsKeyword`, the `(?![+#])` guard on form `"c"`, lines ~518-524; `web/src/app/resume-builder/page.tsx`, `resetPreviewSettings()` now called in `handleBuildFresh` line 828 and `handleFile` line 796, deliberately NOT in `handleSelectTemplate` lines 936-968)
- **Method:** re-ran the full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (30 granular one-assertion-per-`it` checks, deleted afterwards — no scratch file left, no product file modified, nothing committed, no GitHub issues filed for this run per the pipeline instruction). The author's new regression tests were **not** trusted on their own: every fix was re-probed with my own checks, including page-level probes of all three N-17 entry paths with mocked fetch. My temp file: **27 of 30 passed; the 3 failures are all my own adversarial probes in two new findings (N-18, N-19) below** — none of the author's specified probes failed.

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 254 tests, all pass** (was 252 after the architect-fix round; +2 author regression tests for N-16/N-17) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **27 of 30 passed** (3 failures = N-18, N-19, see below) |

### Per-bug verdicts — both FIXED

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| N-16 | **FIXED** | All 7 task probes pass: `("C++ programming","c")` FALSE, `("C# developer","c")` FALSE, `("C programming","c")` TRUE, `("C++ developer","c++")` TRUE, `("C# developer","c#")` TRUE, `("Rust programming","r")` FALSE, `("R programming","r")` TRUE. The guard is exactly as described: `form === "c"` adds `(?![+#])` before the `s?` suffix (`resume-builder.ts:518-524`), so `c#`/`c++` still match as exact keywords via their own forms. Control probes pass: `("C# and C++ developer","c")` FALSE (a haystack containing only the siblings still does not match `c`); `("C++","c++")` TRUE; `("C/C++ developer","c")` TRUE — the `C` in `C/C++` is genuinely the C language, so that match is correct. |
| N-17 | **FIXED** | Page-level, with mocked fetch, all three entry paths reset: (a) "Start from template" — customised doc 1 (Serif + hide Experience), Back to Resumes, Start from template → Modern → new document opens with Font `sans` and Experience checked (the exact failing probe from the architect re-test, now green). (b) "Start from job description" — same customisation, then Start from job description → paste JD → "Use this description" → Modern → Font `sans`, Experience checked. (c) Upload/parse — same customisation, then Import → `cv.pdf` upload → Classic → Font `sans`, Experience checked. All three go through the two new `resetPreviewSettings()` calls (`handleBuildFresh` covers (a) and (b) via `handleConfirmJd`; `handleFile` covers (c)). |

### Judgement on the handleSelectTemplate reasoning — CORRECT, verified at page level

The author's reasoning holds, and I verified it by probing rather than taking the comment's word for it. In the editor on existing doc 1, I customised Serif + hid Experience, then used the top-bar "Change template" → Classic → "Use Classic": the document kept Font `serif` and Experience still hidden — the switch did **not** reset the design, exactly as intended. The new-document path through `handleSelectTemplate` is already covered: every route that opens the template library for a *new* document goes through `handleBuildFresh` first (`resume-home.tsx:104` → `page.tsx:1087`; `handleConfirmJd` → `handleBuildFresh`, `page.tsx:874`), which resets before the modal ever appears, and the upload path resets in `handleFile` before the modal. `handleSelectTemplate` itself (`page.tsx:936-968`) contains no reset call, confirmed by reading. One caveat, not a hole: the correctness depends on that routing invariant — if a future entry path ever opens the template library for a new document without going through `handleBuildFresh`/`handleFile`, the leak returns. The invariant is currently true for all three entry paths (verified above), but it is implicit; a comment on `handleSelectTemplate` noting "do not reset here — new-document callers reset first" would make it harder to break accidentally.

### Regression spot-checks (13/13 passed)

N-7: `("statu","status")`, `("clas","class")` FALSE. N-9: `("tomatoes in the garden","tomato")` TRUE. N-10: `("shoe store","shoes")` TRUE, `("DOS operating system","doses")` FALSE. N-13: `("house for sale","houses")` TRUE. N-14: `("toe injury","toes")` TRUE, `("doe a deer, a female deer","does")` FALSE. B-3: `("testing the app","test")`, `("JavaScript and React","java")` FALSE. N-5: `("CS degree from State","css")` FALSE. P3 #7: `("MySQL and PostgreSQL","sql")` TRUE, `("SQL queries","mysql")` FALSE.

### New bugs found in the fixes

- **N-18 — MINOR: the `s?` plural suffix on the single-letter keyword `c` lets "CS" (Computer Science) match `c`.** `web/src/lib/resume-builder.ts:518-524` (`containsKeyword`, the `s?` appended to every form). Probe: `containsKeyword("CS degree from State", "c")` → **true** (expected false). Root cause: the N-16 fix guarded the character *after* `c` against `+`/`#`, but the regex still appends the N-4 plural `s?` to the 1-char form — so `c` + `s?` matches the token `CS`. The guard passes because the char after `c` is `s`, not `+`/`#`. This is a residual of the same B-3 false-positive class, not a regression from this round (the `s?` predates the fix) — but it is arguably the most realistic collision in this chain: resumes say "B.S. in CS" / "CS degree" constantly, and `c` is a genuine JD keyword, so a CS-degree resume with no C at all falsely matches a C JD. Note the `css` case is correctly false (`("CS degree from State","css")` FALSE — N-5 still holds), so this is specifically the 1-char keyword + plural-suffix interaction. **Suggested fix:** do not append `s?` when the form is a single character (a 1-letter "plural" is never a real plural — `c` → `cs` is the acronym CS, not plural C), or gate the `s?` to forms longer than 1 char.
- **N-19 — MINOR: the same lookaround-boundary hole as N-16, for keyword `r` — "R&D" matches `r`.** `web/src/lib/resume-builder.ts:518-524` (`containsKeyword`, the guard is `c`-only). Probe: `containsKeyword("R&D engineer", "r")` → **true** (expected false). Root cause: `&` is a non-word character, so `(?!\w)` passes after the `R` in `R&D`, and the N-16 guard only covers `c` + `+/#` — it was never generalised. "R&D engineer" / "R&D team" are common resume phrases, and `r` is a plausible JD keyword, so this is the same false-positive class as N-16 with a realistic trigger. The task's own `("Rust programming","r")` FALSE probe passes, so this is specifically the `&` boundary, not the general `r` handling. **Suggested fix:** generalise the guard — for the 1-char form `r`, add a trailing negative lookahead `(?![&])` (and audit other 1-char keywords the same way), or for single-letter forms require the following character to be whitespace/end-of-string/punctuation that cannot be part of a compound abbreviation. Observation, checked and NOT a bug: `("Objective-C developer","c")` is also TRUE (the `-` counts as a boundary) — but Objective-C is a strict superset of C, so a resume with Objective-C does know C, and that match is defensible, unlike `java` vs `JavaScript`. Left as-is deliberately.

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, Designer panel, gauge, or click-through in a real browser. All UI behaviour above is from jsdom tests + code tracing.
- **No backend / no Docker on this VM:** documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end against a live backend is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.
- **?documentId= reset verified by code reading only** (as in the architect re-test): the call is present in the load path (`page.tsx:747`), but a fresh jsdom mount cannot observe a reset from a prior document's settings on that path.

### Recommendation

**Ship N-16 and N-17 as fixed** — every probe the fixes were specified against passes with my own independent checks, including page-level probes of all three N-17 entry paths and a page-level confirmation that the editor template-switch correctly preserves design, the full suite is green (254 tests), and `tsc` is clean. The 2 new findings are both minor and both the same single-letter-keyword boundary class as N-16: N-18 (`CS` → `c` via the plural `s?`) is the more realistic one and worth fixing next since "B.S. in CS" appears in a large share of real resumes; N-19 (`R&D` → `r` via the `&` boundary) is the direct generalisation the N-16 fix did not make. Neither blocks shipping. The `containsKeyword` stemming chain has now had eight consecutive fix rounds; with this round finding only two narrow single-letter boundary residuals, the matcher is at a reasonable stopping point after those — the remaining known limitations (N-12 over-generation on `photoes`/`pianoes`, N-15 irregular plurals) still need a wordlist to fix properly and are not worth hand-rolling.

## Re-test 9 (N-18, N-19)

- **Tester:** Quester (chief QA)
- **Branch / head:** `kotlin-migration` @ `3b9b56c`, N-18/N-19 fixes uncommitted in the working tree (`web/src/lib/resume-builder.ts`, `containsKeyword`: `SINGLE_LETTER_GUARD` map `c → (?![+#])`, `r → (?![&])` at lines 447-450, and the plural now gated to forms longer than 1 char at line 520; `web/src/app/resume-builder/page.tsx`, hardening comment on `handleSelectTemplate` at lines 938-943)
- **Method:** full web suite + `tsc --noEmit`, plus a fresh temporary adversarial Vitest file (34 granular one-assertion-per-`it` checks, deleted afterwards — no scratch file left, no product file modified, nothing committed, no GitHub issues filed for this run per the pipeline instruction). The author's new regression test (`"single-letter keywords do not match CS or R&D (N-18, N-19)"`, `resume-builder.test.tsx:1594`) was **not** trusted on its own: every fix was re-probed with my own checks. My temp file: **32 of 34 passed; the 2 failures are both my own adversarial probes in two new findings (N-20, N-21) below** — none of the author's specified probes failed.

### Baseline checks (all green)

| Check | Result |
|---|---|
| `npx vitest run` (web/, temp file deleted) | **15 files, 255 tests, all pass** (was 254 after re-test 8; +1 author regression test for N-18/N-19) |
| `npx tsc --noEmit` | clean (exit 0) |
| Temp adversarial file (while present) | **32 of 34 passed** (2 failures = N-20, N-21, see below) |

### Per-bug verdicts — both FIXED

| # | Verdict | Evidence (my own checks, not the author suite) |
|---|---|---|
| N-18 | **FIXED** | All 4 task probes pass: `("CS degree from State","c")` FALSE, `("B.S. in CS","c")` FALSE, `("C programming","c")` TRUE, `("C/C++ developer","c")` TRUE. The fix is exactly as described: `const plural = form.length > 1 ? "s?" : ""` (`resume-builder.ts:520`), so the 1-char form `c` no longer gains the `s?` that matched the token `CS`. Control probes pass: `("C++ and C# developer","c")` FALSE (siblings-only haystack still does not match `c`); `("C/C++ developer","c++")` TRUE (the C++ half of the compound still matches its own keyword). |
| N-19 | **FIXED** | All 3 task probes pass: `("R&D engineer","r")` FALSE, `("R&D team","r")` FALSE, `("R programming","r")` TRUE. The guard is now the `SINGLE_LETTER_GUARD` map (`resume-builder.ts:447-450`): `c → (?![+#])`, `r → (?![&])`, applied per form at line 516. |
| N-16 (still holds) | **HOLDS** | All 5 probes pass: `("C++ programming","c")` FALSE, `("C# developer","c")` FALSE, `("C++ developer","c++")` TRUE, `("C# developer","c#")` TRUE, `("Rust programming","r")` FALSE. |
| handleSelectTemplate comment | **PRESENT** | The hardening comment is in place (`page.tsx:938-943`): "Do NOT reset the preview settings here: when called from the editor to switch templates on an existing document, the user's design customisations must survive. The new-document path is covered because every route that opens the library for a new document goes through handleBuildFresh / handleFile first, and those reset." The N-17 routing invariant is unchanged from re-test 8 (all three new-document entry paths reset; editor template-switch preserves design — verified at page level in re-test 8, not re-probed here since neither path was touched by this round's fix). |

### Regression spot-checks (19/19 passed)

N-7: `("statu","status")`, `("clas","class")` FALSE. N-9: `("tomatoes in the garden","tomato")` TRUE. N-10: `("shoe store","shoes")` TRUE, `("DOS operating system","doses")` FALSE. N-13: `("house for sale","houses")` TRUE, `("reuse the code","reuses")` TRUE. N-14: `("toe injury","toes")` TRUE, `("doe a deer, a female deer","does")` FALSE. B-3: `("testing the app","test")`, `("JavaScript and React","java")` FALSE. N-5: `("CS degree from State","css")` FALSE. Plurals: `("API experience","apis")` TRUE, `("REST services","service")` TRUE. P3 #7: `("MySQL and PostgreSQL","sql")` TRUE, `("SQL queries","mysql")` FALSE.

### New bugs found in the fixes

- **N-20 — MINOR: the same `&`-boundary hole as N-19, for the unguarded single-letter keyword `d` — "D&D" matches `d`.** `web/src/lib/resume-builder.ts:447-450` (`SINGLE_LETTER_GUARD` covers only `c` and `r`). Probe: `containsKeyword("D&D group", "d")` → **true** (expected false). Root cause: `&` is a non-word character, so `(?!\w)` passes after the `D` in `D&D`, and the guard map has no entry for `d` — the N-19 fix generalised the guard's *mechanism* (a map) but not its *coverage*. Control: `("D programming","d")` is TRUE, so plain `d` still works; the hole is specifically the `&` compound. Real-world impact is narrower than N-19 (`d` as a JD keyword means the D language, rarer than `r`; "D&D" is a common phrase but less common on resumes than "R&D"), but it is the identical false-positive class and proves the map is incomplete by construction — every future single-letter keyword needs a manual guard entry. **Suggested fix:** audit all single-letter keywords the extractor can produce (`c`, `r`, `d`, and any others — e.g. `go` is 2-char and unaffected) and add a guard for each, or for single-letter forms require the following character to be whitespace/end-of-string/punctuation that cannot be part of a compound abbreviation.
- **N-21 — MINOR: the `s?` plural suffix on the 2-char keyword `cs` lets "CSS" (stylesheets) match `cs` (computer science).** `web/src/lib/resume-builder.ts:520` (`const plural = form.length > 1 ? "s?" : ""`). Probe: `containsKeyword("CSS developer", "cs")` → **true** (expected false). Root cause: the N-18 fix gated `s?` to forms longer than 1 char, but 2-char forms still take it — `cs` + `s?` matches the token `CSS`. This is the same plural-suffix collision class as N-18, one step up the length ladder: `cs` is not a plural of anything, and `CSS` is one of the most common tokens on real resumes, while `cs` is a plausible JD keyword ("CS degree required"). Note the `css` keyword itself is correctly handled (`("CS degree from State","css")` FALSE — N-5 still holds), so this is specifically the 2-char-keyword + plural-suffix interaction. **Suggested fix:** do not append `s?` to forms that already end in `s` (a form ending in `s` is either already plural or an acronym like `cs` — appending another `s` can only create a different real word), or gate the plural to forms that do not end in `s`.

### My own probes — what I chose and what happened

I probed three angles that could break the new fixes: (a) the `C/C++` compound vs `c++` — clean, both halves match their own keyword; (b) other single-letter keywords beyond the guarded `c`/`r` — exposed N-20 (`D&D` → `d`); (c) the plural suffix on a 2-char form ending in `s` — exposed N-21 (`CSS` → `cs`). The `("D programming","d")` TRUE control confirms plain single-letter matching still works for unguarded letters; only the compound-boundary case is broken.

### What I could NOT verify (not passes)

- **No live browser:** no visual check of the editor, preview, Designer panel, gauge, or click-through in a real browser. All behaviour above is from jsdom tests + code reading.
- **No backend / no Docker on this VM:** the documents list, parse, create/update, export, save-to-profile, Co-Pilot, and jobs-search endpoints were exercised only against mocked `fetch` in tests. Real end-to-end is **unverified**.
- **No `mvn verify`:** backend untouched and not runnable here.
- **N-17 entry paths not re-probed this round** (see the handleSelectTemplate row): neither `handleBuildFresh`/`handleFile` nor `handleSelectTemplate` was touched by the N-18/N-19 fix, so the re-test 8 page-level evidence stands; a future round that touches those paths should re-probe them.

### Recommendation

**Ship N-18 and N-19 as fixed** — every probe the fixes were specified against passes with my own independent checks, the full suite is green (255 tests), and `tsc` is clean. The 2 new findings are both minor and both the same two false-positive classes as N-16/N-18/N-19, now at the tail of the chain: N-20 (`D&D` → `d`) shows the guard map is incomplete by construction, and N-21 (`CSS` → `cs`) shows the plural suffix still collides on 2-char forms ending in `s`. Neither blocks shipping — `d` is a rare JD keyword and `cs` is a less common JD keyword than `c` — but both are cheap one-line-class fixes (a guard entry for `d`; suppress `s?` on forms already ending in `s`) and worth one final round, since the matcher has now had nine consecutive fix rounds and these are the last two members of the same class I can find by probing. After that, the matcher is at a reasonable stopping point; the remaining known limitations (N-12 over-generation on `photoes`/`pianoes`, N-15 irregular plurals) still need a wordlist to fix properly and are not worth hand-rolling.
