// Home daily logic — 360° coverage: positive, negative, edge.
// Every rule is exercised: new-jobs-today, applications this week,
// pipeline funnel, question/tip of the day, and the check-in streak
// (including midnight rollover and a broken streak).
import { beforeEach, describe, expect, it } from "vitest";
import {
  applicationsThisWeek,
  checkedInToday,
  computeStreak,
  DAILY_CHECKINS_KEY,
  dayOfYear,
  isPostedToday,
  loadCheckinDates,
  newSponsorJobsToday,
  pipelineFunnel,
  questionOfTheDay,
  recordCheckin,
  resumeTipOfTheDay,
  todayISO,
  WEEKLY_APPLICATION_GOAL,
} from "@/lib/home-daily";
import { sampleBuilderState, analyzeResume } from "@/lib/resume-builder";
import type {
  Job,
  Match,
  Profile,
  QuestionDto,
  TrackerDto,
} from "@/lib/types";

// Fixed "today": Wednesday 2026-11-18 (UTC). All dates derive from it so
// the week/month boundaries are exact and the tests never depend on the
// real clock.
const TODAY = new Date("2026-11-18T15:30:00Z"); // Wednesday

function job(overrides: Partial<Job> & { id: string }): Job {
  return {
    title: "Senior Java Engineer",
    company: "Acme",
    location: "Reston, VA",
    workType: "Hybrid",
    salaryMin: 120000,
    salaryMax: 150000,
    currency: "USD",
    sponsorsH1b: true,
    skills: ["Java", "Spring Boot", "Kafka"],
    summary: "Build services.",
    role: "Backend",
    postedAt: "2026-11-18T08:00:00Z",
    source: "SEED",
    sourceUrl: null,
    ...overrides,
  };
}

function match(jobObj: Job, score: number): Match {
  return { job: jobObj, score, reasons: [], gaps: [], source: "RESUME" };
}

function profile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: "p1",
    userId: "u1",
    headline: "Senior Java Engineer",
    summary: "",
    location: "Reston, VA",
    workType: "Hybrid",
    salaryMin: null,
    salaryMax: null,
    currency: "USD",
    visibility: "PUBLIC",
    skills: ["Java", "Spring Boot"],
    h1bClockStartDate: "2026-11-01",
    linkedinUrl: null,
    workAuthorization: "H1B",
    completeness: 80,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
    ...overrides,
  };
}

function trackerEntry(
  overrides: Partial<TrackerDto> & { id: string },
): TrackerDto {
  return {
    jobId: "job-1",
    title: "Senior Java Engineer",
    company: "Acme",
    resumeId: null,
    status: "SAVED",
    notes: null,
    createdAt: "2026-11-16T00:00:00Z",
    updatedAt: "2026-11-18T10:00:00Z",
    ...overrides,
  };
}

function question(id: string, text: string): QuestionDto {
  return { id, text, category: "Behavioral", role: "Backend" };
}

describe("isPostedToday", () => {
  it("posted this morning (UTC) is today", () => {
    expect(isPostedToday("2026-11-18T08:00:00Z", TODAY)).toBe(true);
  });
  it("posted yesterday is not today", () => {
    expect(isPostedToday("2026-11-17T23:59:00Z", TODAY)).toBe(false);
  });
  it("posted tomorrow is not today", () => {
    expect(isPostedToday("2026-11-19T00:00:00Z", TODAY)).toBe(false);
  });
  it("an invalid date is never today", () => {
    expect(isPostedToday("not-a-date", TODAY)).toBe(false);
    expect(isPostedToday("", TODAY)).toBe(false);
  });
  it("midnight boundary: 00:00 UTC today counts, 23:59 yesterday does not", () => {
    expect(isPostedToday("2026-11-18T00:00:00Z", TODAY)).toBe(true);
    expect(isPostedToday("2026-11-17T23:59:59Z", TODAY)).toBe(false);
  });
});

