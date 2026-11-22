// Feature 6: the sponsor-alert rule — sponsorsH1b AND profile match
// (backend score, or skills / location / headline overlap) AND unseen —
// plus the in-app component (check action, unread count, dismiss).
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  SEEN_SPONSOR_JOBS_KEY,
  findNewSponsorJobs,
  loadSeenJobIds,
  markJobsSeen,
  sponsorAlertReason,
} from "@/lib/sponsor-alerts";
import { SponsorAlerts } from "@/components/sponsor-alerts";
import type { Job, Match, Profile } from "@/lib/types";

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
    skills: ["Java", "Spring Boot"],
    summary: "Build services.",
    role: "Backend",
    postedAt: "2026-11-10T00:00:00Z",
    source: "SEED",
    sourceUrl: "https://example.com/jobs/1",
    ...overrides,
  };
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

function match(jobObj: Job, score: number): Match {
  return { job: jobObj, score, reasons: [], gaps: [], source: "RESUME" };
}

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  window.localStorage.clear();
});

describe("sponsorAlertReason", () => {
  it("returns null when the job does not sponsor H-1B", () => {
    expect(
      sponsorAlertReason(
        job({ id: "j1", sponsorsH1b: false }),
        profile(),
        match(job({ id: "j1" }), 95),
      ),
    ).toBeNull();
  });

  it("alerts on a backend match, reusing the backend score", () => {
    const j = job({ id: "j1" });
    const reason = sponsorAlertReason(j, profile(), match(j, 87));
    expect(reason?.via).toBe("match");
    expect(reason?.score).toBe(87);
    expect(reason?.reason).toContain("87%");
  });

  it("alerts on a profile skill overlap with no backend match", () => {
    const reason = sponsorAlertReason(job({ id: "j1" }), profile(), null);
    expect(reason?.via).toBe("skills");
    expect(reason?.score).toBeNull();
  });

  it("does not alert when nothing overlaps", () => {
    const j = job({
      id: "j1",
      title: "Ruby on Rails Developer",
      location: "Austin, TX",
      skills: ["Ruby", "Rails"],
    });
    expect(
      sponsorAlertReason(j, profile({ headline: "Senior Java Engineer" }), null),
    ).toBeNull();
  });

  it("whole-token: a JavaScript-only profile does not match a Java job", () => {
    const j = job({ id: "j1", skills: ["Java"], title: "Java Developer" });
    const p = profile({
      headline: "Frontend Engineer",
      skills: ["JavaScript"],
      location: "Austin, TX",
    });
    expect(sponsorAlertReason(j, p, null)).toBeNull();
  });

  it("a remote job matches on location for any profile location", () => {
    const j = job({
      id: "j1",
      title: "Go Developer",
      location: "Remote",
      skills: ["Go"],
    });
    const reason = sponsorAlertReason(
      j,
      profile({ headline: "Data Analyst", skills: ["Excel"], location: "Reston, VA" }),
      null,
    );
    expect(reason?.via).toBe("location");
  });
});

describe("findNewSponsorJobs", () => {
  it("excludes jobs already in the seen-set", () => {
    const j1 = job({ id: "j1" });
    const j2 = job({ id: "j2", title: "Kafka Engineer" });
    const out = findNewSponsorJobs(
      [j1, j2],
      profile(),
      [],
      new Set(["j1"]),
    );
    expect(out.map((a) => a.job.id)).toEqual(["j2"]);
  });

  it("only ever returns jobs from the feed", () => {
    const out = findNewSponsorJobs([], profile(), []);
    expect(out).toEqual([]);
  });
});

describe("seen-set persistence", () => {
  it("round-trips through localStorage under the namespaced key", () => {
    const seen = markJobsSeen(["job-a", "job-b"]);
    expect(seen.has("job-a")).toBe(true);
    expect(
      JSON.parse(
        window.localStorage.getItem(SEEN_SPONSOR_JOBS_KEY) ?? "[]",
      ),
    ).toEqual(["job-a", "job-b"]);
    expect(loadSeenJobIds().has("job-b")).toBe(true);
  });

  it("tolerates a corrupt stored value", () => {
    window.localStorage.setItem(SEEN_SPONSOR_JOBS_KEY, "not-json{");
    expect(loadSeenJobIds()).toEqual(new Set());
  });
});

