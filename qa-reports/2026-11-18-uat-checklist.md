# UAT Signoff Checklist — 7 Wow-Factor Features + Resume Builder Iteration 2

- **Build under test:** branch `kotlin-migration` (bundle head — see MORNING-FIX-README.md), deployed on the Mac via `docker compose build web backend && docker compose up -d web backend`
- **UAT approver:** Balachandar Jeldi (he is the approver; nobody signs off on his behalf)
- **How to use:** run each row in the browser on the Mac against the live app. Mark **Pass** or **Fail** per row and add a note for anything unexpected. An item moves to the `prod` branch only after its rows are marked Pass here.
- **Preconditions:** logged in as the dev user; Adzuna env vars present in the backend container (count only, never print values); Ollama running on the Mac (needed for F4 and F5 live generation).

## F1 — 60-Day Clock + Daily Plan (`/clock`)

| # | Flow | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| F1.1 | Clock unset | Open `/clock` with no layoff date set | Setup prompt only (date input + save). No countdown, no fake numbers. | | |
| F1.2 | Set the date | Enter a layoff date (e.g. 30 days ago) and save | Countdown shows 30 days left; deadline date shown; state Active. Profile keeps the date after reload. | | |
| F1.3 | Urgent + expired | (Use a test date if needed) 14 days left → Urgent badge; past 60 days → Expired state | Badges match the state; plan still renders (or states the clock expired). | | |
| F1.4 | Daily plan is real | With sponsor jobs + builder resumes present, read today's plan | Jobs listed are real sponsor jobs from the feed; resumes listed are your actual documents; no filler rows when data is empty. | | |
| F1.5 | Dashboard + nav | Open the dashboard and the top nav | Clock summary shows days left with a link to `/clock`; nav has a Clock entry. | | |

## F2 — One-Click Tailor + Apply (Jobs page)

| # | Flow | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| F2.1 | Tailor | On a job, click "Tailor + Apply", pick a builder resume | Co-Pilot returns tailored options per section; you pick one per section. | | |
| F2.2 | Diff is real | Read the diff view | Added/removed/unchanged lines reflect the actual original vs tailored text. | | |
| F2.3 | Approve → apply | Confirm; then approve in the Approvals inbox | Nothing is sent until you approve. After approval the application is sent. | | |
| F2.4 | Tracker flips on approval | After approving, open the Tracker | The job's tracker item is APPLIED (not before approval). | | |
| F2.5 | Co-Pilot failure | (If a section fails to tailor) | That section is skipped with a note; original text kept; no fake content. | | |

## F3 — ATS Parse Preview (Resumes page)

| # | Flow | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| F3.1 | Parsed text | Open a READY resume, expand "How an ATS reads this" | The raw parsed text renders verbatim, exactly as the parser produced it. | | |
| F3.2 | Flags are honest | Check the flag list on a resume with missing contact info or an empty section | Flags name only observable evidence (missing email/phone, empty/absent section). No scores, no invented ATS rules. | | |
| F3.3 | Not-ready resume | Open a resume whose text is not ready yet | A clear "not ready" message (no empty card, no crash). | | |

## F4 — Interview Prep (`/interviews`)

| # | Flow | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| F4.1 | Pick a job | Pick a job from the feed, generate prep | 5 role questions appear, then model answers. | | |
| F4.2 | Answers are grounded | Read the model answers | Every answer cites your actual experience (roles, companies, skills). No invented roles/companies. Gaps are flagged, not filled. | | |
| F4.3 | Paste a JD | Paste a job description, generate prep | Same behaviour as F4.1, from the pasted text. Short pastes (< 40 chars) are rejected with a message. | | |
| F4.4 | Coach down | Stop Ollama, generate prep | An honest error (Coach unreachable, needs Ollama on the Mac) — no fake answers. | | |

## F5 — Cover Letter Generator (`/cover-letter`)

| # | Flow | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| F5.1 | Home card | Open the Resume Builder home | A 4th action card "New Cover Letter" is present and opens the generator. | | |
| F5.2 | Generate | Pick a builder resume, paste a JD, generate | 3–5 full-letter drafts; pick one; edit it inline. | | |
| F5.3 | Save | Save the letter, reload the page, reopen it | The letter is there (saved on this device — the UI says so). | | |
| F5.4 | Export PDF | Export the letter as PDF | The PDF contains the letter with paragraph breaks intact (each paragraph separate). **This is the B1 live proof — the one verification that still genuinely matters.** | | |
| F5.5 | Export DOCX | Export the letter as DOCX | The DOCX contains the letter with paragraph breaks intact (each paragraph separate, no collapsed text). | | |

