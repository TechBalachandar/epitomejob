// W5 derived dashboard logic (contract §9/§10): next-steps priority
// order, recent-activity merge/sort/take-5, and the 5-item completeness
// checklist. The score itself always comes from the server — these tests
// only cover the per-field done/missing derivation.
import { describe, expect, it } from "vitest";
import {
  activeTrackerCount,
  completenessItems,
  deriveNextSteps,
  deriveRecentActivity,
  latestResume,
  missingCompletenessLabels,
  topMatch,
} from "@/lib/dashboard";
import type { Approval, Match, Profile, ResumeItem, TrackerDto } from "@/lib/types";

function profile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: "profile-1",
    userId: "user-1",
    headline: "Senior Software Engineer",
    summary: "Java developer",
    location: "Reston, VA",
    workType: "HYBRID",
    salaryMin: 150000,
    salaryMax: 190000,
    currency: "USD",
    visibility: "PRIVATE",
    skills: [],
    h1bClockStartDate: null,
    completeness: 100,
    createdAt: "2026-10-13T00:00:00.000Z",
    updatedAt: "2026-11-10T00:00:00.000Z",
    ...overrides,
  };
}

function resume(overrides: Partial<ResumeItem> = {}): ResumeItem {
  return {
    id: "resume-1",
    fileName: "resume.pdf",
    fileType: "application/pdf",
    fileSize: 1234,
    status: "READY",
    createdAt: "2026-11-11T00:00:00Z",
    ...overrides,
  };
}

function match(score: number, title = "Agentic AI Engineer"): Match {
  return {
    job: {
      id: `job-${score}`,
      title,
      company: "Meridian Care",
      location: "Remote (US)",
      workType: "Remote",
      salaryMin: 160000,
      salaryMax: 195000,
      currency: "USD",
      sponsorsH1b: true,
      skills: ["Python"],
      summary: "AI work",
      role: "AI",
      postedAt: "2026-11-11T09:00:00Z",
    },
    score,
    reasons: [],
    gaps: [],
    source: "RESUME",
  };
}

describe("deriveNextSteps", () => {
  const full = {
    resumes: [resume()],
    profile: profile(),
    pendingApprovals: [] as Approval[],
    matches: [match(92)],
  };

  it("shows the upload CTA first when there is no resume at all", () => {
    const steps = deriveNextSteps({ ...full, resumes: [] });
    expect(steps[0]).toEqual({
      text: "Upload your resume to unlock matching.",
      href: "/resumes",
    });
  });

  it("shows the missing profile items when completeness < 100", () => {
    const steps = deriveNextSteps({
      ...full,
      profile: profile({ completeness: 60, headline: "  ", salaryMin: null }),
    });
    expect(steps).toContainEqual({
      text: "Complete your profile — missing: Headline, Salary range.",
      href: "/profile",
    });
  });

  it("omits the profile step when completeness is 100", () => {
    const steps = deriveNextSteps(full);
    expect(steps.some((s) => s.href === "/profile")).toBe(false);
  });

  it("shows the pending-approval count with the approvals link", () => {
    const approvals = [
      { id: "a1", userId: "u", action: "APPLY_JOB", payload: "{}", status: "PENDING", expiresAt: "2026-11-20T00:00:00Z", decidedAt: null },
      { id: "a2", userId: "u", action: "APPLY_JOB", payload: "{}", status: "PENDING", expiresAt: "2026-11-20T00:00:00Z", decidedAt: null },
    ] as Approval[];
    const steps = deriveNextSteps({ ...full, pendingApprovals: approvals });
    expect(steps).toContainEqual({
      text: "2 approvals waiting for your decision.",
      href: "/approvals",
    });
  });

  it("shows the top match with score, title and company", () => {
    const steps = deriveNextSteps(full);
    expect(steps).toContainEqual({
      text: "Review your 92% match: Agentic AI Engineer at Meridian Care.",
      href: "/matches",
    });
  });

  it("keeps the contract priority order: resume, profile, approvals, match", () => {
    const steps = deriveNextSteps({
      resumes: [],
      profile: profile({ completeness: 40, headline: "" }),
      pendingApprovals: [
        { id: "a1", userId: "u", action: "APPLY_JOB", payload: "{}", status: "PENDING", expiresAt: "2026-11-20T00:00:00Z", decidedAt: null },
      ] as Approval[],
      matches: [match(92)],
    });
    expect(steps.map((s) => s.href)).toEqual([
      "/resumes",
      "/profile",
      "/approvals",
      "/matches",
    ]);
  });

  it("returns only the match step when everything else is done", () => {
    expect(deriveNextSteps(full)).toEqual([
      {
        text: "Review your 92% match: Agentic AI Engineer at Meridian Care.",
        href: "/matches",
      },
    ]);
    expect(
      deriveNextSteps({ ...full, matches: [] }),
    ).toEqual([]);
  });

  it("never shows a step whose condition is false", () => {
    const steps = deriveNextSteps({
      resumes: [resume()],
      profile: profile(),
      pendingApprovals: [],
      matches: [],
    });
    expect(steps).toEqual([]);
  });
});

