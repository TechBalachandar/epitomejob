// Feature 1: the day-by-day plan for the 60-day H-1B clock.
// Pure and testable: every item is derived from the user's real data —
// live matches / live sponsor jobs, their own builder documents, and the
// frozen clock math. Nothing is hardcoded: with no sponsor matches there
// is no apply list, with no documents there is no tailor list, and with
// no clock start date there is no plan at all (the caller shows setup).
import type {
  Job,
  Match,
  Profile,
  ResumeBuilderDocument,
} from "./types";
import { clockStatus } from "./h1b-clock";
import { containsKeyword, resumeHaystack } from "./resume-builder";
import {
  profileOverlapsJob,
  type MatchVia,
} from "./sponsor-alerts";

export type PlanItemKind = "apply" | "tailor" | "interview";

export interface PlanItem {
  kind: PlanItemKind;
  title: string;
  detail: string;
  href: string;
  jobId?: string;
  documentId?: string | null;
}

export interface SponsorJobEntry {
  job: Job;
  score: number | null;
  via: MatchVia;
}

export interface ResumeToTailor {
  document: ResumeBuilderDocument;
  /** The job this resume would be tailored for (best sponsor match). */
  job: Job;
  /** Job skills missing from this resume (whole-token check). */
  missingSkills: string[];
}

export interface WeekDay {
  /** ISO date (YYYY-MM-DD) for this plan day. */
  dateISO: string;
  label: string;
  items: PlanItem[];
}

export interface DailyPlan {
  /** Null when the clock is not set — there is no plan without a date. */
  daysLeft: number | null;
  deadline: Date | null;
  sponsorJobs: SponsorJobEntry[];
  resumesToTailor: ResumeToTailor[];
  interviewJob: Job | null;
  today: PlanItem[];
  week: WeekDay[];
}

export const MAX_SPONSOR_JOBS = 5;
export const MAX_RESUMES_TO_TAILOR = 3;

function applyItem(job: Job, score: number | null): PlanItem {
  return {
    kind: "apply",
    title: `Apply to ${job.title} at ${job.company}`,
    detail:
      score !== null
        ? `Backend match ${score}% · sponsors H-1B`
        : "Sponsor job from the live feed · sponsors H-1B",
    href: job.sourceUrl ?? "/jobs",
    jobId: job.id,
  };
}

/**
 * Sponsor jobs to apply to: backend-scored sponsor matches first (best
 * score first), then sponsor jobs from the live feed that overlap the
 * profile (skills / location / headline — same rule as the alerts).
 * Capped at MAX_SPONSOR_JOBS. Jobs the user already applied to are not
 * filtered here — the tracker is the source of truth for that.
 */
export function sponsorJobsToApply(
  jobs: Job[],
  matches: Match[],
  profile: Profile | null | undefined,
): SponsorJobEntry[] {
  const out: SponsorJobEntry[] = [];
  const seenJobIds = new Set<string>();
  const matched = [...matches]
    .filter((m) => m.job.sponsorsH1b)
    .sort((a, b) => b.score - a.score);
  for (const m of matched) {
    out.push({ job: m.job, score: m.score, via: "match" });
    seenJobIds.add(m.job.id);
  }
  for (const job of jobs) {
    if (out.length >= MAX_SPONSOR_JOBS) break;
    if (!job.sponsorsH1b || seenJobIds.has(job.id)) continue;
    const overlap = profileOverlapsJob(job, profile);
    if (!overlap) continue;
    out.push({ job, score: null, via: overlap.via });
    seenJobIds.add(job.id);
  }
  return out.slice(0, MAX_SPONSOR_JOBS);
}

/** Job skills missing from a builder document (whole-token check). */
export function missingSkillsForDocument(
  doc: ResumeBuilderDocument,
  job: Job,
): string[] {
  const haystack = resumeHaystack(doc.state);
  return job.skills.filter(
    (skill) => skill.trim() && !containsKeyword(haystack, skill),
  );
}

/**
 * Resumes to tailor: each builder document paired with the best sponsor
 * job and the skills it is missing for that job. Documents whose resume
 * already covers every skill of the target job are left out — there is
 * nothing to tailor.
 */
export function resumesToTailor(
  documents: ResumeBuilderDocument[],
  sponsorJobs: SponsorJobEntry[],
): ResumeToTailor[] {
  if (sponsorJobs.length === 0) return [];
  const target = sponsorJobs[0].job;
  const out: ResumeToTailor[] = [];
  for (const doc of documents) {
    const missing = missingSkillsForDocument(doc, target);
    if (missing.length === 0) continue;
    out.push({ document: doc, job: target, missingSkills: missing });
    if (out.length >= MAX_RESUMES_TO_TAILOR) break;
  }
  return out;
}