## F6 — Sponsor-Job Alerts (Clock page)

| # | Flow | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| F6.1 | Alerts on load | Open `/clock` with matching sponsor jobs in the feed | Alerts appear without clicking anything. Non-matching / non-sponsor jobs stay silent. | | |
| F6.2 | Rule is explicit | Read the rule text on the page | It states the exact rule (sponsors H-1B + profile match + unseen) and the page behaves that way. | | |
| F6.3 | Dismiss sticks | Dismiss an alert, then click "Check for new sponsor jobs" (and reload) | The dismissed alert does not come back. | | |
| F6.4 | Alert links | Click an alert | It opens the real job posting (or the jobs page when no posting URL exists). | | |

## F7 — Offer Comparison (`/offers`)

| # | Flow | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| F7.1 | Manual entry | Enter two offers (salary, visa, location, benefits) | Side-by-side table; "Best" only where numbers are unambiguous (higher money, visa = yes). Location / work type / benefits never name a winner. | | |
| F7.2 | From Tracker | With 2+ Tracker items in OFFER stage, use "Compare offers" | The offers are pre-filled from the tracker items; figures you enter are editable inline. | | |
| F7.3 | No fake market data | Read the whole page | No market salary range is shown (only a posting's own listed range, labelled as the listing). | | |
| F7.4 | Salary-less job | Compare an offer from a job with no listed salary | Shows "Not listed" — never a fabricated $0k range. | | |
| F7.5 | Bad input | Type `abc` / `$` / empty into an inline money field | No `$NaN` and no `$0` for input you never entered; the field clears / stays empty. | | |

## Regression — Resume Builder iteration 2

| # | Flow | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| R.1 | Home | Open the Resume Builder | 4 action cards (incl. New Cover Letter), Recent Resumes with search + sort. | | |
| R.2 | Quick Tour | First visit (clear the tour-seen flag to re-test) | Welcome modal appears once; Start Quick Tour walks the editor; Skip dismisses and never returns. | | |
| R.3 | Job Matcher | Paste a JD with AWS/SQL/Git terms | The terms appear as keywords; a JavaScript-only resume does NOT match the keyword `java`. | | |
| R.4 | Section hide | Uncheck a section in Content mode | Both the heading AND the content disappear from the live preview. | | |
| R.5 | Designer | Change Location Alignment + Skills Layout | The live preview updates (left/right dates; comma / list / columns skills). | | |
| R.6 | Menu | Open the menu | Import, Duplicate (disabled with no content), Quick Tour, Back to All Resumes — each works; no dead item. | | |
| R.7 | Jobs + Approvals (regression) | Open Jobs (with Adzuna down or vars missing) and Approvals | Jobs shows seeded jobs (no blank page); expired approvals are muted with no buttons. | | |

## Ops — Nightly build timing (not browser UAT)

| # | Check | Steps | Expected result | Pass/Fail | Notes |
|---|---|---|---|---|---|
| O.1 | Nightly runs at night | After the next nightly run, check the Actions tab | `nightly.yml` ran ~11 PM ET (04:00 UTC) and `quester-perf.yml` ~11:30 PM ET (04:30 UTC), both finished before 5 AM ET, so the 7 AM report has fresh results. | | |

## Signoff

| Item | UAT result | Signed off by | Date |
|---|---|---|---|
| F1 — 60-Day Clock + Daily Plan | | Balachandar Jeldi | |
| F2 — One-Click Tailor + Apply | | Balachandar Jeldi | |
| F3 — ATS Parse Preview | | Balachandar Jeldi | |
| F4 — Interview Prep | | Balachandar Jeldi | |
| F5 — Cover Letter Generator | | Balachandar Jeldi | |
| F6 — Sponsor-Job Alerts | | Balachandar Jeldi | |
| F7 — Offer Comparison | | Balachandar Jeldi | |
| R — Builder iteration 2 regression | | Balachandar Jeldi | |

**Prod branch rule:** an item moves to the `prod` branch only after its rows above are marked Pass. Nothing is in prod until the user confirms the deploy.