describe("newSponsorJobsToday", () => {
  const prof = profile();

  it("a sponsor job posted today that matches the profile is new", () => {
    const out = newSponsorJobsToday([job({ id: "j1" })], [], prof, new Set(), TODAY);
    expect(out).toHaveLength(1);
    expect(out[0].job.id).toBe("j1");
    expect(out[0].via).toBe("skills");
  });
  it("a backend-scored sponsor match posted today comes first, best score first", () => {
    const j1 = job({ id: "j1", title: "A" });
    const j2 = job({ id: "j2", title: "B" });
    const out = newSponsorJobsToday(
      [j1, j2],
      [match(j2, 70), match(j1, 90)],
      prof,
      new Set(),
      TODAY,
    );
    expect(out.map((o) => o.job.id)).toEqual(["j1", "j2"]);
    expect(out[0].via).toBe("match");
    expect(out[0].score).toBe(90);
  });
  it("a job posted yesterday is never new today (stale is not relabelled)", () => {
    const out = newSponsorJobsToday(
      [job({ id: "j1", postedAt: "2026-11-17T08:00:00Z" })],
      [],
      prof,
      new Set(),
      TODAY,
    );
    expect(out).toHaveLength(0);
  });
  it("a non-sponsor job posted today is not new", () => {
    const out = newSponsorJobsToday(
      [job({ id: "j1", sponsorsH1b: false })],
      [],
      prof,
      new Set(),
      TODAY,
    );
    expect(out).toHaveLength(0);
  });
  it("a sponsor job that does not overlap the profile is not new", () => {
    const out = newSponsorJobsToday(
      [job({ id: "j1", title: "Pastry Chef", company: "Bakery", skills: ["Baking"], location: "Paris, FR" })],
      [],
      prof,
      new Set(),
      TODAY,
    );
    expect(out).toHaveLength(0);
  });
  it("a seen job never repeats", () => {
    const out = newSponsorJobsToday([job({ id: "j1" })], [], prof, new Set(["j1"]), TODAY);
    expect(out).toHaveLength(0);
  });
  it("a backend match is new even when the profile overlap would miss it", () => {
    const j = job({ id: "j1", title: "Pastry Chef", skills: ["Baking"] });
    const out = newSponsorJobsToday([j], [match(j, 88)], prof, new Set(), TODAY);
    expect(out).toHaveLength(1);
    expect(out[0].via).toBe("match");
  });
  it("caps at 5 jobs", () => {
    const jobs = Array.from({ length: 8 }, (_, i) =>
      job({ id: `j${i}`, title: `Job ${i}` }),
    );
    const out = newSponsorJobsToday(jobs, [], prof, new Set(), TODAY);
    expect(out).toHaveLength(5);
  });
  it("empty feed and no profile give an empty list, not an error", () => {
    expect(newSponsorJobsToday([], [], null, new Set(), TODAY)).toHaveLength(0);
    expect(newSponsorJobsToday([], [], undefined, new Set(), TODAY)).toHaveLength(0);
  });
});

describe("applicationsThisWeek", () => {
  it("counts APPLIED / INTERVIEW / OFFER updated this Mon–Sun week", () => {
    const entries = [
      trackerEntry({ id: "t1", status: "APPLIED", updatedAt: "2026-11-16T09:00:00Z" }), // Monday
      trackerEntry({ id: "t2", status: "INTERVIEW", updatedAt: "2026-11-18T09:00:00Z" }), // Wednesday
      trackerEntry({ id: "t3", status: "OFFER", updatedAt: "2026-11-17T09:00:00Z" }),
    ];
    const week = applicationsThisWeek(entries, TODAY);
    expect(week.count).toBe(3);
    expect(week.goal).toBe(WEEKLY_APPLICATION_GOAL);
    expect(week.weekStartISO).toBe("2026-11-16");
    expect(week.weekEndISO).toBe("2026-11-22");
  });
  it("SAVED and REJECTED never count", () => {
    const entries = [
      trackerEntry({ id: "t1", status: "SAVED", updatedAt: "2026-11-18T09:00:00Z" }),
      trackerEntry({ id: "t2", status: "REJECTED", updatedAt: "2026-11-18T09:00:00Z" }),
    ];
    expect(applicationsThisWeek(entries, TODAY).count).toBe(0);
  });
  it("an application from last week does not count", () => {
    const entries = [
      trackerEntry({ id: "t1", status: "APPLIED", updatedAt: "2026-11-15T23:59:00Z" }), // Sunday, previous week
    ];
    expect(applicationsThisWeek(entries, TODAY).count).toBe(0);
  });
  it("Monday 00:00 UTC counts, the previous Sunday 23:59 does not", () => {
    const monday = applicationsThisWeek(
      [trackerEntry({ id: "t1", status: "APPLIED", updatedAt: "2026-11-16T00:00:00Z" })],
      TODAY,
    );
    const sunday = applicationsThisWeek(
      [trackerEntry({ id: "t1", status: "APPLIED", updatedAt: "2026-11-15T23:59:59Z" })],
      TODAY,
    );
    expect(monday.count).toBe(1);
    expect(sunday.count).toBe(0);
  });
  it("an entry with an invalid updatedAt is skipped, not counted", () => {
    const entries = [trackerEntry({ id: "t1", status: "APPLIED", updatedAt: "garbage" })];
    expect(applicationsThisWeek(entries, TODAY).count).toBe(0);
  });
  it("empty tracker gives 0 against the goal", () => {
    const week = applicationsThisWeek([], TODAY);
    expect(week.count).toBe(0);
    expect(week.goal).toBe(5);
  });
  it("undefined tracker gives 0, not an error", () => {
    expect(applicationsThisWeek(undefined, TODAY).count).toBe(0);
  });
});

