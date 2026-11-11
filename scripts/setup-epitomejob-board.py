#!/usr/bin/env python3
"""EpitomeJob board setup — run ONCE from your Mac. No Homebrew, no gh CLI.

Creates labels, milestones, 7 issues and the GitHub Projects board for
TechBalachandar/epitomejob, using the GitHub API with your Personal Access
Token (the same token you used for git clone). Python 3 ships with macOS,
so nothing needs installing.

Run:  python3 scripts/setup-epitomejob-board.py
It asks for your token (typing is hidden). The token is used only for
these API calls and is never printed, saved, or sent anywhere else.

If the Projects board step fails (it needs the `project` scope on the
token, separate from `repo`), labels/milestones/issues are still created
and the script tells you the 2-minute UI fallback.
"""
import getpass
import json
import sys
import urllib.error
import urllib.request

REPO = "TechBalachandar/epitomejob"
API = "https://api.github.com"
GRAPHQL = "https://api.github.com/graphql"


class GitHub:
    def __init__(self, token):
        self.token = token

    def call(self, method, path, data=None, tolerate=()):
        url = API + path
        body = json.dumps(data).encode() if data is not None else None
        req = urllib.request.Request(url, data=body, method=method)
        req.add_header("Authorization", "token " + self.token)
        req.add_header("Accept", "application/vnd.github+json")
        req.add_header("X-GitHub-Api-Version", "2022-11-28")
        if body is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req) as resp:
                return resp.status, json.loads(resp.read() or b"{}")
        except urllib.error.HTTPError as e:
            if e.code in tolerate:
                return e.code, json.loads(e.read() or b"{}")
            raise

    def graphql(self, query, variables):
        body = json.dumps({"query": query, "variables": variables}).encode()
        req = urllib.request.Request(GRAPHQL, data=body, method="POST")
        req.add_header("Authorization", "token " + self.token)
        req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req) as resp:
            payload = json.loads(resp.read())
        if payload.get("errors"):
            raise RuntimeError(str(payload["errors"]))
        return payload["data"]


LABELS = [
    ("M1", "1D76DB", "API Gateway"),
    ("M2", "5319E7", "Identity & Auth"),
    ("M3", "006B75", "Profile"),
    ("M4", "0052CC", "Resume"),
    ("M5", "0E8A16", "Job Discovery"),
    ("M6", "1D76DB", "Matching Engine"),
    ("M7", "B60205", "Coach Agent (Supervisor)"),
    ("M8", "FBCA04", "Interview Prep"),
    ("M9", "006B75", "Application Tracker"),
    ("M10", "D93F0B", "Learning"),
    ("M11", "5319E7", "MCP Server"),
    ("M12", "0052CC", "RAG Service"),
    ("M13", "0E8A16", "Notification"),
    ("M14", "D4C5F9", "Billing & Analytics"),
    ("wedge-h1b", "E11D21", "H-1B / visa wedge work"),
    ("testing", "FBCA04", "Testing / QA"),
    ("qa-agent", "B60205", "Daily QA agent"),
    ("docs", "0E8A16", "Docs / planning"),
]

MILESTONES = [
    ("W0 — Web UI foundation", "2026-11-20T23:59:59Z"),
    ("W1 — Connect UI to backend", "2026-11-27T23:59:59Z"),
    ("W2 — Core journeys", "2026-12-11T23:59:59Z"),
    ("W3 — Agent brain on web", "2027-01-01T23:59:59Z"),
    ("W4 — Polish / containers / cloud", "2027-01-22T23:59:59Z"),
    ("Phase M — Mobile", "2027-02-19T23:59:59Z"),
    ("Phase 5 — Microservices split", None),
]

