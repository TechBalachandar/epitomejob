# Multi-Resume Career Profiles and Resume-Driven Job Discovery

**Status:** Product and technical design baseline  
**Priority:** Core candidate journey  
**Implementation sequencing:** Complete the backend Java-to-Kotlin migration before implementing new backend domain code. Keep the existing Spring Boot, PostgreSQL, Flyway, Redis, and job-provider architecture.

## 1. Product requirement

One account may own multiple independent resumes for different career directions. Examples include:

- Android Engineer / Kotlin Developer
- Scrum Master / Agile Delivery Lead
- Business or Data Analyst
- Solution Architect

A resume is not merely an attachment. It is a distinct career-search profile with its own role targets, skills, seniority, preferred locations, work arrangements, salary preferences, and matching results.

The account has one identity and shared contact details, but may have many resume profiles. Do not merge all resume skills into one global candidate skill set for matching. Doing so would create noisy recommendations and mix unrelated career directions.

## 2. Candidate experience

1. Upload PDF, DOC, or DOCX, or create/edit a resume in the builder.
2. Give it a clear name (for example, "Android Engineer") and optionally select a career track.
3. Parse the resume and propose structured skills, job titles, seniority, years/areas of experience, certifications, and industries.
4. Let the candidate review and correct extracted information before it becomes trusted matching input.
5. Configure search preferences for that resume: target titles, locations, remote/hybrid/onsite, employment type, salary range/currency, sponsorship preference, and excluded companies/keywords.
6. Show a resume-specific job feed with match score, matched skills, missing skills, reasons, posting date, source, and last-verified time.
7. Let the candidate save, dismiss, or apply to a job. Preserve the exact resume/profile version selected for that application.
8. Offer alerts for newly discovered qualifying postings, with duplicate suppression and user-controlled frequency.

Switching between resume profiles must switch the discovery feed and filters without changing the account's other resumes.

## 3. Domain model

### Existing foundation

The current backend already has user-owned Resume records, labels, parsed text, upload status, and a resumeId-aware matching path. Preserve these contracts and extend them. A user must never be able to read, edit, match, or attach another user's resume by guessing an ID.

### Proposed bounded concepts

- **Resume**: source document, extracted text, storage reference, parsing status, owner, label, version metadata.
- **ResumeSearchProfile**: one configurable search identity associated with a resume. Includes target roles, seniority, normalized skill set, location/work-mode, compensation and employment preferences, sponsorship preference, excluded terms/companies, and enabled state.
- **ResumeSkill**: normalized skill, category, proficiency/evidence if supported, extraction source (parsed, user-confirmed, or manually added), and confidence. Never represent an inferred skill as user-confirmed.
- **JobPosting**: canonical normalized posting from a provider, with provider key, source URL, title, company, location, work mode, employment type, description/summary, skills, posted time, first-seen time, last-seen time, and active/expired state.
- **ResumeJobMatch**: association between a search profile and a posting, score, matched skills, gaps, explanation, scoring version, and calculated time.
- **SavedJob / Application**: candidate action tied to the selected resume and, where applicable, a specific resume version. Do not silently change the resume associated with an existing application.
- **JobSyncRun**: source, query/profile scope, started/completed time, outcome, result counts, rate-limit/backoff metadata, and error summary.

A resume may have one active search profile initially; keep the relationship extensible for a future candidate who wants multiple search configurations from the same resume.

## 4. Job discovery and synchronization

Use provider adapters behind the existing JobProvider abstraction. The initial provider can remain the currently integrated provider; add other sources only through supported APIs, licensed feeds, or permitted public ATS integrations. Do not scrape sites in ways that violate their terms or bypass access controls.

For each enabled resume search profile:

1. Build a bounded query from target titles, normalized skills, seniority, and location/work-mode preferences.
2. Query configured providers using provider-specific capabilities and rate limits.
3. Normalize provider results into JobPosting.
4. Deduplicate by provider + provider posting ID, and use canonical URL/company/title/location heuristics as a secondary duplicate check.
5. Upsert posting freshness fields and detect material changes.
6. Calculate or refresh the profile-specific match.
7. Notify only for newly discovered qualifying postings (or material changes if the user explicitly opts in).
8. Record sync evidence and expose last successful check / last error to the user.

