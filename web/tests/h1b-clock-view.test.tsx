// Feature 1: the clock view — setup prompt (never a fake countdown) when
// unset, live countdown + real-data plan when set.
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { H1bClockView } from "@/components/h1b-clock-view";
import { clockStatus, formatDeadline } from "@/lib/h1b-clock";

/** ISO date N days before today (UTC calendar days). */
function daysAgoISO(n: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
}
import { sampleBuilderState } from "@/lib/resume-builder";
import type { Job, Match, Profile, ResumeBuilderDocument } from "@/lib/types";

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
    skills: ["Java", "Spring Boot", "Kafka", "Terraform"],
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

describe("H1bClockView — unset", () => {
  it("shows the setup prompt and no countdown", () => {
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: null })}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText("Set your 60-day clock")).toBeInTheDocument();
    expect(
      screen.getByLabelText("Layoff date (last working day)"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/days left/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Deadline/)).not.toBeInTheDocument();
  });

  it("saves the picked date and rejects a future date", async () => {
    const user = userEvent.setup();
    const onSaveDate = vi.fn();
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: null })}
        onSaveDate={onSaveDate}
      />,
    );
    await user.type(
      screen.getByLabelText("Layoff date (last working day)"),
      "2026-11-10",
    );
    await user.click(
      screen.getByRole("button", { name: "Start my 60-day clock" }),
    );
    expect(onSaveDate).toHaveBeenCalledWith("2026-11-10");
  });

  it("shows an error for a future date and does not save", async () => {
    const user = userEvent.setup();
    const onSaveDate = vi.fn();
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: null })}
        onSaveDate={onSaveDate}
      />,
    );
    await user.type(
      screen.getByLabelText("Layoff date (last working day)"),
      "2999-01-01",
    );
    await user.click(
      screen.getByRole("button", { name: "Start my 60-day clock" }),
    );
    expect(
      screen.getByText("The start date can't be in the future."),
    ).toBeInTheDocument();
    expect(onSaveDate).not.toHaveBeenCalled();
  });
});

describe("H1bClockView — set", () => {
  it("shows the live countdown, deadline, and a plan from real data", () => {
    const sponsor = job({ id: "j1" });
    const start = daysAgoISO(5);
    const expectedDeadline = formatDeadline(clockStatus(start).deadline!);
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: start })}
        matches={[match(sponsor, 88)]}
        jobs={[sponsor]}
        documents={[doc("d1", "Main resume")]}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText("Your 60 days")).toBeInTheDocument();
    expect(screen.getByText(/days left/)).toBeInTheDocument();
    expect(
      screen.getByText(new RegExp(`Deadline ${expectedDeadline}`)),
    ).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Today's plan")).toBeInTheDocument();
    // Today + the 7-day plan both render the same items.
    expect(
      screen.getAllByText("Apply to Senior Java Engineer at Acme").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText('Tailor "Main resume" for Senior Java Engineer')
        .length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("Missing: Terraform").length).toBeGreaterThan(0);
    expect(
      screen.getByRole("link", { name: "Open mock interviews →" }),
    ).toHaveAttribute("href", "/interviews");
    expect(screen.getByText("Next 7 days")).toBeInTheDocument();
    expect(screen.getByText("Sponsor jobs to apply to")).toBeInTheDocument();
    expect(screen.getByText("Resumes to tailor")).toBeInTheDocument();
  });

  it("shows an empty plan message with no sponsor data", () => {
    render(
      <H1bClockView
        profile={profile()}
        matches={[]}
        jobs={[]}
        documents={[]}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText(/Nothing to plan yet/)).toBeInTheDocument();
    expect(screen.queryByText("Sponsor jobs to apply to")).not.toBeInTheDocument();
  });

  it("shows Expired once the 60 days are up", () => {
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(70) })}
        onSaveDate={vi.fn()}
      />,
    );
    // Heading + state badge both read "Expired".
    expect(screen.getAllByText("Expired").length).toBe(2);
    expect(screen.getByText(/your grace period has ended/)).toBeInTheDocument();
  });

  it("shows a loading state", () => {
    render(
      <H1bClockView
        profile={null}
        isLoading
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText("Loading your clock…")).toBeInTheDocument();
  });
});

