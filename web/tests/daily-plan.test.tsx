// Feature 1: daily plan is derived from real data — matches, the live
// jobs feed, builder documents, and the frozen clock math. No hardcoded
// jobs, resumes, or scores anywhere in the plan.
import { describe, expect, it } from "vitest";
import {
  buildDailyPlan,
  missingSkillsForDocument,
  resumesToTailor,
  sponsorJobsToApply,
} from "@/lib/daily-plan";
import { sampleBuilderState } from "@/lib/resume-builder";
import type { Job, Match, Profile, ResumeBuilderDocument } from "@/lib/types";

// "today" fixed at UTC midnight 2026-11-17 so day math is exact.
const TODAY = new Date(Date.UTC(2026, 10, 17));

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
    postedAt: "2026-11-10T00:00:00Z",
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
    visibility: "PRIVATE",
    skills: ["Java", "Spring Boot"],
    h1bClockStartDate: "2026-10-18",
    linkedinUrl: null,
    workAuthorization: "H1B",
    completeness: 80,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function doc(id: string, title: string): ResumeBuilderDocument {
  return {
    id,
    templateId: "modern",
    title,
    state: sampleBuilderState(),
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  };
}

describe("sponsorJobsToApply", () => {
  it("ranks sponsor matches by backend score first", () => {
    const low = job({ id: "j-low", title: "Java Engineer" });
    const high = job({ id: "j-high", title: "Staff Java Engineer" });
    const out = sponsorJobsToApply(
      [],
      [match(low, 62), match(high, 91)],
      profile(),
    );
    expect(out.map((e) => e.job.id)).toEqual(["j-high", "j-low"]);
    expect(out[0].score).toBe(91);
    expect(out[0].via).toBe("match");
  });

  it("excludes non-sponsor matches even with a high score", () => {
    const noSponsor = job({ id: "j-no", sponsorsH1b: false });
    const out = sponsorJobsToApply([], [match(noSponsor, 99)], profile());
    expect(out).toEqual([]);
  });

  it("includes an unmatched sponsor feed job that overlaps profile skills", () => {
    const feed = job({ id: "j-feed", title: "Kafka Engineer" });
    const out = sponsorJobsToApply([feed], [], profile());
    expect(out.map((e) => e.job.id)).toEqual(["j-feed"]);
    expect(out[0].score).toBeNull();
    expect(out[0].via).toBe("skills");
  });

  it("excludes a sponsor feed job with no profile overlap", () => {
    const feed = job({
      id: "j-far",
      title: "Ruby on Rails Developer",
      company: "Elsewhere",
      location: "Austin, TX",
      skills: ["Ruby", "Rails"],
    });
    const out = sponsorJobsToApply(
      [feed],
      [],
      profile({ headline: "Senior Java Engineer", skills: ["Java"] }),
    );
    expect(out).toEqual([]);
  });

  it("caps at 5 sponsor jobs", () => {
    const matches = Array.from({ length: 7 }, (_, i) =>
      match(job({ id: `j-${i}` }), 90 - i),
    );
    expect(sponsorJobsToApply([], matches, profile())).toHaveLength(5);
  });
});

describe("missingSkillsForDocument", () => {
  it("whole-token match: JavaScript does not cover Java", () => {
    const d = doc("d1", "Resume");
    d.state.skills = ["JavaScript", "React"];
    d.state.summary = "Frontend engineer working in JavaScript and React.";
    const missing = missingSkillsForDocument(
      d,
      job({ id: "j1", skills: ["Java", "Terraform"] }),
    );
    expect(missing).toEqual(["Java", "Terraform"]);
    // …while the sample resume's experience text does cover Spring Boot.
    expect(
      missingSkillsForDocument(d, job({ id: "j1", skills: ["Spring Boot"] })),
    ).toEqual([]);
  });

  it("returns empty when the resume covers every job skill", () => {
    const d = doc("d1", "Resume");
    d.state.skills = ["Java", "Spring Boot", "Kafka"];
    expect(
      missingSkillsForDocument(d, job({ id: "j1" })),
    ).toEqual([]);
  });
});