describe("pipelineFunnel", () => {
  it("counts every stage and totals them", () => {
    const entries = [
      trackerEntry({ id: "t1", status: "SAVED" }),
      trackerEntry({ id: "t2", status: "APPLIED" }),
      trackerEntry({ id: "t3", status: "INTERVIEW" }),
      trackerEntry({ id: "t4", status: "OFFER" }),
      trackerEntry({ id: "t5", status: "REJECTED" }),
    ];
    const f = pipelineFunnel(entries);
    expect(f).toMatchObject({ saved: 1, applied: 1, interview: 1, offer: 1, rejected: 1, total: 5 });
  });
  it("rates accumulate down the funnel (INTERVIEW counts as reached APPLIED)", () => {
    const entries = [
      trackerEntry({ id: "t1", status: "SAVED" }),
      trackerEntry({ id: "t2", status: "APPLIED" }),
      trackerEntry({ id: "t3", status: "INTERVIEW" }),
      trackerEntry({ id: "t4", status: "OFFER" }),
    ];
    const f = pipelineFunnel(entries);
    expect(f.appliedRate).toBeCloseTo(3 / 4);
    expect(f.interviewRate).toBeCloseTo(2 / 3);
    expect(f.offerRate).toBeCloseTo(1 / 2);
  });
  it("rates are null when the denominator is zero — never 0% from nothing", () => {
    const f = pipelineFunnel([]);
    expect(f.appliedRate).toBeNull();
    expect(f.interviewRate).toBeNull();
    expect(f.offerRate).toBeNull();
    const onlySaved = pipelineFunnel([trackerEntry({ id: "t1", status: "SAVED" })]);
    expect(onlySaved.appliedRate).toBe(0);
    expect(onlySaved.interviewRate).toBeNull();
    expect(onlySaved.offerRate).toBeNull();
  });
  it("M3: an all-REJECTED tracker shows Applied 0 at 0% — REJECTED is not counted as applied", () => {
    const f = pipelineFunnel([
      trackerEntry({ id: "t1", status: "REJECTED" }),
      trackerEntry({ id: "t2", status: "REJECTED" }),
    ]);
    expect(f.appliedRate).toBe(0);
    expect(f.interviewRate).toBeNull();
    expect(f.offerRate).toBeNull();
  });
  it("M3: REJECTED does not inflate the applied rate in a mixed funnel", () => {
    const f = pipelineFunnel([
      trackerEntry({ id: "t1", status: "APPLIED" }),
      trackerEntry({ id: "t2", status: "REJECTED" }),
    ]);
    expect(f.appliedRate).toBeCloseTo(1 / 2);
    // 1 item reached APPLIED, 0 of them reached INTERVIEW → 0, not null.
    expect(f.interviewRate).toBe(0);
    expect(f.offerRate).toBeNull();
  });
  it("Architect F1: INTERVIEW-only exposes reached counts — the Applied row renders 1 at 100%, never 0 at 100%", () => {
    const f = pipelineFunnel([trackerEntry({ id: "t1", status: "INTERVIEW" })]);
    // Exact-stage counts: nothing is still sitting at APPLIED.
    expect(f.applied).toBe(0);
    // Reached counts (what the funnel rows render): the item did reach APPLIED.
    expect(f.reachedApplied).toBe(1);
    expect(f.reachedInterview).toBe(1);
    expect(f.reachedOffer).toBe(0);
    expect(f.appliedRate).toBe(1);
    expect(f.interviewRate).toBe(1);
    expect(f.offerRate).toBe(0);
  });
  it("Architect F1: OFFER-only exposes reached counts — Applied and Interviewing rows render 1 at 100%", () => {
    const f = pipelineFunnel([trackerEntry({ id: "t1", status: "OFFER" })]);
    expect(f.applied).toBe(0);
    expect(f.interview).toBe(0);
    expect(f.reachedApplied).toBe(1);
    expect(f.reachedInterview).toBe(1);
    expect(f.reachedOffer).toBe(1);
    expect(f.appliedRate).toBe(1);
    expect(f.interviewRate).toBe(1);
    expect(f.offerRate).toBe(1);
  });
  it("Architect F1: a rendered row count of 0 never sits beside a 100% rate", () => {
    const cases: Array<Array<"SAVED" | "APPLIED" | "INTERVIEW" | "OFFER" | "REJECTED">> = [
      ["INTERVIEW"],
      ["OFFER"],
      ["APPLIED", "INTERVIEW"],
      ["REJECTED"],
      ["SAVED"],
      [],
    ];
    for (const statuses of cases) {
      const f = pipelineFunnel(
        statuses.map((status, i) => trackerEntry({ id: `t${i}`, status })),
      );
      const rows: Array<[number, number | null]> = [
        [f.reachedApplied, f.appliedRate],
        [f.reachedInterview, f.interviewRate],
        [f.reachedOffer, f.offerRate],
      ];
      for (const [count, rate] of rows) {
        if (count === 0) {
          expect(rate).not.toBe(1);
        }
      }
    }
  });
  it("undefined tracker is an empty funnel", () => {
    expect(pipelineFunnel(undefined).total).toBe(0);
  });
});

