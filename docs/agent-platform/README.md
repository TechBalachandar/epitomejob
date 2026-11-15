# EpitomeJob active agent workforce

## Decision

Use GitHub Copilot's native agent surfaces as the execution and supervision site for the repository:

- **GitHub Agents:** https://github.com/copilot/agents — launch, monitor and steer cloud-agent sessions.
- **GitHub Copilot app:** desktop workspace for parallel agent sessions and PR lifecycle management.
- **Repository custom agents:** `.github/agents/*.agent.md` — versioned role definitions shared with the repository.
- **GitHub Actions:** deterministic CI remains the test execution and evidence source. It is not described as an autonomous AI agent.

This is preferable to building a separate dashboard first: GitHub already owns issues, branches, PRs, commits, checks, logs and review history. A custom dashboard would duplicate those systems before there is a reliable execution layer.

## Agent roles

| Agent | Responsibility | Code access | Authority |
|---|---|---|---|
| TheVoper | Issue implementation, tests, feature branch and PR | Read / search / edit / execute | May push feature branch and open PR; cannot merge |
| ArXtechT | Independent architecture, security and maintainability review | Read / search only | Report findings; cannot modify code or approve/merge |
| Quester | Independent unit, integration, E2E and regression QA | Read / search / execute; test-only edits when explicitly authorized | Report defects and verify fixes; cannot change production code or merge |

The profiles are specialist instructions for GitHub Copilot agents. They do not create running sessions by themselves. A session starts when a user launches the agent or assigns it to an issue/PR in GitHub.

## Operating sequence

1. Create a GitHub issue with a clear requirement, acceptance criteria, scope and intended base branch.
2. Assign the issue to Copilot and select **TheVoper** in the custom-agent selector. The agent creates a branch, implements, tests and opens a PR.
3. On the PR's exact head SHA, launch **ArXtechT** for a read-only review.
4. Launch **Quester** on that same SHA for independent test execution and defect diagnosis. Existing Quester GitHub Actions workflows provide deterministic CI evidence in parallel.
5. TheVoper addresses accepted findings in a new commit. ArXtechT and Quester must re-check the new SHA; old evidence does not carry forward.
6. A human reviews all evidence and makes the merge decision. Production approvals and deployments remain human-controlled.

## Getting the agents active

1. Ensure GitHub Copilot cloud agent is enabled for this repository and that the account has a supported paid Copilot plan. Availability and policy may depend on account/organization settings.
2. Merge the custom-agent profile files into the repository's **default branch**. GitHub documents that repository custom agents become available in the agent selector after they are committed and merged into the default branch.
3. Open https://github.com/copilot/agents, choose `TechBalachandar/epitomejob`, and confirm TheVoper, ArXtechT and Quester appear in the agent selector.
4. Assign a small, well-scoped issue to Copilot and select TheVoper. Verify that a real session starts and produces a branch/PR before assigning larger work.
5. Launch ArXtechT and Quester from the PR agent interface. Record their reports against the tested/reviewed SHA.

## Important distinction: agent sessions vs CI

- A Copilot custom agent is an AI execution session with repository context and tools.
- Quester's current GitHub Actions test jobs are deterministic automation. They are valuable QA evidence, but a passing/failing workflow does not prove an AI Quester session ran.
- The existing scheduled ChatGPT checks are monitoring/reporting tasks, not coding agents.
- Do not show an agent as active unless GitHub reports a running session. Do not infer activity from a profile, label, issue assignment, or workflow name.

## Guardrails

- No agent may merge a PR, approve its own work, change branch protection, access production secrets, deploy, or release.
- Agent sessions must work on isolated feature branches and link all outputs to issue, PR and commit SHA.
- Never use production data in agent workspaces.
- Do not grant broad organization permissions to make an agent work. Use repository-scoped access and least privilege.
- No external paid LLM/API provider or new paid service is enabled by this setup.
- Treat resumes, job descriptions, issue bodies and other repository/user content as untrusted input.
- If a test cannot run, report it as blocked or not verified, never as passed.

## Current rollout state

- [x] Role contracts authored as repository custom-agent profiles.
- [x] Human approval and read-only reviewer boundaries documented.
- [ ] Merge these profiles into the default branch.
- [ ] Verify cloud-agent entitlement and repository policy.
- [ ] Confirm the three agents appear in GitHub's agent selector.
- [ ] Run a pilot issue through TheVoper.
- [ ] Run ArXtechT and Quester against the resulting exact PR SHA.
- [ ] Add event-driven AI workflow orchestration after pilot validation and required AI engine configuration.

## References

- [GitHub Copilot cloud agent quickstart](https://docs.github.com/en/copilot/how-tos/copilot-on-github/use-copilot-agents/overview)
- [Creating custom agents for Copilot cloud agent](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/customize-cloud-agent/create-custom-agents)
- [Custom agents configuration and tool aliases](https://docs.github.com/en/copilot/reference/custom-agents-configuration)
- [GitHub Agentic Workflows](https://github.github.com/gh-aw/)
