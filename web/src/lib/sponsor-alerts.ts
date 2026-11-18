// Feature 1 + 6 shared rule: when does a sponsor job "match the profile"?
// No new scorer is invented here. A job matches when EITHER the backend
// already scored it (a Match exists for it — the backend score is reused
// as-is) OR it overlaps the profile on skills / location / headline, using
// the existing whole-token keyword check (containsKeyword) from the
// resume-builder keyword logic, so "java" never matches "JavaScript".
// A job only ever becomes an alert when it matches AND sponsorsH1b.
import type { Job, Match, Profile } from "./types";
import { containsKeyword, keywordsFromJobDescription } from "./resume-builder";

export type MatchVia = "match" | "skills" | "location" | "headline";

export interface ProfileOverlap {
  via: Exclude<MatchVia, "match">;
  detail: string;
}

/** The alert rule, shown verbatim in the UI so it is never a black box. */
export const SPONSOR_ALERT_RULE =
  "A job alerts when it sponsors H-1B visas and matches your profile — " +
  "either the backend already scored it as a match, or one of your skills " +
  "appears in the job (whole-word match), your location overlaps the job's " +
  "location (remote jobs count for everyone), or a headline keyword appears " +
  "in the job title — and you have not seen it before.";

/** localStorage key for the seen-set of sponsor job ids (namespaced). */
export const SEEN_SPONSOR_JOBS_KEY = "epitomejob:seen-sponsor-jobs";

/** Read the seen-set from localStorage; empty when unavailable. */
export function loadSeenJobIds(): Set<string> {
  try {
    if (typeof window === "undefined" || !window.localStorage) return new Set();
    const raw = window.localStorage.getItem(SEEN_SPONSOR_JOBS_KEY);
    if (!raw) return new Set();
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((v): v is string => typeof v === "string"));
  } catch {
    return new Set();
  }
}

/** Persist the seen-set to localStorage; best-effort, never throws. */
export function saveSeenJobIds(ids: Set<string>): void {
  try {
    if (typeof window === "undefined" || !window.localStorage) return;
    window.localStorage.setItem(
      SEEN_SPONSOR_JOBS_KEY,
      JSON.stringify([...ids]),
    );
  } catch {
    // localStorage full or unavailable — alerts still work in-memory.
  }
}

/** Add ids to the seen-set and persist it. Returns the new set. */
export function markJobsSeen(ids: string[]): Set<string> {
  const seen = loadSeenJobIds();
  for (const id of ids) seen.add(id);
  saveSeenJobIds(seen);
  return seen;
}

function normalize(value: string): string {
  return value.trim().toLowerCase();
}

/**
 * Profile overlap without a backend Match: skills, location, then
 * headline, in that order. Returns null when nothing overlaps.
 */
export function profileOverlapsJob(
  job: Job,
  profile: Profile | null | undefined,
): ProfileOverlap | null {
  if (!profile) return null;
  const jobText = `${job.title} ${job.company} ${job.skills.join(" ")}`;
  for (const skill of profile.skills ?? []) {
    if (skill.trim() && containsKeyword(jobText, skill)) {
      return { via: "skills", detail: `Your skill "${skill}" appears in this job` };
    }
  }
  const profileLocation = normalize(profile.location ?? "");
  const jobLocation = normalize(job.location ?? "");
  if (jobLocation === "remote") {
    return { via: "location", detail: "Remote job — location fits anywhere" };
  }
  if (
    profileLocation &&
    jobLocation &&
    (jobLocation.includes(profileLocation) ||
      profileLocation.includes(jobLocation))
  ) {
    return {
      via: "location",
      detail: `Location overlaps yours (${job.location})`,
    };
  }
  const headlineKeywords = keywordsFromJobDescription(
    profile.headline ?? "",
    10,
  );
  for (const keyword of headlineKeywords) {
    if (containsKeyword(job.title, keyword)) {
      return {
        via: "headline",
        detail: `Your headline keyword "${keyword}" is in the job title`,
      };
    }
  }
  return null;
}

/**
 * The full alert rule for one job: sponsorsH1b AND (backend Match OR
 * profile overlap). Returns the reason, or null when it does not qualify.
 */
export function sponsorAlertReason(
  job: Job,
  profile: Profile | null | undefined,
  match?: Match | null,
): { via: MatchVia; score: number | null; reason: string } | null {
  if (!job.sponsorsH1b) return null;
  if (match) {
    return {
      via: "match",
      score: match.score,
      reason: `Backend match ${match.score}%`,
    };
  }
  const overlap = profileOverlapsJob(job, profile);
  if (!overlap) return null;
  return { via: overlap.via, score: null, reason: overlap.detail };
}

export interface SponsorAlert {
  job: Job;
  via: MatchVia;
  score: number | null;
  reason: string;
}

/**
 * Find sponsor jobs in the live feed that qualify under the rule and have
 * not been seen before. Only jobs returned by the feed are ever returned —
 * nothing is invented. `matches` are the backend-scored matches (used as
 * the "match" reason); jobs without a Match fall back to profile overlap.
 */
export function findNewSponsorJobs(
  jobs: Job[],
  profile: Profile | null | undefined,
  matches: Match[] = [],
  seen: Set<string> = new Set(),
): SponsorAlert[] {
  const matchByJobId = new Map(matches.map((m) => [m.job.id, m]));
  const alerts: SponsorAlert[] = [];
  for (const job of jobs) {
    if (seen.has(job.id)) continue;
    const reason = sponsorAlertReason(job, profile, matchByJobId.get(job.id));
    if (reason) alerts.push({ job, ...reason });
  }
  // Backend-scored matches first, then by score where there is one.
  alerts.sort((a, b) => (b.score ?? -1) - (a.score ?? -1));
  return alerts;
}