describe("resumesToTailor", () => {
  it("pairs each document with the best sponsor job and its gaps", () => {
    const out = resumesToTailor(
      [doc("d1", "Main resume")],
      [
        {
          job: job({ id: "j1", skills: ["Java", "Spring Boot", "Terraform"] }),
          score: 88,
          via: "match",
        },
      ],
    );
    expect(out).toHaveLength(1);
    expect(out[0].missingSkills).toEqual(["Terraform"]);
    expect(out[0].job.id).toBe("j1");
  });

  it("is empty with no sponsor jobs, and skips fully-covered resumes", () => {
    expect(resumesToTailor([doc("d1", "R")], [])).toEqual([]);
    const covered = doc("d1", "R");
    covered.state.skills = ["Java", "Spring Boot", "Kafka"];
    expect(
      resumesToTailor(
        [covered],
        [{ job: job({ id: "j1" }), score: 88, via: "match" }],
      ),
    ).toEqual([]);
  });
});

describe("buildDailyPlan", () => {
  it("returns no plan when the clock is not set", () => {
    const plan = buildDailyPlan(
      {
        profile: profile({ h1bClockStartDate: null }),
        matches: [match(job({ id: "j1" }), 90)],
        jobs: [],
        documents: [doc("d1", "R")],
      },
      TODAY,
    );
    expect(plan.daysLeft).toBeNull();
    expect(plan.sponsorJobs).toEqual([]);
    expect(plan.today).toEqual([]);
    expect(plan.week).toEqual([]);
  });

  it("derives days left from the frozen clock math", () => {
    // Started 2026-10-18: 30 days since → 30 left.
    const plan = buildDailyPlan(
      { profile: profile(), matches: [], jobs: [], documents: [] },
      TODAY,
    );
    expect(plan.daysLeft).toBe(30);
    expect(plan.deadline?.toISOString()).toBe("2026-12-17T00:00:00.000Z");
  });

  it("today has apply + tailor + interview from the same top sponsor job", () => {
    const sponsor = job({
      id: "j1",
      skills: ["Java", "Spring Boot", "Kafka", "Terraform"],
    });
    const plan = buildDailyPlan(
      {
        profile: profile(),
        matches: [match(sponsor, 88)],
        jobs: [],
        documents: [doc("d1", "Main resume")],
      },
      TODAY,
    );
    expect(plan.today.map((i) => i.kind)).toEqual([
      "apply",
      "tailor",
      "interview",
    ]);
    expect(plan.interviewJob?.id).toBe("j1");
    expect(plan.today[1].href).toContain("/resume-builder?documentId=d1");
    expect(plan.today[2].href).toBe("/interviews");
  });

  it("week has 7 days starting today, with real items only", () => {
    const sponsor = job({
      id: "j1",
      skills: ["Java", "Spring Boot", "Kafka", "Terraform"],
    });
    const plan = buildDailyPlan(
      {
        profile: profile(),
        matches: [match(sponsor, 88)],
        jobs: [],
        documents: [doc("d1", "Main resume")],
      },
      TODAY,
    );
    expect(plan.week).toHaveLength(7);
    expect(plan.week[0].label).toBe("Today");
    expect(plan.week[0].dateISO).toBe("2026-11-17");
    expect(plan.week[1].label).toBe("Tomorrow");
    expect(plan.week[0].items[0].kind).toBe("apply");
    // Interview practice lands on day 3 and day 6 (offsets 2 and 5).
    expect(plan.week[2].items.some((i) => i.kind === "interview")).toBe(true);
    expect(plan.week[5].items.some((i) => i.kind === "interview")).toBe(true);
  });

  it("week days are empty when there is no sponsor data", () => {
    const plan = buildDailyPlan(
      { profile: profile(), matches: [], jobs: [], documents: [] },
      TODAY,
    );
    expect(plan.today).toEqual([]);
    expect(plan.week.every((d) => d.items.length === 0)).toBe(true);
  });
});

