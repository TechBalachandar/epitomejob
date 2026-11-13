// W5 H-1B clock math (contract §8): all four states + the 14/0/-1
// boundaries. Dates are fixed UTC calendar dates so the results do not
// depend on the machine's timezone.
import { describe, expect, it } from "vitest";
import { clockStatus, formatDeadline } from "@/lib/h1b-clock";

// "today" is fixed at UTC midnight 2026-11-13 so every case is exact.
const TODAY = new Date(Date.UTC(2026, 10, 13));

describe("clockStatus", () => {
  it("not-set when the start date is null", () => {
    expect(clockStatus(null, TODAY)).toEqual({
      state: "not-set",
      daysLeft: null,
      deadline: null,
    });
    expect(clockStatus(undefined, TODAY).state).toBe("not-set");
  });

  it("active when more than 14 days are left", () => {
    // Started 2026-10-15: 29 whole days since → 31 left.
    const s = clockStatus("2026-10-15", TODAY);
    expect(s.state).toBe("active");
    expect(s.daysLeft).toBe(31);
    expect(s.deadline?.toISOString()).toBe("2026-12-14T00:00:00.000Z");
  });

  it("urgent at exactly 14 days left (boundary)", () => {
    // 46 days since 2026-09-28 → 14 left.
    const s = clockStatus("2026-09-28", TODAY);
    expect(s.state).toBe("urgent");
    expect(s.daysLeft).toBe(14);
  });

  it("urgent at 1 day left and at 0 (last day)", () => {
    expect(clockStatus("2026-09-15", TODAY)).toMatchObject({
      state: "urgent",
      daysLeft: 1,
    });
    expect(clockStatus("2026-09-14", TODAY)).toMatchObject({
      state: "urgent",
      daysLeft: 0,
    });
  });

  it("expired at -1 days left (boundary) and beyond", () => {
    expect(clockStatus("2026-09-13", TODAY)).toMatchObject({
      state: "expired",
      daysLeft: -1,
    });
    expect(clockStatus("2026-09-01", TODAY)).toMatchObject({
      state: "expired",
      daysLeft: -13,
    });
  });

  it("deadline is start + 60 calendar days", () => {
    const s = clockStatus("2026-10-15", TODAY);
    expect(s.deadline?.toISOString()).toBe("2026-12-14T00:00:00.000Z");
    expect(formatDeadline(s.deadline!)).toBe("Dec 14, 2026");
  });

  it("counts whole calendar days at UTC midnight, never local time", () => {
    // Today 2026-11-13 23:30 UTC is still the same UTC calendar day as
    // 00:30 — daysSince must not move with the time of day.
    const late = new Date(Date.UTC(2026, 10, 13, 23, 30));
    const early = new Date(Date.UTC(2026, 10, 13, 0, 30));
    expect(clockStatus("2026-10-15", late).daysLeft).toBe(31);
    expect(clockStatus("2026-10-15", early).daysLeft).toBe(31);
    // Start date is a calendar date: the day it starts is day 0 (60 left).
    expect(clockStatus("2026-11-13", TODAY).daysLeft).toBe(60);
  });

  it("is unaffected by the DST change inside the window", () => {
    // US DST ended Nov 1, 2026 — inside this window. UTC-midnight math
    // must still give whole days: Oct 20 → Nov 13 is 24 days → 36 left.
    expect(clockStatus("2026-10-20", TODAY).daysLeft).toBe(36);
  });
});