describe("SponsorAlerts component", () => {
  it("shows a new sponsor job with an unread badge and the rule text", () => {
    render(
      <SponsorAlerts
        jobs={[job({ id: "j1" })]}
        matches={[]}
        profile={profile()}
      />,
    );
    expect(screen.getByText("Sponsor-job alerts")).toBeInTheDocument();
    expect(screen.getByText("1 new")).toBeInTheDocument();
    expect(screen.getByText(/Senior Java Engineer · Acme/)).toBeInTheDocument();
    expect(
      screen.getByText(/sponsors H-1B visas and matches your profile/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "View the real posting" }),
    ).toHaveAttribute("href", "https://example.com/jobs/1");
  });

  it("shows the empty state when nothing new matches", () => {
    render(<SponsorAlerts jobs={[]} matches={[]} profile={profile()} />);
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
    expect(screen.queryByText(/new$/)).not.toBeInTheDocument();
  });

  it("Check marks surfaced jobs seen so they do not alert again", async () => {
    const user = userEvent.setup();
    const { unmount } = render(
      <SponsorAlerts
        jobs={[job({ id: "j1" })]}
        matches={[]}
        profile={profile()}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: "Check for new sponsor jobs" }),
    );
    expect(loadSeenJobIds().has("j1")).toBe(true);
    // Fresh mount: the seen job no longer alerts.
    unmount();
    render(
      <SponsorAlerts
        jobs={[job({ id: "j1" })]}
        matches={[]}
        profile={profile()}
      />,
    );
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
  });

  it("Dismiss removes one alert and Mark all read clears the rest", async () => {
    const user = userEvent.setup();
    render(
      <SponsorAlerts
        jobs={[
          job({ id: "j1" }),
          job({ id: "j2", title: "Kafka Engineer" }),
        ]}
        matches={[]}
        profile={profile()}
      />,
    );
    expect(screen.getByText("2 new")).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /Dismiss alert for Senior Java/ }),
    );
    expect(screen.getByText("1 new")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Mark all read" }));
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
  });

  it("B2: surfaces alerts when the feed arrives after mount, with no manual check", () => {
    const { rerender } = render(
      <SponsorAlerts jobs={[]} matches={[]} profile={profile()} />,
    );
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
    rerender(
      <SponsorAlerts
        jobs={[job({ id: "j1" })]}
        matches={[]}
        profile={profile()}
      />,
    );
    expect(screen.getByText("1 new")).toBeInTheDocument();
    expect(screen.getByText(/Senior Java Engineer · Acme/)).toBeInTheDocument();
  });

  it("B3: a dismissed alert does not resurface on the next check", async () => {
    const user = userEvent.setup();
    render(
      <SponsorAlerts
        jobs={[job({ id: "j1" })]}
        matches={[]}
        profile={profile()}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: /Dismiss alert for Senior Java/ }),
    );
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
    expect(loadSeenJobIds().has("j1")).toBe(true);
    await user.click(
      screen.getByRole("button", { name: "Check for new sponsor jobs" }),
    );
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
    expect(
      screen.queryByText(/Senior Java Engineer · Acme/),
    ).not.toBeInTheDocument();
  });
});

describe("profileOverlapsJob — priority and edge cases", () => {
  it("prefers skills over location and headline when several overlap", async () => {
    const { profileOverlapsJob } = await import("@/lib/sponsor-alerts");
    const j = job({ id: "j1", title: "Senior Java Engineer", location: "Reston, VA" });
    expect(profileOverlapsJob(j, profile())?.via).toBe("skills");
  });

  it("matches on location when skills do not overlap", async () => {
    const { profileOverlapsJob } = await import("@/lib/sponsor-alerts");
    const j = job({
      id: "j1",
      title: "Ruby Developer",
      location: "Reston, VA",
      skills: ["Ruby", "Rails"],
    });
    const overlap = profileOverlapsJob(
      j,
      profile({ headline: "Senior Java Engineer", skills: ["Java"] }),
    );
    expect(overlap?.via).toBe("location");
    expect(overlap?.detail).toContain("Reston, VA");
  });

  it("matches when the profile location contains the job location", async () => {
    const { profileOverlapsJob } = await import("@/lib/sponsor-alerts");
    const j = job({
      id: "j1",
      title: "Ruby Developer",
      location: "Reston",
      skills: ["Ruby"],
    });
    expect(
      profileOverlapsJob(j, profile({ location: "Reston, VA", skills: ["Java"] }))?.via,
    ).toBe("location");
  });

  it("matches a remote job regardless of case", async () => {
    const { profileOverlapsJob } = await import("@/lib/sponsor-alerts");
    const j = job({ id: "j1", title: "Go Developer", location: "REMOTE", skills: ["Go"] });
    expect(
      profileOverlapsJob(j, profile({ headline: "Data Analyst", skills: ["Excel"] }))?.via,
    ).toBe("location");
  });

  it("matches on a headline keyword in the job title when nothing else overlaps", async () => {
    const { profileOverlapsJob } = await import("@/lib/sponsor-alerts");
    const j = job({
      id: "j1",
      title: "Senior Go Developer",
      location: "Austin, TX",
      skills: ["Go"],
    });
    const overlap = profileOverlapsJob(
      j,
      profile({ headline: "Senior Java Engineer", skills: ["Java"], location: "Reston, VA" }),
    );
    expect(overlap?.via).toBe("headline");
    expect(overlap?.detail).toContain("senior");
  });

  it("returns null for a null or undefined profile", async () => {
    const { profileOverlapsJob } = await import("@/lib/sponsor-alerts");
    expect(profileOverlapsJob(job({ id: "j1" }), null)).toBeNull();
    expect(profileOverlapsJob(job({ id: "j1" }), undefined)).toBeNull();
  });

  it("returns null for an empty profile (no skills, location, or headline)", async () => {
    const { profileOverlapsJob } = await import("@/lib/sponsor-alerts");
    expect(
      profileOverlapsJob(
        job({ id: "j1" }),
        profile({ skills: [], location: "", headline: "" }),
      ),
    ).toBeNull();
  });

  it("a blank profile skill is ignored", async () => {
    const { profileOverlapsJob } = await import("@/lib/sponsor-alerts");
    const j = job({
      id: "j1",
      title: "Ruby Developer",
      location: "Austin, TX",
      skills: ["Ruby"],
    });
    expect(
      profileOverlapsJob(j, profile({ skills: ["   ", ""], location: "Reston, VA", headline: "Analyst" })),
    ).toBeNull();
  });
});