describe("sponsorJobsToApply — edge cases", () => {
  it("dedupes a job that appears both as a match and in the feed", () => {
    const j = job({ id: "j-dup" });
    const out = sponsorJobsToApply([j], [match(j, 88)], profile());
    expect(out.map((e) => e.job.id)).toEqual(["j-dup"]);
    expect(out[0].via).toBe("match");
  });

  it("dedupes duplicate feed entries for the same job", () => {
    const j1 = job({ id: "j-dup", title: "Kafka Engineer" });
    const j2 = job({ id: "j-dup", title: "Kafka Engineer" });
    const out = sponsorJobsToApply([j1, j2], [], profile());
    expect(out).toHaveLength(1);
  });

  it("feed jobs fill the remaining slots after matches, in feed order", () => {
    const m = job({ id: "j-m" });
    const f1 = job({ id: "j-f1", title: "Kafka Engineer" });
    const f2 = job({ id: "j-f2", title: "Spring Engineer" });
    const out = sponsorJobsToApply([f1, f2], [match(m, 80)], profile());
    expect(out.map((e) => e.job.id)).toEqual(["j-m", "j-f1", "j-f2"]);
  });

  it("a full match list leaves no room for feed jobs", () => {
    const matches = Array.from({ length: 5 }, (_, i) =>
      match(job({ id: `j-m${i}` }), 90 - i),
    );
    const feed = job({ id: "j-feed", title: "Kafka Engineer" });
    const out = sponsorJobsToApply([feed], matches, profile());
    expect(out).toHaveLength(5);
    expect(out.map((e) => e.job.id)).not.toContain("j-feed");
  });

  it("feed jobs are excluded with a null or undefined profile (no overlap to check)", () => {
    const feed = job({ id: "j-feed", title: "Kafka Engineer" });
    expect(sponsorJobsToApply([feed], [], null)).toEqual([]);
    expect(sponsorJobsToApply([feed], [], undefined)).toEqual([]);
  });

  it("a match with score 0 still ranks as a match", () => {
    const j = job({ id: "j-zero" });
    const out = sponsorJobsToApply([], [match(j, 0)], profile());
    expect(out).toHaveLength(1);
    expect(out[0].score).toBe(0);
    expect(out[0].via).toBe("match");
  });

  it("a sponsor match is kept even when the profile is null", () => {
    const j = job({ id: "j-m" });
    const out = sponsorJobsToApply([], [match(j, 75)], null);
    expect(out.map((e) => e.job.id)).toEqual(["j-m"]);
  });

  it("an empty feed and empty matches return an empty list", () => {
    expect(sponsorJobsToApply([], [], profile())).toEqual([]);
  });
});

describe("missingSkillsForDocument — edge cases", () => {
  it("ignores blank or whitespace-only job skills", () => {
    const d = doc("d1", "Resume");
    const missing = missingSkillsForDocument(
      d,
      job({ id: "j1", skills: ["Java", "   ", ""] }),
    );
    expect(missing).toEqual([]);
  });

  it("returns empty for a job with no skills at all", () => {
    const d = doc("d1", "Resume");
    expect(missingSkillsForDocument(d, job({ id: "j1", skills: [] }))).toEqual([]);
  });

  it("matches skills case-insensitively and across summary and education", () => {
    const d = doc("d1", "Resume");
    d.state.skills = [];
    d.state.summary = "Built services with KAFKA and terraform.";
    d.state.education = [
      { id: "e1", school: "State University", degree: "B.S. Computer Science", startDate: "", endDate: "" },
    ];
    expect(
      missingSkillsForDocument(d, job({ id: "j1", skills: ["kafka", "Terraform"] })),
    ).toEqual([]);
  });

  it("matches special-character skills such as C++", () => {
    const d = doc("d1", "Resume");
    d.state.skills = ["C++", "Go"];
    expect(
      missingSkillsForDocument(d, job({ id: "j1", skills: ["C++", "Rust"] })),
    ).toEqual(["Rust"]);
  });

  it("matches a very long skill token exactly", () => {
    const long = "Distributed Systems Observability Platform Engineering";
    const d = doc("d1", "Resume");
    d.state.skills = [long];
    expect(
      missingSkillsForDocument(d, job({ id: "j1", skills: [long, "Java"] })),
    ).toEqual([]);
  });
});