ISSUES = [
    {
        "title": "[W0] Next.js shell + design system + auth UI (/login)",
        "labels": ["M2"],
        "milestone": "W0 — Web UI foundation",
        "body": """Phase: W0 — Web UI foundation (Nov 9–20, 2026). Slice 1 of W0 (Mon Nov 9).

## What
- Scaffold the web app: Next.js 16.4.x + React 19.3.1 + TypeScript 5.9 + Tailwind CSS 4.3.4 + shadcn/ui, pnpm + Node 22 LTS.
- Design system: design tokens (colors, type, spacing), shadcn/ui components in place, app shell (nav + layout) shared by all 12 pages.
- Auth UI: /login page. JWT access token in memory only, refresh token in httpOnly cookie — never localStorage for the access token.
- MSW mocks for auth so the UI works with no backend.

## Exit criteria (Done only when all pass)
- [ ] App runs locally (pnpm dev), /login renders with no console errors.
- [ ] Mock login works and redirects to the dashboard.
- [ ] Layout holds at 390px width.
- [ ] No real backend needed for any of the above.

Plan: docs/EpitomeJob-Execution-Plan-v3-Updated.md §W0.""",
    },
    {
        "title": "[W0] Dashboard + profile + resume upload UI",
        "labels": ["M3", "M4"],
        "milestone": "W0 — Web UI foundation",
        "body": """Phase: W0. Slice 2 of W0 (Tue–Wed Nov 10–11).

## What
- Dashboard (/) with H-1B wedge widgets: 60-day countdown + sponsorship filter (flag features.sponsorship-filter.enabled).
- Profile page (/profile) with completeness score (headline/summary/location/work type/salary = 20 points each).
- Resume upload UI (/resumes): PDF only, shows READY/FAILED status from the mock, then the real backend in W1.
- MSW mocks for /profiles/me and /resumes.

## Exit criteria
- [ ] All three pages render, no console errors, 390px width holds.
- [ ] Fake PDF upload shows READY; a bad file shows FAILED.
- [ ] Completeness score updates as fields are filled.

Plan: docs/EpitomeJob-Execution-Plan-v3-Updated.md §W0.""",
    },
    {
        "title": "[W0] Coach chat + approval inbox UI",
        "labels": ["M7"],
        "milestone": "W0 — Web UI foundation",
        "body": """Phase: W0. Slice 3 of W0 (Thu Nov 12).

## What
- Coach chat page (/coach): chat UI with mock Coach replies.
- Approval inbox (/approvals): approval cards from the mock; Approve removes the card from the inbox, Reject keeps an audit note. No write ever happens without an approval card.
- SSE stream UI hookup point (/approvals/stream) — wired to the real backend in W1.

## Exit criteria
- [ ] Coach chat returns a mock reply.
- [ ] Approval card Approve removes it from the inbox; Reject records the decision.
- [ ] Pages render, no console errors, 390px width holds.

Plan: docs/EpitomeJob-Execution-Plan-v3-Updated.md §W0.""",
    },
    {
        "title": "[W0] First gate check (Fri Nov 13)",
        "labels": ["testing"],
        "milestone": "W0 — Web UI foundation",
        "body": """Phase: W0. Gate check 1 of 2 (Fri Nov 13).

## What
Run the W0 gate so far: every page built to date renders, no console errors, 390px width holds, mock login + resume upload + approval flow work.

## Exit criteria
- [ ] Gate checklist run and recorded here (pass, or the failing item listed with its fix issue).
- [ ] Anything failing gets a fix before Week 2 starts — no carrying a red gate forward.

Cake rule: a phase is Done only when its gate passes.""",
    },
    {
        "title": "[W0] Remaining pages: /register, /jobs, /matches, /interviews, /tracker, /learning",
        "labels": ["M5", "M6", "M8", "M9", "M10"],
        "milestone": "W0 — Web UI foundation",
        "body": """Phase: W0. Slice 4 of W0 (Mon–Wed Nov 16–18).

## What
- /register (auth UI, same token rules as /login).
- /jobs (job list with sponsorship filter) + /matches (match cards with "why this matches" + gaps).
- /interviews (mock interview + rubric + feedback UI).
- /tracker (kanban: SAVED → APPLIED → INTERVIEW → OFFER → REJECTED).
- /learning is a placeholder page linking out only — Learning M10 is cut from v1.
- All on MSW mocks.

## Exit criteria
- [ ] Every page renders, no console errors, 390px width holds.
- [ ] All 12 pages now exist (login, register, dashboard, profile, resumes, jobs, matches, coach, approvals, interviews, tracker, learning).

Plan: docs/EpitomeJob-Execution-Plan-v3-Updated.md §W0.""",
    },
    {
        "title": "[W0] Hardening + final gate (incl. first green nightly run)",
        "labels": ["testing", "qa-agent"],
        "milestone": "W0 — Web UI foundation",
        "body": """Phase: W0. Final gate (Thu–Fri Nov 19–20).

## What
- Hardening: remove dead mock code paths, fix any 390px breaks, zero console errors on all 12 pages.
- Run the full W0 gate: every page renders; mock login works; resume upload shows READY/FAILED; approval flow works; no real backend needed.
- First green nightly QA run (Nightly QA workflow, 6:00 AM ET) — if it is not green by Nov 20, W0 is NOT done.

## Exit criteria
- [ ] All 8 W0 exit criteria pass (see docs/EpitomeJob-Execution-Plan-v3-Updated.md §W0).
- [ ] Nightly QA run green at least once.
- [ ] Excel tracker (docs/EpitomeJob-Project-Tracker.xlsx) updated: W0 Status + % Complete.

Cake rule: a phase is Done only when its gate passes.""",
    },
    {
        "title": "[Phase 0] Backend gate verification on Mac (run starter, curls)",
        "labels": ["M2", "M3", "M4", "M7", "testing"],
        "milestone": "W1 — Connect UI to backend",
        "body": """Phase: W1 prerequisite — the Phase 0 backend starter (backend/) has never been run yet.

## What
On the Mac, from the repo root:
- cp backend/.env.example backend/.env and set a real APP_JWT_SECRET.
- docker compose up -d (Postgres 16 + MinIO), then mvn spring-boot:run.
- Run the Phase 0 gate curls in backend/README.md: health → register → login → profile → resume upload → approval propose → approve → SSE stream.

## Exit criteria
- [ ] All gate curls pass through the browser/Postman (see backend/README.md).
- [ ] If anything fails, paste ONLY the error tail here — never the full launch log.

This unblocks W1 (wiring the UI to the real backend).""",
    },
]