describe("questionOfTheDay", () => {
  const bank = [
    question("q1", "Tell me about yourself."),
    question("q2", "Why this role?"),
    question("q3", "Describe a hard bug you fixed."),
  ];
  it("returns one question from the bank", () => {
    const q = questionOfTheDay(bank, TODAY);
    expect(q).not.toBeNull();
    expect(bank).toContain(q);
  });
  it("the same day always shows the same question", () => {
    expect(questionOfTheDay(bank, TODAY)).toEqual(
      questionOfTheDay(bank, new Date("2026-11-18T23:59:00Z")),
    );
  });
  it("consecutive days rotate (with 3 questions, day 1 and day 2 differ)", () => {
    const d1 = questionOfTheDay(bank, new Date("2026-11-18T12:00:00Z"));
    const d2 = questionOfTheDay(bank, new Date("2026-11-19T12:00:00Z"));
    expect(d1).not.toEqual(d2);
  });
  it("a single-question bank always returns that question", () => {
    const only = [question("q1", "Only one.")];
    expect(questionOfTheDay(only, TODAY)).toEqual(only[0]);
    expect(questionOfTheDay(only, new Date("2026-12-25T12:00:00Z"))).toEqual(only[0]);
  });
  it("an empty bank gives null — never a made-up question", () => {
    expect(questionOfTheDay([], TODAY)).toBeNull();
  });
  it("day-of-year wraps at year end (Dec 31 and Jan 1 can share the rotation)", () => {
    expect(dayOfYear(new Date("2026-01-01T12:00:00Z"))).toBe(0);
    expect(dayOfYear(new Date("2026-12-31T12:00:00Z"))).toBe(364);
  });
});

describe("resumeTipOfTheDay", () => {
  it("returns a failing check from the real analysis", () => {
    const state = sampleBuilderState();
    state.skills = []; // fail the skills check
    state.summary = ""; // fail the summary check
    const analysis = analyzeResume(state);
    const tip = resumeTipOfTheDay(analysis, TODAY);
    expect(tip).not.toBeNull();
    expect(tip!.passed).toBe(false);
  });
  it("the tip rotates across days when several checks fail", () => {
    const state = sampleBuilderState();
    state.skills = [];
    state.summary = "";
    state.experience = [];
    const analysis = analyzeResume(state);
    const tips = new Set(
      [0, 1, 2, 3, 4, 5].map(
        (d) =>
          resumeTipOfTheDay(
            analysis,
            new Date(`2026-11-${18 + d}T12:00:00Z`),
          )?.id,
      ),
    );
    expect(tips.size).toBeGreaterThan(1);
  });
  it("a resume that passes everything gives null — no generic tip", () => {
    const analysis = analyzeResume(sampleBuilderState());
    // sampleBuilderState passes the structure checks; measurable may or
    // may not pass — assert the null case via a fully-passing analysis.
    const passing = {
      score: 100,
      checks: analysis.checks.map((c) => ({ ...c, passed: true })),
      weakest: null,
    };
    expect(resumeTipOfTheDay(passing, TODAY)).toBeNull();
  });
  it("null analysis gives null", () => {
    expect(resumeTipOfTheDay(null, TODAY)).toBeNull();
    expect(resumeTipOfTheDay(undefined, TODAY)).toBeNull();
  });
});