describe("resumesToTailor — edge cases", () => {
  it("caps at 3 documents even when more have gaps", () => {
    const sponsor = { job: job({ id: "j1", skills: ["Terraform", "Go"] }), score: 90, via: "match" as const };
    const docs = Array.from({ length: 5 }, (_, i) => doc(`d${i}`, `Resume ${i}`));
    expect(resumesToTailor(docs, [sponsor])).toHaveLength(3);
  });

  it("pairs every document with the FIRST (best) sponsor job only", () => {
    const best = { job: job({ id: "j-best", skills: ["Terraform"] }), score: 95, via: "match" as const };
    const second = { job: job({ id: "j-second", skills: ["Go", "Rust"] }), score: 80, via: "match" as const };
    const out = resumesToTailor([doc("d1", "R")], [best, second]);
    expect(out).toHaveLength(1);
    expect(out[0].job.id).toBe("j-best");
    expect(out[0].missingSkills).toEqual(["Terraform"]);
  });

  it("skips a document that covers the target job but keeps the next one", () => {
    const covered = doc("d1", "Covered");
    covered.state.skills = ["Java", "Spring Boot", "Kafka", "Terraform"];
    const gapped = doc("d2", "Gapped");
    const sponsor = { job: job({ id: "j1", skills: ["Terraform"] }), score: 90, via: "match" as const };
    const out = resumesToTailor([covered, gapped], [sponsor]);
    expect(out.map((t) => t.document.id)).toEqual(["d2"]);
  });
});