describe("deriveRecentActivity", () => {
  it("merges tracker, approvals and resumes, sorts desc, takes 5", () => {
    const tracker = [
      { id: "t1", jobId: "j1", title: "Job A", company: "Co A", resumeId: null, status: "SAVED", notes: null, createdAt: "2026-11-10T00:00:00Z", updatedAt: "2026-11-12T10:00:00Z" },
      { id: "t2", jobId: "j2", title: "Job B", company: "Co B", resumeId: null, status: "APPLIED", notes: null, createdAt: "2026-11-09T00:00:00Z", updatedAt: "2026-11-09T10:00:00Z" },
    ] as TrackerDto[];
    const approvals = [
      { id: "a1", userId: "u", action: "APPLY_JOB", payload: "{}", status: "APPROVED", expiresAt: "2026-11-20T00:00:00Z", decidedAt: "2026-11-11T09:00:00Z" },
    ] as Approval[];
    const resumes = [resume({ createdAt: "2026-11-13T08:00:00Z" })];
    const activity = deriveRecentActivity({ tracker, approvals, resumes });
    expect(activity).toHaveLength(4);
    expect(activity.map((a) => a.at)).toEqual([
      "2026-11-13T08:00:00Z",
      "2026-11-12T10:00:00Z",
      "2026-11-11T09:00:00Z",
      "2026-11-09T10:00:00Z",
    ]);
    expect(activity[0].text).toContain("resume.pdf");
    expect(activity[1].text).toBe("Saved Job A at Co A");
    expect(activity[2].text).toContain("approved");
  });

  it("takes only the 5 most recent", () => {
    const resumes = Array.from({ length: 7 }, (_, i) =>
      resume({
        id: `r${i}`,
        fileName: `resume-${i}.pdf`,
        createdAt: `2026-11-0${i + 1}T00:00:00Z`,
      }),
    );
    expect(deriveRecentActivity({ tracker: [], approvals: [], resumes })).toHaveLength(5);
  });

  it("returns an empty list when there is no data", () => {
    expect(
      deriveRecentActivity({ tracker: [], approvals: [], resumes: [] }),
    ).toEqual([]);
  });
});

describe("completeness checklist", () => {
  it("marks all five items done for a full profile", () => {
    expect(completenessItems(profile()).every((i) => i.done)).toBe(true);
    expect(missingCompletenessLabels(profile())).toEqual([]);
  });

  it("treats blank strings as missing and requires both salaries", () => {
    const items = completenessItems(
      profile({ headline: "   ", summary: "", salaryMax: null }),
    );
    expect(items.find((i) => i.key === "headline")?.done).toBe(false);
    expect(items.find((i) => i.key === "summary")?.done).toBe(false);
    expect(items.find((i) => i.key === "salary")?.done).toBe(false);
    expect(items.find((i) => i.key === "location")?.done).toBe(true);
    expect(missingCompletenessLabels(profile({ headline: "   ", summary: "", salaryMax: null }))).toEqual([
      "Headline",
      "Summary",
      "Salary range",
    ]);
  });

  it("counts active tracker entries as SAVED/APPLIED/INTERVIEW only", () => {
    const tracker = [
      { status: "SAVED" },
      { status: "APPLIED" },
      { status: "INTERVIEW" },
      { status: "OFFER" },
      { status: "REJECTED" },
    ] as TrackerDto[];
    expect(activeTrackerCount(tracker)).toBe(3);
    expect(activeTrackerCount(undefined)).toBe(0);
  });

  it("picks the top match by score and the latest resume by date", () => {
    expect(topMatch([match(70), match(92), match(82)])?.score).toBe(92);
    expect(topMatch([])).toBeNull();
    expect(
      latestResume([
        resume({ id: "old", createdAt: "2026-11-01T00:00:00Z" }),
        resume({ id: "new", createdAt: "2026-11-11T00:00:00Z" }),
      ])?.id,
    ).toBe("new");
  });
});
