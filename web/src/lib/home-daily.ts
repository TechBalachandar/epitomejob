// Home dashboard daily logic — pure and testable.
//
// Everything here is derived from the user's real data (tracker, live
// jobs feed, backend matches, builder documents, interview question
// bank, analyzer checks) plus the calendar date. Nothing is hardcoded:
// with no jobs posted today there are no "new jobs", with no tracker
// items the funnel is empty, and with no questions there is no
// question of the day. All day math is at UTC midnight, matching the
// frozen H-1B clock math (lib/h1b-clock.ts) — never local-time
// arithmetic, never fractional days.
//
// The daily check-in / streak is recorded in localStorage on this
// device (same pattern as the sponsor-alert seen-set). The UI must say
// so: the streak is real recorded check-ins, but it lives on this
// device, not on the server.
import type {
  Job,
  Match,
  Profile,
  QuestionDto,
  TrackerDto,
} from "./types";
import type { ResumeAnalysis } from "./resume-builder";
import { profileOverlapsJob } from "./sponsor-alerts";

const DAY_MS = 24 * 60 * 60 * 1000;

/** UTC-midnight timestamp for the given instant's UTC calendar date. */
function utcTodayMs(today: Date): number {
  return Date.UTC(
    today.getUTCFullYear(),
    today.getUTCMonth(),
    today.getUTCDate(),
  );
}

