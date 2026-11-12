// Pure helpers for the W2 jobs/matches/interviews pages. Kept free of
// React so they are easy to unit-test.
import type { MatchSource } from "./types";

/** "$145k – $175k" */
export function formatSalary(min: number, max: number): string {
  const k = (n: number) => `$${Math.round(n / 1000)}k`;
  return `${k(min)} – ${k(max)}`;
}

/** "Posted 2d ago" / "Posted today" / "Posted Nov 10, 2026". */
export function formatPostedAt(postedAt: string, now = new Date()): string {
  const posted = new Date(postedAt);
  if (Number.isNaN(posted.getTime())) return "Posted recently";
  const days = Math.floor((now.getTime() - posted.getTime()) / 86_400_000);
  if (days <= 0) return "Posted today";
  if (days === 1) return "Posted 1d ago";
  if (days < 30) return `Posted ${days}d ago`;
  return `Posted ${posted.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })}`;
}

/** Match source badge text (contract §3). */
export function matchSourceLabel(source: MatchSource): string {
  return source === "RESUME"
    ? "Matched on your resume"
    : "Matched on your profile — no resume yet";
}

/** Query string for GET /jobs from the jobs-page filters. */
export function buildJobsQuery(filters: {
  query?: string;
  location?: string;
  workType?: string;
  sponsorsH1b?: boolean;
  page?: number;
  size?: number;
}): string {
  const params = new URLSearchParams();
  if (filters.query?.trim()) params.set("query", filters.query.trim());
  if (filters.location?.trim()) params.set("location", filters.location.trim());
  if (filters.workType && filters.workType !== "ALL") params.set("workType", filters.workType);
  if (filters.sponsorsH1b) params.set("sponsorsH1b", "true");
  if (filters.page !== undefined) params.set("page", String(filters.page));
  if (filters.size !== undefined) params.set("size", String(filters.size));
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}
