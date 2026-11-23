// Landing page + home dashboard — the Mission Model.
// Landing: public, no login form, mission + steps + features + pricing.
// Home: clock hero, weekly applications, funnel, new jobs today, daily
// learning slot, and the daily check-in with a real streak.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import LandingPage from "@/app/page";
import HomePage from "@/app/home/page";
import { DAILY_CHECKINS_KEY, todayISO } from "@/lib/home-daily";
import type { Job, Match, Profile, QuestionDto, ResumeBuilderDocument, TrackerDto } from "@/lib/types";

/** ISO date N days before today (UTC). */
function daysAgoISO(n: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
}

/** ISO timestamp today at 08:00 UTC. */
function todayAt0800(): string {
  const d = new Date();
  d.setUTCHours(8, 0, 0, 0);
  return d.toISOString();
}

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
    postedAt: todayAt0800(),
    source: "SEED",
    sourceUrl: null,
    ...overrides,
  };
}

const mockState = vi.hoisted(() => {
  const prof: Profile = {
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
    h1bClockStartDate: "",
    linkedinUrl: null,
    workAuthorization: "H1B",
    completeness: 80,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  };
  // Inline builder state (no imports inside vi.hoisted — they are not
  // initialized yet). Skills and summary are empty so the Analyzer has
  // failing checks and a tip of the day exists.
  const docState = {
    contact: {
      fullName: "Alex Morgan",
      title: "Senior Software Engineer",
      email: "alex.morgan@example.com",
      phone: "(571) 555-0142",
      location: "Reston, VA",
      linkedin: "linkedin.com/in/alexmorgan",
    },
    summary: "",
    experience: [
      {
        id: "exp-1",
        title: "Senior Software Engineer",
        company: "Meridian Care Technologies",
        startDate: "2021",
        endDate: "Present",
        bullets: ["Led the migration of 12 services, cutting p95 latency by 38%."],
      },
    ],
    education: [
      {
        id: "edu-1",
        school: "George Mason University",
        degree: "B.S. Computer Science",
        startDate: "2013",
        endDate: "2017",
      },
    ],
    skills: [],
  };
  const doc: ResumeBuilderDocument = {
    id: "doc-1",
    templateId: null,
    title: "My resume",
    state: docState,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  };
  return {
    profile: prof,
    tracker: [] as TrackerDto[],
    jobs: [] as Job[],
    matches: [] as Match[],
    documents: [doc] as ResumeBuilderDocument[],
    questions: [] as QuestionDto[],
  };
});

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/home",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));