describe("buildDailyPlan — clock boundaries and plan derivation", () => {
  it("still builds a plan on the last day (0 days left)", () => {
    // Started 2026-09-18: 60 days since → 0 left.
    const plan = buildDailyPlan(
      {
        profile: profile({ h1bClockStartDate: "2026-09-18" }),
        matches: [match(job({ id: "j1" }), 88)],
        jobs: [],
        documents: [],
      },
      TODAY,
    );
    expect(plan.daysLeft).toBe(0);
    expect(plan.today.map((i) => i.kind)).toEqual(["apply", "interview"]);
  });

  it("still builds a plan after expiry (negative days left)", () => {
    const plan = buildDailyPlan(
      {
        profile: profile({ h1bClockStartDate: "2026-09-01" }),
        matches: [match(job({ id: "j1" }), 88)],
        jobs: [],
        documents: [],
      },
      TODAY,
    );
    expect(plan.daysLeft).toBe(-17);
    expect(plan.sponsorJobs).toHaveLength(1);
  });

  it("starts at exactly 60 days left on the layoff date itself", () => {
    const plan = buildDailyPlan(
      { profile: profile({ h1bClockStartDate: "2026-11-17" }), matches: [], jobs: [], documents: [] },
      TODAY,
    );
    expect(plan.daysLeft).toBe(60);
    expect(plan.deadline?.toISOString()).toBe("2027-01-16T00:00:00.000Z");
  });

  it("returns no plan for a null profile", () => {
    const plan = buildDailyPlan(
      { profile: null, matches: [match(job({ id: "j1" }), 90)], jobs: [], documents: [] },
      TODAY,
    );
    expect(plan.daysLeft).toBeNull();
    expect(plan.week).toEqual([]);
  });

  it("week rotates through sponsor jobs one per day", () => {
    const jobs = [1, 2, 3].map((n) => job({ id: `j${n}`, title: `Job ${n}` }));
    const plan = buildDailyPlan(
      {
        profile: profile(),
        matches: jobs.map((j) => match(j, 90)),
        jobs: [],
        documents: [],
      },
      TODAY,
    );
    const applyIds = plan.week.map(
      (d) => d.items.find((i) => i.kind === "apply")?.jobId,
    );
    expect(applyIds).toEqual(["j1", "j2", "j3", "j1", "j2", "j3", "j1"]);
  });

  it("tailor slots land on odd offsets only (days 2, 4, 6)", () => {
    const sponsor = job({ id: "j1", skills: ["Java", "Spring Boot", "Kafka", "Terraform"] });
    const plan = buildDailyPlan(
      {
        profile: profile(),
        matches: [match(sponsor, 88)],
        jobs: [],
        documents: [doc("d1", "Main resume")],
      },
      TODAY,
    );
    const tailorOffsets = plan.week
      .map((d, i) => ({ i, has: d.items.some((x) => x.kind === "tailor") }))
      .filter((x) => x.has)
      .map((x) => x.i);
    expect(tailorOffsets).toEqual([1, 3, 5]);
    expect(plan.week[0].items.some((x) => x.kind === "tailor")).toBe(false);
  });

  it("no interview items when there is no interview job", () => {
    const plan = buildDailyPlan(
      { profile: profile(), matches: [], jobs: [], documents: [] },
      TODAY,
    );
    expect(plan.interviewJob).toBeNull();
    expect(plan.week.flatMap((d) => d.items).some((i) => i.kind === "interview")).toBe(false);
  });

  it("interview job falls back to the top non-sponsor match when no sponsor job exists", () => {
    const plain = job({ id: "j-plain", sponsorsH1b: false });
    const plan = buildDailyPlan(
      { profile: profile(), matches: [match(plain, 70)], jobs: [], documents: [] },
      TODAY,
    );
    expect(plan.sponsorJobs).toEqual([]);
    expect(plan.interviewJob?.id).toBe("j-plain");
    expect(plan.today.map((i) => i.kind)).toEqual(["interview"]);
  });

  it("apply item falls back to /jobs when the job has no source URL", () => {
    const sponsor = job({ id: "j1", sourceUrl: null });
    const plan = buildDailyPlan(
      { profile: profile(), matches: [match(sponsor, 88)], jobs: [], documents: [] },
      TODAY,
    );
    expect(plan.today[0].href).toBe("/jobs");
  });

  it("apply item links to the real posting URL when present", () => {
    const sponsor = job({ id: "j1", sourceUrl: "https://example.com/jobs/1" });
    const plan = buildDailyPlan(
      { profile: profile(), matches: [match(sponsor, 88)], jobs: [], documents: [] },
      TODAY,
    );
    expect(plan.today[0].href).toBe("https://example.com/jobs/1");
  });

  it("tailor item uses /resume-builder when the document has no id", () => {
    const sponsor = job({ id: "j1", skills: ["Terraform"] });
    const noId = doc("d1", "Main resume");
    noId.id = null;
    const plan = buildDailyPlan(
      { profile: profile(), matches: [match(sponsor, 88)], jobs: [], documents: [noId] },
      TODAY,
    );
    const tailor = plan.today.find((i) => i.kind === "tailor");
    expect(tailor?.href).toBe("/resume-builder");
    expect(tailor?.documentId).toBeNull();
  });

  it("tailor item falls back to 'Untitled resume' when the document has no title", () => {
    const sponsor = job({ id: "j1", skills: ["Terraform"] });
    const untitled = doc("d1", "Main resume");
    untitled.title = null;
    const plan = buildDailyPlan(
      { profile: profile(), matches: [match(sponsor, 88)], jobs: [], documents: [untitled] },
      TODAY,
    );
    const tailor = plan.today.find((i) => i.kind === "tailor");
    expect(tailor?.title).toContain('Tailor "Untitled resume"');
  });

  it("week labels roll over the month boundary", () => {
    const sponsor = job({ id: "j1" });
    const monthEnd = new Date(Date.UTC(2026, 10, 28));
    const plan = buildDailyPlan(
      { profile: profile({ h1bClockStartDate: "2026-10-18" }), matches: [match(sponsor, 88)], jobs: [], documents: [] },
      monthEnd,
    );
    expect(plan.week[0].dateISO).toBe("2026-11-28");
    expect(plan.week[3].dateISO).toBe("2026-12-01");
    expect(plan.week[6].dateISO).toBe("2026-12-04");
  });

  it("duplicate matches for the same job are passed through as-is (backend uniqueness assumed)", () => {
    const j = job({ id: "j1" });
    const out = sponsorJobsToApply([], [match(j, 88), match(j, 88)], profile());
    // The dedupe set covers feed jobs; matches are pushed as-is, so a
    // duplicated match row from the backend would surface twice.
    expect(out.map((e) => e.job.id)).toEqual(["j1", "j1"]);
  });
});