describe("sponsorAlertReason — precedence and edge cases", () => {
  it("a backend match wins over a profile overlap", () => {
    const j = job({ id: "j1" });
    const reason = sponsorAlertReason(j, profile(), match(j, 72));
    expect(reason?.via).toBe("match");
    expect(reason?.score).toBe(72);
  });

  it("a backend match with score 0 still alerts", () => {
    const j = job({ id: "j1" });
    const reason = sponsorAlertReason(j, profile(), match(j, 0));
    expect(reason?.via).toBe("match");
    expect(reason?.score).toBe(0);
    expect(reason?.reason).toContain("0%");
  });

  it("returns null for a non-sponsor job even with a backend match and overlap", () => {
    const j = job({ id: "j1", sponsorsH1b: false });
    expect(sponsorAlertReason(j, profile(), match(j, 99))).toBeNull();
  });

  it("returns null when the profile is null and there is no backend match", () => {
    expect(sponsorAlertReason(job({ id: "j1" }), null, null)).toBeNull();
  });
});

describe("findNewSponsorJobs — ordering and edge cases", () => {
  it("orders backend-scored alerts by score first, unscored last", () => {
    const j1 = job({ id: "j1", title: "Kafka Engineer" });
    const j2 = job({ id: "j2", title: "Java Engineer" });
    const j3 = job({ id: "j3", title: "Spring Engineer" });
    const out = findNewSponsorJobs(
      [j1, j2, j3],
      profile(),
      [match(j2, 50), match(j3, 90)],
    );
    expect(out.map((a) => a.job.id)).toEqual(["j3", "j2", "j1"]);
    expect(out[2].via).toBe("skills");
  });

  it("keeps feed order for equal scores", () => {
    const j1 = job({ id: "j1", title: "Java Engineer" });
    const j2 = job({ id: "j2", title: "Kafka Engineer" });
    const out = findNewSponsorJobs(
      [j1, j2],
      profile(),
      [match(j1, 80), match(j2, 80)],
    );
    expect(out.map((a) => a.job.id)).toEqual(["j1", "j2"]);
  });

  it("never alerts for a backend match whose job is not in the feed", () => {
    const ghost = job({ id: "j-ghost" });
    expect(findNewSponsorJobs([], profile(), [match(ghost, 99)])).toEqual([]);
  });

  it("a seen job stays hidden even when it has the highest score", () => {
    const j1 = job({ id: "j1" });
    const j2 = job({ id: "j2", title: "Kafka Engineer" });
    const out = findNewSponsorJobs(
      [j1, j2],
      profile(),
      [match(j1, 99)],
      new Set(["j1"]),
    );
    expect(out.map((a) => a.job.id)).toEqual(["j2"]);
  });

  it("duplicate feed entries for the same job surface twice (no dedupe)", () => {
    const j1 = job({ id: "j1" });
    const j2 = job({ id: "j1" });
    const out = findNewSponsorJobs([j1, j2], profile(), []);
    // Current behaviour: the feed is trusted to be unique.
    expect(out.map((a) => a.job.id)).toEqual(["j1", "j1"]);
  });
});

