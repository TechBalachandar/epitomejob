// H-1B 60-day clock math (W5-API-CONTRACT §8, frozen).
// daysLeft = 60 - whole calendar days since the clock start date.
// All day math is done at UTC midnight (Date.UTC on year/month/day) —
// never local-time arithmetic, never fractional days — so the result is
// the same in every timezone and across DST changes.

export type ClockState = "not-set" | "active" | "urgent" | "expired";

export interface ClockStatus {
  state: ClockState;
  daysLeft: number | null;
  deadline: Date | null;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Parse an ISO date ("YYYY-MM-DD") to its UTC-midnight timestamp. */
function utcMidnightMs(isoDate: string): number {
  const [year, month, day] = isoDate.split("-").map(Number);
  return Date.UTC(year, month - 1, day);
}

/** UTC-midnight timestamp for the given instant's UTC calendar date. */
function utcTodayMs(today: Date): number {
  return Date.UTC(
    today.getUTCFullYear(),
    today.getUTCMonth(),
    today.getUTCDate(),
  );
}

export function clockStatus(
  startISO: string | null | undefined,
  today: Date = new Date(),
): ClockStatus {
  if (!startISO) {
    return { state: "not-set", daysLeft: null, deadline: null };
  }
  const startMs = utcMidnightMs(startISO);
  const daysSince = Math.floor((utcTodayMs(today) - startMs) / DAY_MS);
  const daysLeft = 60 - daysSince;
  const deadline = new Date(startMs + 60 * DAY_MS);
  const state: ClockState =
    daysLeft < 0 ? "expired" : daysLeft <= 14 ? "urgent" : "active";
  return { state, daysLeft, deadline };
}

/** Format a deadline Date as a short calendar date (UTC, to match the math). */
export function formatDeadline(deadline: Date): string {
  return deadline.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}