describe("daily check-in / streak (localStorage)", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("no check-ins means no streak and not checked in", () => {
    expect(loadCheckinDates()).toEqual([]);
    expect(checkedInToday([], TODAY)).toBe(false);
    expect(computeStreak([], TODAY)).toEqual({
      count: 0,
      lastCheckin: null,
      alive: false,
    });
  });

  it("recording today gives a 1-day streak and checked-in state", () => {
    const dates = recordCheckin(TODAY);
    expect(dates).toEqual(["2026-11-18"]);
    expect(checkedInToday(dates, TODAY)).toBe(true);
    expect(computeStreak(dates, TODAY).count).toBe(1);
    expect(window.localStorage.getItem(DAILY_CHECKINS_KEY)).toContain("2026-11-18");
  });

  it("recording twice on the same day is one entry (idempotent)", () => {
    recordCheckin(TODAY);
    const dates = recordCheckin(TODAY);
    expect(dates).toEqual(["2026-11-18"]);
  });

  it("three consecutive days give a 3-day streak", () => {
    const dates = ["2026-11-16", "2026-11-17", "2026-11-18"];
    expect(computeStreak(dates, TODAY).count).toBe(3);
    expect(computeStreak(dates, TODAY).alive).toBe(true);
  });

  it("M1: lastCheckin is the most recent check-in, not the streak start", () => {
    const s = computeStreak(["2026-11-16", "2026-11-17", "2026-11-18"], TODAY);
    expect(s).toEqual({ count: 3, lastCheckin: "2026-11-18", alive: true });
    const y = computeStreak(["2026-11-16", "2026-11-17"], TODAY);
    expect(y.lastCheckin).toBe("2026-11-17");
  });

  it("a gap breaks the streak — only the most recent run counts", () => {
    const dates = ["2026-11-10", "2026-11-11", "2026-11-14", "2026-11-15"];
    // Today is Nov 18; last check-in Nov 15, so the streak is dead.
    const s = computeStreak(dates, TODAY);
    expect(s.count).toBe(0);
    expect(s.alive).toBe(false);
    expect(s.lastCheckin).toBe("2026-11-15");
  });

  it("yesterday only keeps the streak alive until the end of today", () => {
    const dates = ["2026-11-16", "2026-11-17"];
    const s = computeStreak(dates, TODAY);
    expect(s.count).toBe(2);
    expect(s.alive).toBe(true);
    expect(checkedInToday(dates, TODAY)).toBe(false);
  });

  it("midnight rollover: a check-in recorded late yesterday still counts today", () => {
    const dates = recordCheckin(new Date("2026-11-17T23:59:00Z"));
    expect(dates).toEqual(["2026-11-17"]);
    expect(computeStreak(dates, TODAY).count).toBe(1);
  });

  it("corrupt or non-ISO entries in storage are ignored, not fatal", () => {
    window.localStorage.setItem(
      DAILY_CHECKINS_KEY,
      JSON.stringify(["2026-11-18", "garbage", 42, null, "2026-13-40"]),
    );
    expect(loadCheckinDates()).toEqual(["2026-11-18"]);
  });

  it("M2: impossible dates (2026-02-30) are ignored — Date.parse rolls them over", () => {
    window.localStorage.setItem(
      DAILY_CHECKINS_KEY,
      JSON.stringify(["2026-02-30", "2026-11-18"]),
    );
    expect(loadCheckinDates()).toEqual(["2026-11-18"]);
  });

  it("a non-array value in storage loads as empty", () => {
    window.localStorage.setItem(DAILY_CHECKINS_KEY, JSON.stringify({ a: 1 }));
    expect(loadCheckinDates()).toEqual([]);
  });

  it("todayISO uses the UTC calendar date", () => {
    expect(todayISO(new Date("2026-11-18T23:30:00Z"))).toBe("2026-11-18");
    expect(todayISO(new Date("2026-11-19T00:30:00Z"))).toBe("2026-11-19");
  });
});