describe("seen-set persistence — edge cases", () => {
  it("tolerates a stored non-array JSON value", () => {
    window.localStorage.setItem(SEEN_SPONSOR_JOBS_KEY, '{"a":1}');
    expect(loadSeenJobIds()).toEqual(new Set());
    window.localStorage.setItem(SEEN_SPONSOR_JOBS_KEY, "123");
    expect(loadSeenJobIds()).toEqual(new Set());
  });

  it("filters non-string entries out of a stored array", () => {
    window.localStorage.setItem(
      SEEN_SPONSOR_JOBS_KEY,
      JSON.stringify(["job-a", 42, null, "job-b"]),
    );
    expect(loadSeenJobIds()).toEqual(new Set(["job-a", "job-b"]));
  });

  it("markJobsSeen dedupes and keeps previously seen ids", () => {
    markJobsSeen(["job-a"]);
    const seen = markJobsSeen(["job-a", "job-b"]);
    expect([...seen].sort()).toEqual(["job-a", "job-b"]);
  });

  it("saving an empty set writes an empty array", async () => {
    const { saveSeenJobIds } = await import("@/lib/sponsor-alerts");
    saveSeenJobIds(new Set());
    expect(window.localStorage.getItem(SEEN_SPONSOR_JOBS_KEY)).toBe("[]");
  });
});

describe("SponsorAlerts component — edge cases", () => {
  it("shows the empty state for a null profile without crashing", () => {
    render(<SponsorAlerts jobs={[job({ id: "j1" })]} matches={[]} profile={null} />);
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
    expect(screen.queryByText(/new$/)).not.toBeInTheDocument();
  });

  it("links to /jobs and hides the posting link when the job has no source URL", () => {
    render(
      <SponsorAlerts
        jobs={[job({ id: "j1", sourceUrl: null })]}
        matches={[]}
        profile={profile()}
      />,
    );
    expect(
      screen.getByRole("link", { name: "Senior Java Engineer · Acme" }),
    ).toHaveAttribute("href", "/jobs");
    expect(screen.queryByRole("link", { name: "View the real posting" })).not.toBeInTheDocument();
  });

  it("lists backend-scored alerts before unscored ones", () => {
    const { container } = render(
      <SponsorAlerts
        jobs={[
          job({ id: "j-feed", title: "Kafka Engineer" }),
          job({ id: "j-scored", title: "Java Engineer" }),
        ]}
        matches={[match(job({ id: "j-scored", title: "Java Engineer" }), 91)]}
        profile={profile()}
      />,
    );
    const items = [...container.querySelectorAll("ul li")].map((li) => li.textContent ?? "");
    expect(items[0]).toContain("Java Engineer");
    expect(items[1]).toContain("Kafka Engineer");
    expect(items[0]).toContain("Backend match 91% (91%)");
  });

  it("surfaces a job after a profile change makes it match, on Check", async () => {
    const user = userEvent.setup();
    const noMatch = profile({ skills: ["Java"], headline: "Java Engineer", location: "Reston, VA" });
    const goJob = job({ id: "j-go", title: "Go Developer", location: "Austin, TX", skills: ["Go"] });
    const { rerender } = render(
      <SponsorAlerts jobs={[goJob]} matches={[]} profile={noMatch} />,
    );
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
    rerender(
      <SponsorAlerts
        jobs={[goJob]}
        matches={[]}
        profile={profile({ skills: ["Go"], headline: "Go Engineer", location: "Reston, VA" })}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Check for new sponsor jobs" }));
    expect(screen.getByText("1 new")).toBeInTheDocument();
    expect(screen.getByText(/Go Developer · Acme/)).toBeInTheDocument();
  });

  it("shows the last-checked time after a Check", async () => {
    const user = userEvent.setup();
    render(
      <SponsorAlerts jobs={[job({ id: "j1" })]} matches={[]} profile={profile()} />,
    );
    expect(screen.queryByText(/Last checked/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Check for new sponsor jobs" }));
    expect(screen.getByText(/Last checked/)).toBeInTheDocument();
  });

  it("a dismissed alert stays dismissed after Check", async () => {
    const user = userEvent.setup();
    render(
      <SponsorAlerts jobs={[job({ id: "j1" })]} matches={[]} profile={profile()} />,
    );
    expect(screen.getByText("1 new")).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /Dismiss alert for Senior Java/ }),
    );
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Check for new sponsor jobs" }));
    expect(screen.getByText(/No new sponsor jobs right now/)).toBeInTheDocument();
    expect(screen.queryByText(/Senior Java Engineer · Acme/)).not.toBeInTheDocument();
  });
});