def main():
    token = getpass.getpass("GitHub Personal Access Token (paste, it won't show): ").strip()
    if not token:
        print("No token entered. Exiting.")
        sys.exit(1)
    gh = GitHub(token)

    status, me = gh.call("GET", "/user")
    print("Signed in as: %s" % me.get("login"))

    print("\n-- Labels --")
    for name, color, desc in LABELS:
        status, _ = gh.call(
            "POST", "/repos/%s/labels" % REPO,
            {"name": name, "color": color, "description": desc},
            tolerate=(422,),
        )
        print("  %s: %s" % (name, "created" if status == 201 else "already exists"))

    print("\n-- Milestones --")
    milestone_numbers = {}
    for title, due_on in MILESTONES:
        data = {"title": title, "state": "open"}
        if due_on:
            data["due_on"] = due_on
        status, created = gh.call("POST", "/repos/%s/milestones" % REPO, data,
                                  tolerate=(422,))
        if status == 201:
            milestone_numbers[title] = created["number"]
            print("  %s: created (#%d)" % (title, created["number"]))
        else:
            # Already exists — look it up.
            _, existing = gh.call("GET", "/repos/%s/milestones?state=all&per_page=100" % REPO)
            match = [m for m in existing if m["title"] == title]
            if match:
                milestone_numbers[title] = match[0]["number"]
                print("  %s: already exists (#%d)" % (title, match[0]["number"]))
            else:
                print("  %s: could not create or find — issues will be created without it" % title)

    print("\n-- Issues --")
    created_issues = []  # (number, node_id, html_url)
    for issue in ISSUES:
        data = {
            "title": issue["title"],
            "body": issue["body"],
            "labels": issue["labels"],
        }
        num = milestone_numbers.get(issue["milestone"])
        if num:
            data["milestone"] = num
        status, created = gh.call("POST", "/repos/%s/issues" % REPO, data)
        created_issues.append((created["number"], created["node_id"], created["html_url"]))
        print("  #%d %s" % (created["number"], issue["title"]))

    print("\n-- Projects board --")
    try:
        data = gh.graphql("{ viewer { id } }", {})
        owner_id = data["viewer"]["id"]
        data = gh.graphql(
            "mutation($ownerId: ID!, $title: String!) {"
            " createProjectV2(input:{ownerId:$ownerId, title:$title})"
            " { projectV2 { id number url } } }",
            {"ownerId": owner_id, "title": "EpitomeJob"},
        )
        project = data["createProjectV2"]["projectV2"]
        print("  Board created: %s" % project["url"])
        for number, node_id, url in created_issues:
            gh.graphql(
                "mutation($projectId: ID!, $contentId: ID!) {"
                " addProjectV2ItemById(input:{projectId:$projectId, contentId:$contentId})"
                " { item { id } } }",
                {"projectId": project["id"], "contentId": node_id},
            )
            print("  Added issue #%d to the board" % number)
    except Exception as e:  # noqa: BLE001 — report, don't fail the whole run
        print("  Board step failed: %s" % e)
        print("  Your token probably lacks the `project` scope (separate from `repo`).")
        print("  Fallback (2 min): github.com → your profile → Projects → New project")
        print("  → title 'EpitomeJob', then add the issues below by searching their titles.")

    print("\nDone.")
    print("Issues:")
    for number, _, url in created_issues:
        print("  #%d %s" % (number, url))
    print("Note: the board's Status field starts as Todo / In Progress / Done.")
    print("To match the plan (Backlog → Ready → In Progress → In Review → Done),")
    print("open the project, click the Status field and edit its options in the UI.")


if __name__ == "__main__":
    main()