function dateISOFor(offsetDays: number, today: Date): string {
  const d = new Date(
    Date.UTC(
      today.getUTCFullYear(),
      today.getUTCMonth(),
      today.getUTCDate() + offsetDays,
    ),
  );
  return d.toISOString().slice(0, 10);
}

function dayLabel(offsetDays: number, today: Date): string {
  if (offsetDays === 0) return "Today";
  if (offsetDays === 1) return "Tomorrow";
  const d = new Date(
    Date.UTC(
      today.getUTCFullYear(),
      today.getUTCMonth(),
      today.getUTCDate() + offsetDays,
    ),
  );
  return d.toLocaleDateString("en-US", {
    weekday: "long",
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

/**
 * Build the full plan. Returns daysLeft null (and empty lists) when the
 * clock is not set — the page shows the setup prompt instead.
 */
export function buildDailyPlan(
  input: {
    profile: Profile | null | undefined;
    matches: Match[];
    jobs: Job[];
    documents: ResumeBuilderDocument[];
  },
  today: Date = new Date(),
): DailyPlan {
  const clock = clockStatus(input.profile?.h1bClockStartDate ?? null, today);
  if (clock.state === "not-set") {
    return {
      daysLeft: null,
      deadline: null,
      sponsorJobs: [],
      resumesToTailor: [],
      interviewJob: null,
      today: [],
      week: [],
    };
  }
  const sponsorJobs = sponsorJobsToApply(input.jobs, input.matches, input.profile);
  const tailor = resumesToTailor(input.documents, sponsorJobs);
  const interviewJob = sponsorJobs[0]?.job ?? input.matches[0]?.job ?? null;

  const todayItems: PlanItem[] = [];
  if (sponsorJobs[0]) todayItems.push(applyItem(sponsorJobs[0].job, sponsorJobs[0].score));
  if (tailor[0]) {
    todayItems.push({
      kind: "tailor",
      title: `Tailor "${tailor[0].document.title ?? "Untitled resume"}" for ${tailor[0].job.title}`,
      detail: `Missing: ${tailor[0].missingSkills.join(", ")}`,
      href: tailor[0].document.id
        ? `/resume-builder?documentId=${encodeURIComponent(tailor[0].document.id)}`
        : "/resume-builder",
      documentId: tailor[0].document.id,
      jobId: tailor[0].job.id,
    });
  }
  if (interviewJob) {
    todayItems.push({
      kind: "interview",
      title: `Mock interview: ${interviewJob.title} at ${interviewJob.company}`,
      detail: "Practice with the Coach before you apply",
      href: "/interviews",
      jobId: interviewJob.id,
    });
  }

  // Next 7 days: rotate through the sponsor jobs (one apply per day),
  // a tailor slot every other day, and interview practice twice a week.
  // Days with no real data behind them get no items — never filler.
  const week: WeekDay[] = [];
  for (let offset = 0; offset < 7; offset++) {
    const items: PlanItem[] = [];
    const jobEntry = sponsorJobs[offset % Math.max(sponsorJobs.length, 1)];
    if (sponsorJobs.length > 0 && jobEntry) {
      items.push(applyItem(jobEntry.job, jobEntry.score));
    }
    if (offset % 2 === 1 && tailor[offset % tailor.length]) {
      const t = tailor[offset % tailor.length];
      items.push({
        kind: "tailor",
        title: `Tailor "${t.document.title ?? "Untitled resume"}" for ${t.job.title}`,
        detail: `Missing: ${t.missingSkills.join(", ")}`,
        href: t.document.id
          ? `/resume-builder?documentId=${encodeURIComponent(t.document.id)}`
          : "/resume-builder",
        documentId: t.document.id,
        jobId: t.job.id,
      });
    }
    if ((offset === 2 || offset === 5) && interviewJob) {
      items.push({
        kind: "interview",
        title: `Mock interview: ${interviewJob.title}`,
        detail: "Practice with the Coach",
        href: "/interviews",
        jobId: interviewJob.id,
      });
    }
    week.push({
      dateISO: dateISOFor(offset, today),
      label: dayLabel(offset, today),
      items,
    });
  }

  return {
    daysLeft: clock.daysLeft,
    deadline: clock.deadline,
    sponsorJobs,
    resumesToTailor: tailor,
    interviewJob,
    today: todayItems,
    week,
  };
}
