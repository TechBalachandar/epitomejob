// Pure derived dashboard logic (W5-API-CONTRACT §9/§10): next steps,
// recent activity, and the 5-item completeness checklist. Kept pure so
// Vitest can cover the rules without rendering; the dashboard and profile
// pages only render what these return.

import type {
  Approval,
  Match,
  Profile,
  ResumeItem,
  TrackerDto,
} from "./types";

// --- Completeness checklist (§10) -----------------------------------------
// Per-field done/missing is derived from the returned field values using
// exactly the server's rule (non-blank / both salaries set). The SCORE
// itself always comes from the server's `profile.completeness` — never
// from counting these items.

export interface CompletenessItem {
  key: "headline" | "summary" | "location" | "workType" | "salary";
  label: string;
  done: boolean;
}

export function completenessItems(profile: Profile): CompletenessItem[] {
  const nonBlank = (v: string | null | undefined) =>
    typeof v === "string" && v.trim().length > 0;
  return [
    { key: "headline", label: "Headline", done: nonBlank(profile.headline) },
    { key: "summary", label: "Summary", done: nonBlank(profile.summary) },
    { key: "location", label: "Location", done: nonBlank(profile.location) },
    { key: "workType", label: "Work type", done: nonBlank(profile.workType) },
    {
      key: "salary",
      label: "Salary range",
      done: profile.salaryMin !== null && profile.salaryMax !== null,
    },
  ];
}

export function missingCompletenessLabels(profile: Profile): string[] {
  return completenessItems(profile)
    .filter((item) => !item.done)
    .map((item) => item.label);
}

// --- Next steps (§9) -------------------------------------------------------
// Derived, in this priority order; only steps whose condition holds appear:
// 1. no resume at all → upload CTA
// 2. completeness < 100 → what's missing
// 3. pending approvals > 0 → count + link
// 4. top match → score + title + company

export interface NextStep {
  text: string;
  href: string;
}

export function deriveNextSteps(input: {
  resumes: ResumeItem[] | undefined;
  profile: Profile | undefined;
  pendingApprovals: Approval[] | undefined;
  matches: Match[] | undefined;
}): NextStep[] {
  const steps: NextStep[] = [];
  const resumes = input.resumes ?? [];
  const approvals = input.pendingApprovals ?? [];
  const matches = input.matches ?? [];

  if (resumes.length === 0) {
    steps.push({
      text: "Upload your resume to unlock matching.",
      href: "/resumes",
    });
  }

  if (input.profile && input.profile.completeness < 100) {
    const missing = missingCompletenessLabels(input.profile);
    steps.push({
      text:
        missing.length > 0
          ? `Complete your profile — missing: ${missing.join(", ")}.`
          : "Complete your profile to reach 100%.",
      href: "/profile",
    });
  }

  if (approvals.length > 0) {
    steps.push({
      text: `${approvals.length} approval${approvals.length === 1 ? "" : "s"} waiting for your decision.`,
      href: "/approvals",
    });
  }

  const topMatch = [...matches].sort((a, b) => b.score - a.score)[0];
  if (topMatch) {
    steps.push({
      text: `Review your ${topMatch.score}% match: ${topMatch.job.title} at ${topMatch.job.company}.`,
      href: "/matches",
    });
  }

  return steps;
}

// --- Recent activity (§9) --------------------------------------------------
// Merge latest tracker entries, approvals, and resume uploads; sort by
// date desc; take 5. Every row carries a real date from the API.
// (Approvals carry no created-at, so a decided approval uses decidedAt
// and a pending one uses its expiry date — the only dates the API gives.)

export interface ActivityItem {
  id: string;
  text: string;
  /** ISO date string, real data only. */
  at: string;
  href: string;
}

export function trackerStatusLabel(status: TrackerDto["status"]): string {
  switch (status) {
    case "SAVED":
      return "Saved";
    case "APPLIED":
      return "Applied to";
    case "INTERVIEW":
      return "Interviewing for";
    case "OFFER":
      return "Offer from";
    case "REJECTED":
      return "Rejected from";
  }
}

export function deriveRecentActivity(input: {
  tracker: TrackerDto[] | undefined;
  approvals: Approval[] | undefined;
  resumes: ResumeItem[] | undefined;
}): ActivityItem[] {
  const items: ActivityItem[] = [];

  for (const entry of input.tracker ?? []) {
    items.push({
      id: `tracker-${entry.id}`,
      text: `${trackerStatusLabel(entry.status)} ${entry.title} at ${entry.company}`,
      at: entry.updatedAt,
      href: "/tracker",
    });
  }

  for (const approval of input.approvals ?? []) {
    const decided = approval.status !== "PENDING";
    items.push({
      id: `approval-${approval.id}`,
      text: decided
        ? `Approval ${approval.action.replaceAll("_", " ").toLowerCase()} — ${approval.status.toLowerCase()}`
        : `Approval pending: ${approval.action.replaceAll("_", " ").toLowerCase()}`,
      at: approval.decidedAt ?? approval.expiresAt,
      href: "/approvals",
    });
  }

  for (const resume of input.resumes ?? []) {
    items.push({
      id: `resume-${resume.id}`,
      text: `Resume uploaded: ${resume.fileName} — ${resume.status}`,
      at: resume.createdAt,
      href: "/resumes",
    });
  }

  return items
    .filter((item) => !Number.isNaN(Date.parse(item.at)))
    .sort((a, b) => Date.parse(b.at) - Date.parse(a.at))
    .slice(0, 5);
}

/** Active tracker entries: status in SAVED, APPLIED, INTERVIEW (§9). */
export function activeTrackerCount(
  tracker: TrackerDto[] | undefined,
): number {
  return (tracker ?? []).filter((t) =>
    ["SAVED", "APPLIED", "INTERVIEW"].includes(t.status),
  ).length;
}

/** Top match by score, or null when there are no matches. */
export function topMatch(matches: Match[] | undefined): Match | null {
  const list = matches ?? [];
  if (list.length === 0) return null;
  return [...list].sort((a, b) => b.score - a.score)[0];
}

/** Latest resume by upload date, or null when there are none. */
export function latestResume(
  resumes: ResumeItem[] | undefined,
): ResumeItem | null {
  const list = resumes ?? [];
  if (list.length === 0) return null;
  return [...list].sort(
    (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
  )[0];
}

/** Short date for activity rows, e.g. "Nov 11, 4:12 PM". */
export function formatActivityDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}