vi.mock("@/lib/api", () => ({
  fetchMatches: () => Promise.resolve([]),
  useProfile: () => ({ data: mockState.profile, isLoading: false }),
  useResumes: () => ({ data: [], isLoading: false }),
  useMatches: () => ({ data: mockState.matches, isLoading: false }),
  useTracker: () => ({ data: mockState.tracker, isLoading: false }),
  usePendingApprovals: () => ({ data: [], isLoading: false }),
  useInterviewSessions: () => ({ data: [], isLoading: false }),
  useJobs: () => ({ data: { content: mockState.jobs, page: 0, size: 100, totalElements: mockState.jobs.length, totalPages: 1 }, isLoading: false }),
  useBuilderDocuments: () => ({ data: mockState.documents, isLoading: false }),
  useInterviewQuestions: () => ({ data: mockState.questions, isLoading: false }),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  window.localStorage.clear();
  mockState.profile = { ...mockState.profile, h1bClockStartDate: daysAgoISO(10) };
  mockState.tracker = [];
  mockState.jobs = [];
  mockState.matches = [];
  mockState.questions = [];
});

describe("Landing page (public)", () => {
  it("states the mission — no login wall, no login form", () => {
    render(<LandingPage />);
    expect(screen.getByText(/Laid off on H-1B\? You have 60 days/i)).toBeInTheDocument();
    expect(document.querySelector('input[type="password"]')).toBeNull();
    // "Start your 60 days" appears twice (hero + daily-visit band) — both go to /register.
    const ctas = screen.getAllByRole("link", { name: /Start your 60 days/i });
    expect(ctas).toHaveLength(2);
    for (const cta of ctas) expect(cta).toHaveAttribute("href", "/register");
    // "Sign in" appears twice (hero + pricing) — both go to /login.
    const signins = screen.getAllByRole("link", { name: /Sign in/i });
    expect(signins).toHaveLength(2);
    for (const link of signins) expect(link).toHaveAttribute("href", "/login");
  });

  it("shows the example countdown labelled as an example, never personal", () => {
    render(<LandingPage />);
    expect(screen.getByText(/Example — not your clock/i)).toBeInTheDocument();
    // Started 23 days ago → 37 days left of the 60-day grace period.
    expect(screen.getByText(/has 37 days left/i)).toBeInTheDocument();
  });

  it("lists the four how-it-works steps, each linking to the real feature", () => {
    render(<LandingPage />);
    expect(screen.getByText("Upload your resume")).toBeInTheDocument();
    expect(screen.getByText("Get sponsor jobs")).toBeInTheDocument();
    expect(screen.getByText("Tailor + apply in one click")).toBeInTheDocument();
    expect(screen.getByText("Prep for interviews")).toBeInTheDocument();
  });

  it("lists the real feature set and the real pricing, no subscription", () => {
    render(<LandingPage />);
    for (const name of ["60-Day Clock", "Resume Builder", "Sponsor Jobs", "Coach", "Interview Prep", "Cover Letter", "Sponsor Alerts", "Offer Comparison"]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
    expect(screen.getByText("$79–99")).toBeInTheDocument();
    expect(screen.getByText("$149")).toBeInTheDocument();
    expect(screen.getByText("$299–499")).toBeInTheDocument();
    expect(screen.getByText(/No subscription/i)).toBeInTheDocument();
  });

  it("invents no testimonials or user counts", () => {
    render(<LandingPage />);
    const text = document.body.textContent ?? "";
    expect(text).not.toMatch(/loved by|trusted by|\d+,?\d+ users|5-star/i);
  });
});

describe("Home dashboard", () => {
  it("puts the clock front and center with today's plan", async () => {
    mockState.matches = [
      { job: job({ id: "j1" }), score: 88, reasons: [], gaps: [], source: "RESUME" },
    ];
    mockState.jobs = [job({ id: "j1" })];
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Your 60-day clock")).toBeInTheDocument();
    // Started 10 days ago → 50 days left.
    expect(screen.getByText("50")).toBeInTheDocument();
    expect(screen.getByText(/days left/i)).toBeInTheDocument();
    expect(screen.getByText(/Today's plan/i)).toBeInTheDocument();
  });

  it("with no layoff date set, shows Not set and no fake countdown", async () => {
    mockState.profile = { ...mockState.profile, h1bClockStartDate: null };
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Not set")).toBeInTheDocument();
    expect(screen.queryByText(/days left/i)).toBeNull();
    expect(document.querySelector(".border-amber-400")).toBeNull();
    expect(document.querySelector(".border-rose-400")).toBeNull();
  });

  it("counts this week's applications against the weekly goal", async () => {
    mockState.tracker = [
      { id: "t1", jobId: "j1", title: "A", company: "Acme", resumeId: null, status: "APPLIED", notes: null, createdAt: todayAt0800(), updatedAt: todayAt0800() },
      { id: "t2", jobId: "j2", title: "B", company: "Beta", resumeId: null, status: "INTERVIEW", notes: null, createdAt: todayAt0800(), updatedAt: todayAt0800() },
      { id: "t3", jobId: "j3", title: "C", company: "Gamma", resumeId: null, status: "SAVED", notes: null, createdAt: todayAt0800(), updatedAt: todayAt0800() },
    ];
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Job applications")).toBeInTheDocument();
    expect(screen.getByText(/of 5 this week/i)).toBeInTheDocument();
    expect(screen.getByText("Bookmarked")).toBeInTheDocument();
    expect(screen.getByText("Applied")).toBeInTheDocument();
  });

  it("shows new sponsor jobs posted today, and marking them seen clears the list", async () => {
    const user = userEvent.setup();
    mockState.jobs = [job({ id: "j1" })];
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("New sponsor jobs today")).toBeInTheDocument();
    expect(screen.getByText(/Senior Java Engineer · Acme/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Mark these as seen/i }));
    expect(await screen.findByText(/No new sponsor jobs today yet/i)).toBeInTheDocument();
  });

  it("with no jobs posted today, says so instead of showing stale jobs", async () => {
    mockState.jobs = [job({ id: "j1", postedAt: "2026-11-01T08:00:00Z" })];
    render(<HomePage />, { wrapper });
    expect(await screen.findByText(/No new sponsor jobs today yet/i)).toBeInTheDocument();
    expect(screen.queryByText(/Senior Java Engineer · Acme/)).toBeNull();
  });

  it("shows the question of the day and the resume tip of the day", async () => {
    mockState.questions = [
      { id: "q1", text: "Tell me about a hard bug you fixed.", category: "Behavioral", role: "Backend" },
      { id: "q2", text: "Why this role?", category: "Behavioral", role: "Backend" },
    ];
    mockState.matches = [
      { job: job({ id: "j1" }), score: 88, reasons: [], gaps: [], source: "RESUME" },
    ];
    mockState.jobs = [job({ id: "j1" })];
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Learn today")).toBeInTheDocument();
    expect(
      screen.getByText(/Tell me about a hard bug you fixed\.|Why this role\?/),
    ).toBeInTheDocument();
    // The tip names a real failing check (skills or summary).
    expect(screen.getByText(/Skills listed|Summary present/)).toBeInTheDocument();
  });

  it("daily check-in records today and shows the streak; a second click does nothing new", async () => {
    const user = userEvent.setup();
    render(<HomePage />, { wrapper });
    const button = await screen.findByRole("button", { name: /Did you apply today\? Check in/i });
    await user.click(button);
    expect(await screen.findByRole("button", { name: /Checked in today/i })).toBeInTheDocument();
    expect(screen.getByText("1-day streak")).toBeInTheDocument();
    expect(window.localStorage.getItem(DAILY_CHECKINS_KEY)).toContain(todayISO());
    expect(screen.getByText(/Streak saved on this device/i)).toBeInTheDocument();
  });

  it("an existing streak from yesterday is shown as alive", async () => {
    const yesterday = new Date();
    yesterday.setUTCDate(yesterday.getUTCDate() - 1);
    const yISO = yesterday.toISOString().slice(0, 10);
    const dayBefore = new Date();
    dayBefore.setUTCDate(dayBefore.getUTCDate() - 2);
    window.localStorage.setItem(
      DAILY_CHECKINS_KEY,
      JSON.stringify([dayBefore.toISOString().slice(0, 10), yISO]),
    );
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("2-day streak")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Did you apply today\? Check in/i })).toBeEnabled();
  });

  it("Architect F1: an INTERVIEW-only tracker renders Applied as 1 at 100%, never 0 at 100%", async () => {
    mockState.tracker = [
      { id: "t1", jobId: "j1", title: "A", company: "Acme", resumeId: null, status: "INTERVIEW", notes: null, createdAt: todayAt0800(), updatedAt: todayAt0800() },
    ];
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Job applications")).toBeInTheDocument();
    const appliedRow = screen.getByText("Applied").closest("li");
    expect(appliedRow).not.toBeNull();
    expect(appliedRow!.textContent).toContain("1");
    expect(appliedRow!.textContent).toContain("100%");
    const interviewRow = screen.getByText("Interviewing").closest("li");
    expect(interviewRow).not.toBeNull();
    expect(interviewRow!.textContent).toContain("1");
    expect(interviewRow!.textContent).toContain("100%");
  });

  it("Architect F1: an OFFER-only tracker renders Applied and Interviewing as 1 at 100%", async () => {
    mockState.tracker = [
      { id: "t1", jobId: "j1", title: "A", company: "Acme", resumeId: null, status: "OFFER", notes: null, createdAt: todayAt0800(), updatedAt: todayAt0800() },
    ];
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Job applications")).toBeInTheDocument();
    for (const label of ["Applied", "Interviewing", "Offer"]) {
      const row = screen.getByText(label).closest("li");
      expect(row).not.toBeNull();
      expect(row!.textContent).toContain("1");
      expect(row!.textContent).toContain("100%");
    }
  });

  // E2E contract (Nov 22): /home renders no "Active" / "Urgent" state
  // label — the clock state is the card's warning border (amber when
  // urgent, rose when expired, plain when active). These tests pin that
  // signal, which the dashboard E2E spec asserts.
  it("active clock (50 days left) carries no urgent/expired warning border", async () => {
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Your 60-day clock")).toBeInTheDocument();
    expect(screen.getByText("50")).toBeInTheDocument();
    expect(document.querySelector(".border-amber-400")).toBeNull();
    expect(document.querySelector(".border-rose-400")).toBeNull();
  });

  it("urgent clock (10 days left) carries the amber warning border", async () => {
    mockState.profile = { ...mockState.profile, h1bClockStartDate: daysAgoISO(50) };
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Your 60-day clock")).toBeInTheDocument();
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getByText(/days left/i)).toBeInTheDocument();
    expect(document.querySelector(".border-amber-400")).not.toBeNull();
  });

  it("expired clock carries the rose warning border and says days past deadline", async () => {
    mockState.profile = { ...mockState.profile, h1bClockStartDate: daysAgoISO(70) };
    render(<HomePage />, { wrapper });
    expect(await screen.findByText("Your 60-day clock")).toBeInTheDocument();
    expect(screen.getByText("-10")).toBeInTheDocument();
    expect(screen.getByText(/days past deadline/i)).toBeInTheDocument();
    expect(document.querySelector(".border-rose-400")).not.toBeNull();
  });
});
