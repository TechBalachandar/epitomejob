#!/usr/bin/env python3
"""Quester dry-run: exercise the failure-issue flow against FAKE data.

Prints exactly the issue title/body the quester.yml triage job WOULD
create, and the board-update call it WOULD make. Makes NO live GitHub
API calls (no network at all) and creates/updates nothing.

The title/body template below MUST be kept in sync with
.github/workflows/quester.yml (step "File or update failure issue").
If you change one, change the other.

Usage:
    python3 scripts/quester-dry-run.py
    python3 scripts/quester-dry-run.py --green   # also show the green-run path
"""
import argparse
import sys

REPO = "TechBalachandar/epitomejob"
SERVER_URL = "https://github.com"
PROJECT_OWNER = "TechBalachandar"
PROJECT_NUMBER = 1
PROJECT_URL = f"{SERVER_URL}/users/{PROJECT_OWNER}/projects/{PROJECT_NUMBER}"

# --- Fake run summary (as if quester.yml had just finished) ---------------
FAKE_FAILING_RUN = {
    "sha": "abc1234def5678abc1234def5678abc1234def5",
    "short_sha": "abc1234",
    "event": "push",
    "scope": "main",
    "run_id": "12345678901",
    "failed": ["backend", "web-e2e"],
    "passed": ["python-tests", "python-evals", "web-unit"],
    "first_failing_step": "Run backend tests + coverage gate",
}
FAKE_GREEN_RUN = {
    "sha": "def5678abc1234def5678abc1234def5678abc1",
    "short_sha": "def5678",
    "event": "push",
    "scope": "main",
    "run_id": "12345678902",
}


def run_url(run):
    return f"{SERVER_URL}/{REPO}/actions/runs/{run['run_id']}"


def commit_url(run):
    return f"{SERVER_URL}/{REPO}/commit/{run['sha']}"


def failure_title(run):
    return f"[Quester] CI failure {run['short_sha']} — {', '.join(run['failed'])}"


def failure_body(run):
    return (
        "## Quester CI failure\n"
        f"- Commit: {run['sha']} ({commit_url(run)})\n"
        f"- Scope: {run['scope']}\n"
        f"- Run: {run_url(run)}\n"
        f"- Failing suites: {', '.join(run['failed'])}\n"
        f"- Passing suites: {', '.join(run['passed'])}\n"
        f"- First failing step: {run['first_failing_step']}\n"
        f"- Trigger: {run['event']}\n"
    )


def board_command(issue_url):
    # What quester.yml would run (with the real PROJECTS_PAT as GH_TOKEN).
    # Never executed here.
    return (
        f"GH_TOKEN=<PROJECTS_PAT> gh project item-add {PROJECT_NUMBER} "
        f"--owner {PROJECT_OWNER} --url {issue_url}"
    )


def show_failing_run():
    run = FAKE_FAILING_RUN
    print("=" * 70)
    print("DRY RUN — failing commit (no GitHub API calls are made)")
    print("=" * 70)
    print()
    print("Would-be issue title:")
    print(f"  {failure_title(run)}")
    print()
    print("Would-be issue body:")
    for line in failure_body(run).splitlines():
        print(f"  {line}")
    print()
    print("Board update that WOULD be made (not executed):")
    print(f"  {board_command(SERVER_URL + '/' + REPO + '/issues/42')}")
    print(f"  Project: {PROJECT_URL}")
    print("  (Skipped gracefully with a notice if the PROJECTS_PAT secret is absent.)")
    print()


def show_green_run():
    run = FAKE_GREEN_RUN
    print("=" * 70)
    print("DRY RUN — green push to main (no GitHub API calls are made)")
    print("=" * 70)
    print()
    print("Would-be action: close every open issue labelled `qa-agent` whose")
    print('title starts with "[Quester] CI failure", each with this comment:')
    print()
    print(f"  Quester run is green on main: {run_url(run)} "
          f"(commit {run['sha']}). Closing this failure issue.")
    print()
    print("Would-be commands (not executed):")
    print("  gh issue list --label qa-agent --state open --limit 100 "
          "--json number,title")
    print("  gh issue close <N> --reason completed   # per open failure issue")
    print()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--green", action="store_true",
                        help="also show the green-run close path")
    args = parser.parse_args()
    show_failing_run()
    if args.green:
        show_green_run()
    print("DRY RUN COMPLETE — no issues created, no board writes, no API calls.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