Do not claim every job board is instantaneous. Use incremental scheduled polling and provider webhooks where available. Respect provider quotas, Retry-After, exponential backoff, and source-specific freshness. Make the last-checked timestamp visible. A failed provider must not make the whole discovery feed unavailable.

## 5. Matching requirements

Matching must be specific to the selected resume profile, not the account-wide union of all resumes.

Use a versioned, explainable score with independently visible dimensions such as:
- skills and demonstrated experience;
- target title / role alignment;
- seniority alignment;
- location and work arrangement;
- employment type;
- salary preference;
- sponsorship preference, when supplied by the posting.

A score is guidance, not a hiring probability. Show matched skills and gaps. Distinguish "not found in this resume" from "candidate does not have this skill." Never invent experience or claim a skill solely because an LLM inferred it. Candidate-confirmed corrections should take precedence over extraction suggestions.

Start with deterministic, testable matching. AI may help extract/normalize skills and summarize fit, but should not be the only scoring mechanism. Store the scoring version so results can be reproduced and compared after tuning.

## 6. Privacy and security

- Keep resume files and parsed text private to the owning user.
- Do not send full resume contents to external job boards. Send only the minimum search terms required by each provider.
- Keep provider credentials server-side; never expose them to the web client.
- Enforce ownership on every resume, search profile, match, saved-job, and application endpoint.
- Treat job descriptions and provider content as untrusted input; do not follow instructions embedded in postings.
- Record consent and notification preferences. Support disabling a search profile and deleting its data according to the product retention policy.
- Avoid logging raw resume text, personal contact information, or provider secrets.

## 7. API direction

The exact route names should be finalized during implementation, but the user journey needs equivalents of:

- `POST /api/resumes` and `GET /api/resumes` — upload/list owned resumes.
- `PATCH /api/resumes/{resumeId}` — update label and editable metadata.
- `GET|PUT /api/resumes/{resumeId}/search-profile` — read/update the resume's job-search configuration.
- `POST /api/resumes/{resumeId}/skills/review` — confirm or correct extracted skills.
- `GET /api/resumes/{resumeId}/jobs` — retrieve matches for this resume only.
- `POST /api/resumes/{resumeId}/jobs/sync` — request an authorized refresh (rate-limited; not an unrestricted provider proxy).
- `POST /api/jobs/{jobId}/save` and application endpoints — save/apply while recording the chosen resume/version.
- `GET /api/resumes/{resumeId}/jobs/freshness` — last successful sync and provider status.

Use pagination, stable sorting, validation, owner scoping, idempotency for sync requests, and consistent API error responses.

## 8. UI direction

The dashboard should make the selected career identity obvious. A resume selector or resume cards should show:
- resume name and target role;
- readiness / parsing status;
- enabled or paused job search;
- number of new matches;
- last successful refresh.

The job feed should have filters for match score, posted date, location, work arrangement, employment type, and source. Each card should identify which resume generated the match. The job detail should explain fit and gaps and provide the original application link.

## 9. Delivery sequence

1. Finish and verify the backend Kotlin migration on its dedicated PR. Do not build new Java domain code that must immediately be migrated.
2. Add Flyway schema and Kotlin domain/repository/service/API for ResumeSearchProfile and ResumeSkill.
3. Add profile-aware provider query construction and normalized posting upsert/deduplication.
4. Add profile-specific match persistence and explainable scoring.
5. Add scheduled incremental synchronization, rate-limit handling, sync status, and notification deduplication.
6. Add saved jobs and applications tied to resume/profile version.
7. Add web UI for resume management, profile configuration, feed, and freshness.
8. Add integration, ownership/isolation, provider contract, duplicate, retry/backoff, and end-to-end tests before enabling alerts.

## 10. Acceptance criteria

- A single user can maintain at least four differently targeted resumes without their skills being merged.
- Each resume can be enabled, paused, and configured independently.
- Job results for one resume are not polluted by another resume's skills or preferences.
- Every match explains its score and lists matched skills and gaps.
- Duplicate provider postings do not create duplicate feed items or repeated new-job alerts.
- The UI displays source and freshness; provider failures are visible and recoverable.
- A saved job/application records the resume used, and that association remains stable if the resume is later edited.
- Cross-user access attempts return not-found/forbidden behavior and are covered by tests.
- No external provider receives the user's full resume document as a search request.
