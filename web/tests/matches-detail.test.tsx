// Matches page job detail: a match card shows only a short summary
// (title, company, location, work type). Clicking the card — or the
// "View details" button — must reveal the full job: salary, description,
// skills, H-1B sponsorship and posted date. Clicking Save/Apply must NOT
// toggle the detail.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import MatchesPage from "@/app/matches/page";
import type { Job, Match, ResumeItem } from "@/lib/types";

const mockResume: ResumeItem = {
  id: "res-1",
  label: "Resume",
  fileName: "resume.pdf",
  fileType: "application/pdf",
  fileSize: 1234,
  status: "READY",
  createdAt: "2026-11-01T00:00:00Z",
};

const mockJob: Job = {
  id: "job-1",
  title: "Senior Agentic AI Engineer",
  company: "Acme Health",
  location: "Reston, VA",
  workType: "Hybrid",
  salaryMin: 150000,
  salaryMax: 200000,
  currency: "USD",
  sponsorsH1b: true,
  skills: ["Python", "LangGraph"],
  summary: "Build agentic AI for claims processing.",
  role: "AI",
  postedAt: "2026-11-22T00:00:00Z",
  source: "LINKEDIN",
  sourceUrl: "https://example.com/job/1",
};

const mockMatch: Match = {
  job: mockJob,
  score: 92,
  reasons: [{ label: "Python", points: 20 }],
  gaps: ["Temporal"],
  source: "RESUME",
};

const saveMutate = vi.fn(() => Promise.resolve({}));
const applyMutate = vi.fn(() => Promise.resolve({}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/matches",
  useRouter: () => ({ replace: vi.fn() }),
}));

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  useResumes: () => ({ data: [mockResume] }),
  useMatches: () => ({ data: [mockMatch], isLoading: false, isError: false }),
  useApplyJob: () => ({ mutateAsync: applyMutate, isPending: false }),
  useSaveJob: () => ({ mutateAsync: saveMutate, isPending: false }),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("Matches page job detail", () => {
  it("hides the full job detail by default", async () => {
    render(<MatchesPage />, { wrapper });
    expect(await screen.findByText("Senior Agentic AI Engineer")).toBeInTheDocument();
    expect(screen.queryByText("Build agentic AI for claims processing.")).not.toBeInTheDocument();
    expect(screen.queryByText(/\$150k/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /View details/ })).toBeInTheDocument();
  });

  it("clicking View details reveals salary, description, skills and sponsorship", async () => {
    const user = userEvent.setup();
    render(<MatchesPage />, { wrapper });
    await user.click(await screen.findByRole("button", { name: /View details/ }));

    const detail = await screen.findByTestId("job-detail-job-1");
    expect(within(detail).getByText("Build agentic AI for claims processing.")).toBeInTheDocument();
    expect(within(detail).getByText(/\$150k/)).toBeInTheDocument();
    expect(within(detail).getByText("Python")).toBeInTheDocument();
    expect(within(detail).getByText("LangGraph")).toBeInTheDocument();
    expect(within(detail).getByText("Sponsors H-1B")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Hide details/ })).toBeInTheDocument();
  });

  it("clicking Hide details collapses the detail again", async () => {
    const user = userEvent.setup();
    render(<MatchesPage />, { wrapper });
    await user.click(await screen.findByRole("button", { name: /View details/ }));
    await screen.findByTestId("job-detail-job-1");
    await user.click(screen.getByRole("button", { name: /Hide details/ }));
    expect(screen.queryByText("Build agentic AI for claims processing.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /View details/ })).toBeInTheDocument();
  });

  it("clicking the card title expands the detail", async () => {
    const user = userEvent.setup();
    render(<MatchesPage />, { wrapper });
    await user.click(await screen.findByText("Senior Agentic AI Engineer"));
    await screen.findByTestId("job-detail-job-1");
    expect(screen.getByText("Build agentic AI for claims processing.")).toBeInTheDocument();
  });

  it("clicking Save does not expand the detail", async () => {
    const user = userEvent.setup();
    render(<MatchesPage />, { wrapper });
    await user.click(await screen.findByRole("button", { name: "Save" }));
    expect(await screen.findByRole("button", { name: "Saved" })).toBeInTheDocument();
    expect(screen.queryByText("Build agentic AI for claims processing.")).not.toBeInTheDocument();
  });
});