describe("H1bClockView — clock states and setup edge cases", () => {
  it("shows Urgent with the almost-up note at exactly 14 days left", () => {
    const { container } = render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(46) })}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText("Urgent")).toBeInTheDocument();
    expect(screen.getByText(/your grace period is almost up/)).toBeInTheDocument();
    // Number and unit live in separate nodes, so check the joined text.
    expect(container.textContent).toContain("14");
    expect(screen.getByText("days left")).toBeInTheDocument();
  });

  it("shows singular 'day left' at 1 day left", () => {
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(59) })}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText(/day left$/)).toBeInTheDocument();
    expect(screen.getByText("Urgent")).toBeInTheDocument();
  });

  it("shows '0 day left — last day' on the final day", () => {
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(60) })}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText("day left — last day")).toBeInTheDocument();
    expect(screen.getByText("Urgent")).toBeInTheDocument();
  });

  it("shows the plan even after expiry (negative days left)", () => {
    const sponsor = job({ id: "j1" });
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(70) })}
        matches={[match(sponsor, 88)]}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getAllByText("Expired").length).toBe(2);
    expect(screen.queryByText(/days left/)).not.toBeInTheDocument();
    expect(screen.getByText("Today's plan")).toBeInTheDocument();
    expect(
      screen.getAllByText("Apply to Senior Java Engineer at Acme").length,
    ).toBeGreaterThan(0);
  });

  it("shows an error when the date is left empty", async () => {
    const user = userEvent.setup();
    const onSaveDate = vi.fn();
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: null })}
        onSaveDate={onSaveDate}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: "Start my 60-day clock" }),
    );
    expect(
      screen.getByText("Pick the date your layoff (last working day) was."),
    ).toBeInTheDocument();
    expect(onSaveDate).not.toHaveBeenCalled();
  });

  it("accepts today's date (not in the future)", async () => {
    const user = userEvent.setup();
    const onSaveDate = vi.fn();
    const todayISO = new Date().toISOString().slice(0, 10);
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: null })}
        onSaveDate={onSaveDate}
      />,
    );
    await user.type(
      screen.getByLabelText("Layoff date (last working day)"),
      todayISO,
    );
    await user.click(
      screen.getByRole("button", { name: "Start my 60-day clock" }),
    );
    expect(onSaveDate).toHaveBeenCalledWith(todayISO);
  });

  it("shows a saving state while the date is being saved", () => {
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: null })}
        saving
        onSaveDate={vi.fn()}
      />,
    );
    const button = screen.getByRole("button", { name: "Saving…" });
    expect(button).toBeDisabled();
  });

  it("shows the setup prompt for an undefined profile", () => {
    render(<H1bClockView profile={undefined} onSaveDate={vi.fn()} />);
    expect(screen.getByText("Set your 60-day clock")).toBeInTheDocument();
  });

  it("hides the tailor section when no resume needs tailoring", () => {
    const sponsor = job({ id: "j1" });
    const covered = doc("d1", "Covered");
    covered.state.skills = ["Java", "Spring Boot", "Kafka", "Terraform"];
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(5) })}
        matches={[match(sponsor, 88)]}
        documents={[covered]}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText("Sponsor jobs to apply to")).toBeInTheDocument();
    expect(screen.queryByText("Resumes to tailor")).not.toBeInTheDocument();
  });

  it("renders week day labels Today, Tomorrow and a weekday after that", () => {
    const sponsor = job({ id: "j1" });
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(5) })}
        matches={[match(sponsor, 88)]}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getByText("Today")).toBeInTheDocument();
    expect(screen.getByText("Tomorrow")).toBeInTheDocument();
    // Offsets 2–6 are weekday names like "Thursday, Nov 19".
    expect(
      screen.getAllByText(/^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),/),
    ).toHaveLength(5);
  });

  it("links the sponsor job to its real posting URL", () => {
    const sponsor = job({ id: "j1", sourceUrl: "https://example.com/jobs/1" });
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(5) })}
        matches={[match(sponsor, 88)]}
        onSaveDate={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("link", { name: "Senior Java Engineer · Acme" }),
    ).toHaveAttribute("href", "https://example.com/jobs/1");
  });

  it("shows the mock-interview card for the top sponsor job", () => {
    const sponsor = job({ id: "j1" });
    render(
      <H1bClockView
        profile={profile({ h1bClockStartDate: daysAgoISO(5) })}
        matches={[match(sponsor, 88)]}
        onSaveDate={vi.fn()}
      />,
    );
    expect(screen.getAllByText("Mock interview").length).toBeGreaterThan(0);
    expect(
      screen.getAllByText("Senior Java Engineer at Acme").length,
    ).toBeGreaterThan(0);
  });
});