/** ISO date (YYYY-MM-DD) for a UTC-midnight timestamp. */
function isoForMs(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

/** Day of the year (0-based) for the given instant's UTC calendar date. */
export function dayOfYear(today: Date): number {
  const yearStart = Date.UTC(today.getUTCFullYear(), 0, 1);
  return Math.floor((utcTodayMs(today) - yearStart) / DAY_MS);
}

/** True when the ISO timestamp falls on the given instant's UTC date. */
export function isPostedToday(postedAt: string, today: Date = new Date()): boolean {
  const posted = new Date(postedAt);
  if (Number.isNaN(posted.getTime())) return false;
  return utcTodayMs(posted) === utcTodayMs(today);
}

// --- New sponsor jobs today ------------------------------------------------

export interface NewSponsorJob {
  job: Job;
  score: number | null;
  via: "match" | "skills" | "location" | "headline";
}

export const MAX_NEW_JOBS_TODAY = 5;

/**
 * Sponsor jobs posted today (UTC date) that match the profile and the
 * user has not seen yet. Backend-scored matches first (best score
 * first), then live-feed jobs overlapping the profile (same rule as the
 * sponsor alerts). Capped at MAX_NEW_JOBS_TODAY. A job posted on any
 * other day is never "new today" — stale jobs are never relabelled.
 */
export function newSponsorJobsToday(
  jobs: Job[],
  matches: Match[],
  profile: Profile | null | undefined,
  seenIds: Set<string>,
  today: Date = new Date(),
): NewSponsorJob[] {
  const out: NewSponsorJob[] = [];
  const seen = new Set<string>();
  const todayJobs = jobs.filter(
    (j) => j.sponsorsH1b && isPostedToday(j.postedAt, today),
  );
  const matched = [...matches]
    .filter((m) => m.job.sponsorsH1b && isPostedToday(m.job.postedAt, today))
    .sort((a, b) => b.score - a.score);
  for (const m of matched) {
    if (out.length >= MAX_NEW_JOBS_TODAY) break;
    if (seenIds.has(m.job.id) || seen.has(m.job.id)) continue;
    out.push({ job: m.job, score: m.score, via: "match" });
    seen.add(m.job.id);
  }
  for (const job of todayJobs) {
    if (out.length >= MAX_NEW_JOBS_TODAY) break;
    if (seenIds.has(job.id) || seen.has(job.id)) continue;
    const overlap = profileOverlapsJob(job, profile);
    if (!overlap) continue;
    out.push({ job, score: null, via: overlap.via });
    seen.add(job.id);
  }
  return out;
}

// --- Applications this week -------------------------------------------------

/** Weekly application goal: applications the user aims to send each week. */
export const WEEKLY_APPLICATION_GOAL = 5;

export interface WeekApplications {
  /** Tracker entries that reached at least APPLIED this Mon–Sun (UTC). */
  count: number;
  /** Goal for the week (WEEKLY_APPLICATION_GOAL). */
  goal: number;
  /** ISO date of this week's Monday (UTC). */
  weekStartISO: string;
  /** ISO date of this week's Sunday (UTC). */
  weekEndISO: string;
}

/** Monday 00:00 UTC of the week containing the given instant. */
export function weekStartMs(today: Date): number {
  const ms = utcTodayMs(today);
  // getUTCDay: 0 = Sunday … 6 = Saturday; Monday is 1.
  const dow = new Date(ms).getUTCDay();
  const daysSinceMonday = (dow + 6) % 7;
  return ms - daysSinceMonday * DAY_MS;
}

/**
 * Applications this week: tracker entries whose status reached at least
 * APPLIED (APPLIED, INTERVIEW, OFFER) and whose updatedAt falls in the
 * current Mon–Sun UTC week. SAVED and REJECTED never count. An entry
 * counts once even if it moved stages several times this week.
 */
export function applicationsThisWeek(
  tracker: TrackerDto[] | undefined,
  today: Date = new Date(),
): WeekApplications {
  const start = weekStartMs(today);
  const end = start + 7 * DAY_MS;
  const reached = new Set(["APPLIED", "INTERVIEW", "OFFER"]);
  let count = 0;
  for (const entry of tracker ?? []) {
    if (!reached.has(entry.status)) continue;
    const at = Date.parse(entry.updatedAt);
    if (Number.isNaN(at)) continue;
    if (at >= start && at < end) count++;
  }
  return {
    count,
    goal: WEEKLY_APPLICATION_GOAL,
    weekStartISO: isoForMs(start),
    weekEndISO: isoForMs(end - DAY_MS),
  };
}

// --- Pipeline funnel ----------------------------------------------------------

export interface PipelineFunnel {
  saved: number;
  applied: number;
  interview: number;
  offer: number;
  rejected: number;
  total: number;
  /** Items that reached at least APPLIED (APPLIED + INTERVIEW + OFFER). */
  reachedApplied: number;
  /** Items that reached at least INTERVIEW (INTERVIEW + OFFER). */
  reachedInterview: number;
  /** Items that reached OFFER. */
  reachedOffer: number;
  /** Share that reached at least APPLIED; null when there are no items. */
  appliedRate: number | null;
  /** Share of reached-APPLIED that reached INTERVIEW; null when none did. */
  interviewRate: number | null;
  /** Share of reached-INTERVIEW that reached OFFER; null when none did. */
  offerRate: number | null;
}

/**
 * Funnel counts from the real tracker stages. An item in INTERVIEW has
 * passed APPLIED, and an item in OFFER has passed INTERVIEW, so the
 * "reached" counts accumulate down the funnel. REJECTED is terminal and
 * is excluded from the reached counts, so the rate next to a stage never
 * contradicts that stage's count (an all-REJECTED tracker shows Applied
 * 0 at 0%, not 0 at 100%). Rates are null when the denominator is zero
 * — never 0% from nothing, never a divide-by-zero.
 */
export function pipelineFunnel(
  tracker: TrackerDto[] | undefined,
): PipelineFunnel {
  const list = tracker ?? [];
  const saved = list.filter((t) => t.status === "SAVED").length;
  const applied = list.filter((t) => t.status === "APPLIED").length;
  const interview = list.filter((t) => t.status === "INTERVIEW").length;
  const offer = list.filter((t) => t.status === "OFFER").length;
  const rejected = list.filter((t) => t.status === "REJECTED").length;
  const total = list.length;
  const reachedApplied = applied + interview + offer;
  const reachedInterview = interview + offer;
  const reachedOffer = offer;
  return {
    saved,
    applied,
    interview,
    offer,
    rejected,
    total,
    reachedApplied,
    reachedInterview,
    reachedOffer,
    appliedRate: total > 0 ? reachedApplied / total : null,
    interviewRate:
      reachedApplied > 0 ? reachedInterview / reachedApplied : null,
    offerRate: reachedInterview > 0 ? reachedOffer / reachedInterview : null,
  };
}

// --- Question of the day ------------------------------------------------------

/**
 * One interview question per day, rotated deterministically from the
 * real question bank: day-of-year (UTC) modulo the number of
 * questions. The same day always shows the same question; a new day
 * shows the next one. Null when the bank is empty — never a made-up
 * question.
 */
export function questionOfTheDay(
  questions: QuestionDto[],
  today: Date = new Date(),
): QuestionDto | null {
  if (questions.length === 0) return null;
  return questions[dayOfYear(today) % questions.length];
}

// --- Resume tip of the day ----------------------------------------------------

/**
 * One resume tip per day, rotated deterministically among the real
 * failing Analyzer checks for the user's resume: day-of-year (UTC)
 * modulo the number of failing checks. Null when the resume passes
 * every check or there is no analysis — never a generic tip.
 */
export function resumeTipOfTheDay(
  analysis: ResumeAnalysis | null | undefined,
  today: Date = new Date(),
): ResumeAnalysis["checks"][number] | null {
  const failing = (analysis?.checks ?? []).filter((c) => !c.passed);
  if (failing.length === 0) return null;
  return failing[dayOfYear(today) % failing.length];
}

// --- Daily check-in / streak --------------------------------------------------

/** localStorage key for the recorded daily check-in dates. */
export const DAILY_CHECKINS_KEY = "epitomejob:daily-checkins";

/** ISO date (YYYY-MM-DD) for the given instant's UTC calendar date. */
export function todayISO(today: Date = new Date()): string {
  return isoForMs(utcTodayMs(today));
}

function isValidISODate(value: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return false;
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  // Round-trip: Date.UTC rolls 2026-02-30 over to Mar 2, so the
  // components must match after construction for the date to be real.
  const d = new Date(Date.UTC(year, month - 1, day));
  return (
    d.getUTCFullYear() === year &&
    d.getUTCMonth() === month - 1 &&
    d.getUTCDate() === day
  );
}

/** Read the recorded check-in dates; empty when unavailable or corrupt. */
export function loadCheckinDates(): string[] {
  try {
    if (typeof window === "undefined" || !window.localStorage) return [];
    const raw = window.localStorage.getItem(DAILY_CHECKINS_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    const dates = parsed.filter(
      (v): v is string => typeof v === "string" && isValidISODate(v),
    );
    return [...new Set(dates)].sort();
  } catch {
    return [];
  }
}

/** Persist the check-in dates; best-effort, never throws. */
export function saveCheckinDates(dates: string[]): void {
  try {
    if (typeof window === "undefined" || !window.localStorage) return;
    window.localStorage.setItem(
      DAILY_CHECKINS_KEY,
      JSON.stringify([...new Set(dates)].sort()),
    );
  } catch {
    // localStorage full or unavailable — the streak still works in-memory.
  }
}

/** Record today's check-in (idempotent — same day twice is one entry). */
export function recordCheckin(today: Date = new Date()): string[] {
  const dates = loadCheckinDates();
  const iso = todayISO(today);
  if (!dates.includes(iso)) dates.push(iso);
  const sorted = [...new Set(dates)].sort();
  saveCheckinDates(sorted);
  return sorted;
}

/** True when the user already checked in today. */
export function checkedInToday(
  dates: string[],
  today: Date = new Date(),
): boolean {
  return dates.includes(todayISO(today));
}

export interface Streak {
  /** Consecutive days checked in, ending today or yesterday. */
  count: number;
  /** ISO date of the most recent check-in, or null when never. */
  lastCheckin: string | null;
  /** True when the streak is still alive (last check-in today or yesterday). */
  alive: boolean;
}

/**
 * Consecutive-day streak from the recorded check-ins. Counts back from
 * today when today is checked in, otherwise from yesterday when
 * yesterday is checked in (the streak is alive until the end of today).
 * Any gap breaks it: the count restarts at the most recent run. Never
 * counts a day that was not recorded.
 */
export function computeStreak(
  dates: string[],
  today: Date = new Date(),
): Streak {
  const set = new Set(dates);
  const todayMs = utcTodayMs(today);
  let cursor = todayMs;
  if (!set.has(isoForMs(cursor))) cursor -= DAY_MS;
  if (!set.has(isoForMs(cursor))) {
    const sorted = [...set].sort();
    return { count: 0, lastCheckin: sorted[sorted.length - 1] ?? null, alive: false };
  }
  // Capture the most recent check-in before counting back — the loop
  // leaves the cursor at the streak start, which is not the last date.
  const lastCheckin = isoForMs(cursor);
  let count = 0;
  while (set.has(isoForMs(cursor))) {
    count++;
    cursor -= DAY_MS;
  }
  return { count, lastCheckin, alive: true };
}
