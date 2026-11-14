// W6 web tests (contract §3): per-resume matches via ?resumeId=, job
// source badge + apply link (with and without sourceUrl), resume label
// upload/rename, and the LinkedIn URL + work-authorisation round-trip.
// fetch is mocked at the BFF boundary (/api/*) — no real backend.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import {
  fetchMatches,
  renameResume,
  updateProfile,
  useMatches,
  useUploadResume,
} from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import {
  applyLinkLabel,
  buildJobsQuery,
  jobSourceLabel,
  workAuthorizationLabel,
} from "@/lib/jobs";
import {
  latestReadyResume,
  resolveSelectedResumeId,
  topMatchScoreForResume,
} from "@/lib/resume-selection";
import { JobSource } from "@/components/job-source";
import { ResumeSelector } from "@/components/resume-selector";
import MatchesPage from "@/app/matches/page";
import ProfilePage from "@/app/profile/page";
import type { ResumeItem } from "@/lib/types";

// The pages read ?resumeId= via next/navigation; control it per test.
const navMock = vi.hoisted(() => ({
  params: new URLSearchParams(),
  replace: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => navMock.params,
  usePathname: () => "/matches",
  useRouter: () => ({ replace: navMock.replace }),
}));

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockFetch(impl: (url: string, init?: RequestInit) => Response | Promise<Response>) {
  return vi.fn((input: string | URL | Request, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    return Promise.resolve(impl(url, init));
  });
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const RESUMES: ResumeItem[] = [
  { id: "resume-1", label: "Android Developer", fileName: "android.pdf", fileType: "application/pdf", fileSize: 1000, status: "READY", createdAt: "2026-11-01T00:00:00Z" },
  { id: "resume-2", label: "Java Backend", fileName: "java.pdf", fileType: "application/pdf", fileSize: 1000, status: "READY", createdAt: "2026-11-10T00:00:00Z" },
  { id: "resume-3", label: "Scrum Master", fileName: "scrum.pdf", fileType: "application/pdf", fileSize: 1000, status: "PROCESSING", createdAt: "2026-11-12T00:00:00Z" },
];

function job(id: string, title: string, source: string, sourceUrl: string | null): import("@/lib/types").Job {
  return {
    id,
    title,
    company: "Acme",
    location: "Reston, VA",
    workType: "Hybrid",
    salaryMin: 140000,
    salaryMax: 170000,
    currency: "USD",
    sponsorsH1b: true,
    skills: ["Java"],
    summary: "Do the job.",
    role: "Backend",
    postedAt: "2026-11-10T09:00:00Z",
    source: source as import("@/lib/types").JobSource,
    sourceUrl,
  };
}

beforeEach(() => {
  navMock.params = new URLSearchParams();
  navMock.replace.mockClear();
  useAuthStore.getState().logout();
  useAuthStore.getState().login({
    userId: "user-1",
    email: "dev@example.com",
    role: "USER",
    accessToken: "a.b.c",
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  useAuthStore.getState().logout();
});

describe("resume selection helpers", () => {
  it("defaults to the latest READY resume", () => {
    expect(latestReadyResume(RESUMES)?.id).toBe("resume-2");
    expect(resolveSelectedResumeId(RESUMES, null)).toBe("resume-2");
    expect(resolveSelectedResumeId(RESUMES, "")).toBe("resume-2");
  });

  it("honours an explicit ?resumeId= that names a real resume", () => {
    expect(resolveSelectedResumeId(RESUMES, "resume-1")).toBe("resume-1");
    // Even a not-READY resume wins, so the backend 400 surfaces.
    expect(resolveSelectedResumeId(RESUMES, "resume-3")).toBe("resume-3");
  });

  it("falls back to the latest READY resume for an unknown param", () => {
    expect(resolveSelectedResumeId(RESUMES, "resume-zzz")).toBe("resume-2");
    expect(resolveSelectedResumeId([], null)).toBeNull();
    expect(latestReadyResume([])).toBeNull();
  });

  it("takes the top match score per resume", () => {
    expect(topMatchScoreForResume([{ score: 70 }, { score: 92 }])).toBe(92);
    expect(topMatchScoreForResume([])).toBeNull();
    expect(topMatchScoreForResume(undefined)).toBeNull();
  });
});

describe("job source helpers", () => {
  it("labels every source and builds the apply-link text", () => {
    expect(jobSourceLabel("LINKEDIN")).toBe("LinkedIn");
    expect(jobSourceLabel("INDEED")).toBe("Indeed");
    expect(jobSourceLabel("MONSTER")).toBe("Monster");
    expect(jobSourceLabel("NAUKRI")).toBe("Naukri");
    expect(jobSourceLabel("COMPANY")).toBe("Company site");
    expect(jobSourceLabel(undefined)).toBe("Seed");
    expect(applyLinkLabel("LINKEDIN")).toBe("Apply on LinkedIn");
    expect(applyLinkLabel("COMPANY")).toBe("Apply on company site");
    expect(workAuthorizationLabel("H1B")).toBe("H-1B");
    expect(workAuthorizationLabel("US_CITIZEN")).toBe("US Citizen");
    expect(workAuthorizationLabel(null)).toBe("Prefer not to say");
  });

  it("adds the source filter to the jobs query", () => {
    expect(buildJobsQuery({ source: "LINKEDIN" })).toBe("?source=LINKEDIN");
    expect(buildJobsQuery({ source: "ALL" })).toBe("");
    expect(buildJobsQuery({ query: "java", source: "INDEED" })).toBe(
      "?query=java&source=INDEED",
    );
  });
});

describe("useMatches with resumeId", () => {
  it("fetches /matches/me?resumeId= and refetches when the resume changes", async () => {
    const urls: string[] = [];
    const fetchMock = mockFetch((url) => {
      urls.push(url);
      const resumeId = new URL(`http://x${url}`).searchParams.get("resumeId");
      const matches =
        resumeId === "resume-1"
          ? [{ job: job("job-1", "Android Engineer", "LINKEDIN", "https://www.linkedin.com/jobs/search/?keywords=Android"), score: 88, reasons: [], gaps: [], source: "RESUME" }]
          : [{ job: job("job-2", "Java Backend Engineer", "INDEED", null), score: 91, reasons: [], gaps: [], source: "RESUME" }];
      return jsonResponse(matches);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result, rerender } = renderHook(
      ({ resumeId }: { resumeId: string | null }) => useMatches(resumeId),
      { wrapper, initialProps: { resumeId: "resume-1" } },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0].job.title).toBe("Android Engineer");

    rerender({ resumeId: "resume-2" });
    await waitFor(() =>
      expect(result.current.data?.[0].job.title).toBe("Java Backend Engineer"),
    );
    expect(urls).toContain("/api/matches/me?resumeId=resume-1");
    expect(urls).toContain("/api/matches/me?resumeId=resume-2");
  });

  it("fetches the bare URL with no resumeId (latest READY, unchanged)", async () => {
    const fetchMock = mockFetch((url) => {
      expect(url).toBe("/api/matches/me");
      return jsonResponse([]);
    });
    vi.stubGlobal("fetch", fetchMock);
    await expect(fetchMatches()).resolves.toEqual([]);
  });
});

describe("resume label upload + rename", () => {
  it("POSTs the label as a multipart field with the file", async () => {
    const fetchMock = mockFetch((_url, init) => {
      expect(init?.method).toBe("POST");
      const form = init?.body as FormData;
      expect(form.get("label")).toBe("Android Developer");
      expect((form.get("file") as File).name).toBe("android.pdf");
      return jsonResponse({ id: "resume-9", label: "Android Developer", fileName: "android.pdf", status: "PROCESSING" }, 201);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useUploadResume(), { wrapper });
    const file = new File(["%PDF"], "android.pdf", { type: "application/pdf" });
    const created = await result.current.mutateAsync({ file, label: "Android Developer" });
    expect(created.label).toBe("Android Developer");
  });

  it("PUTs {label} to /resumes/{id} to rename", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/resumes/resume-1");
      expect(init?.method).toBe("PUT");
      expect(init?.body).toBe(JSON.stringify({ label: "Java Backend" }));
      return jsonResponse({ id: "resume-1", label: "Java Backend", fileName: "java.pdf", status: "READY" });
    });
    vi.stubGlobal("fetch", fetchMock);

    const updated = await renameResume("resume-1", "Java Backend");
    expect(updated.label).toBe("Java Backend");
  });
});

describe("JobSource component", () => {
  it("renders the source badge and an apply link when sourceUrl is set", () => {
    render(
      <JobSource
        job={job("job-1", "Android Engineer", "LINKEDIN", "https://www.linkedin.com/jobs/search/?keywords=Android")}
      />,
    );
    expect(screen.getByText("LinkedIn")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: /Apply on LinkedIn/ });
    expect(link).toHaveAttribute(
      "href",
      "https://www.linkedin.com/jobs/search/?keywords=Android",
    );
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("renders the badge only — no link — when sourceUrl is null", () => {
    render(
      <JobSource job={job("job-2", "Java Backend Engineer", "INDEED", null)} />,
    );
    expect(screen.getByText("Indeed")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});

describe("Matches page resume selector", () => {
  function stubMatchesPage() {
    const fetchMock = mockFetch((url) => {
      if (url === "/api/resumes/me") return jsonResponse(RESUMES);
      if (url.startsWith("/api/matches/me")) {
        const resumeId = new URL(`http://x${url}`).searchParams.get("resumeId");
        if (resumeId === "resume-1") {
          return jsonResponse([
            { job: job("job-1", "Android Engineer", "LINKEDIN", "https://www.linkedin.com/jobs/search/?keywords=Android"), score: 88, reasons: [{ label: "Skills: Kotlin (+38)", points: 38 }], gaps: ["Missing skill: Compose"], source: "RESUME" },
          ]);
        }
        return jsonResponse([
          { job: job("job-2", "Java Backend Engineer", "INDEED", null), score: 91, reasons: [{ label: "Skills: Java (+38)", points: 38 }], gaps: [], source: "RESUME" },
        ]);
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
  }

  it("loads matches for the ?resumeId= resume and lists resumes by label", async () => {
    navMock.params = new URLSearchParams("resumeId=resume-1");
    stubMatchesPage();
    render(<MatchesPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByText("Android Engineer")).toBeInTheDocument(),
    );
    expect(screen.getByText("88%")).toBeInTheDocument();
    expect(screen.getByText("LinkedIn")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Apply on LinkedIn/ })).toBeInTheDocument();
    const select = screen.getByLabelText("Resume") as HTMLSelectElement;
    expect(select.value).toBe("resume-1");
    expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Java Backend" })).toBeInTheDocument();
  });

  it("defaults to the latest READY resume and switching updates the URL", async () => {
    stubMatchesPage();
    const user = userEvent.setup();
    render(<MatchesPage />, { wrapper });

    // No ?resumeId= → latest READY (resume-2, Java Backend).
    await waitFor(() =>
      expect(screen.getByText("Java Backend Engineer")).toBeInTheDocument(),
    );
    expect(screen.getByText("91%")).toBeInTheDocument();
    // Indeed job has no sourceUrl: badge only, no apply link.
    expect(screen.getByText("Indeed")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Apply on/ })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Resume"), "resume-1");
    expect(navMock.replace).toHaveBeenCalledWith(
      expect.stringContaining("resumeId=resume-1"),
      expect.anything(),
    );
  });
});

describe("Profile page: LinkedIn URL + work authorisation", () => {
  const PROFILE = {
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
    skills: ["Java"],
    h1bClockStartDate: null,
    linkedinUrl: "https://www.linkedin.com/in/dev-example",
    workAuthorization: "H1B",
    completeness: 100,
    createdAt: "2026-10-13T00:00:00.000Z",
    updatedAt: "2026-11-10T00:00:00.000Z",
  };

  function stubProfilePage(putImpl?: (body: Record<string, unknown>) => Response) {
    const fetchMock = mockFetch((url, init) => {
      if (url === "/api/profiles/me" && init?.method !== "PUT") return jsonResponse(PROFILE);
      if (url === "/api/profiles/me" && init?.method === "PUT") {
        const body = JSON.parse(init.body as string) as Record<string, unknown>;
        if (putImpl) return putImpl(body);
        return jsonResponse({ ...PROFILE, ...body });
      }
      if (url === "/api/resumes/me") return jsonResponse(RESUMES.slice(0, 1));
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
  }

  it("shows the LinkedIn URL as a link and the authorisation, and round-trips both on save", async () => {
    let savedBody: Record<string, unknown> | null = null;
    stubProfilePage((body) => {
      savedBody = body;
      return jsonResponse({ ...PROFILE, ...body });
    });
    const user = userEvent.setup();
    render(<ProfilePage />, { wrapper });

    // At-a-glance: clickable LinkedIn link + H-1B label.
    await waitFor(() =>
      expect(screen.getByRole("link", { name: "https://www.linkedin.com/in/dev-example" })).toBeInTheDocument(),
    );
    // "H-1B" appears both in the at-a-glance value and in the select option.
    expect(screen.getAllByText("H-1B").length).toBeGreaterThanOrEqual(1);

    // Form prefilled from the profile; change both fields and save.
    const urlInput = screen.getByLabelText("LinkedIn URL") as HTMLInputElement;
    expect(urlInput.value).toBe("https://www.linkedin.com/in/dev-example");
    await user.clear(urlInput);
    await user.type(urlInput, "https://www.linkedin.com/in/dev-new");
    await user.selectOptions(screen.getByLabelText("Work authorisation"), "GREEN_CARD");
    await user.click(screen.getByRole("button", { name: "Save profile" }));

    await waitFor(() => expect(savedBody).not.toBeNull());
    const body = savedBody as unknown as Record<string, unknown>;
    expect(body.linkedinUrl).toBe("https://www.linkedin.com/in/dev-new");
    expect(body.workAuthorization).toBe("GREEN_CARD");
  });

  it("shows the API validation message when the backend rejects the URL", async () => {
    stubProfilePage(() =>
      jsonResponse({ message: "INVALID_LINKEDIN_URL" }, 400),
    );
    const user = userEvent.setup();
    render(<ProfilePage />, { wrapper });
    await waitFor(() =>
      expect(screen.getByLabelText("LinkedIn URL")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Save profile" }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("INVALID_LINKEDIN_URL"),
    );
  });

  it("updateProfile sends the two new fields in the PUT body", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/profiles/me");
      expect(init?.method).toBe("PUT");
      expect(JSON.parse(init?.body as string)).toMatchObject({
        linkedinUrl: "https://www.linkedin.com/in/dev-example",
        workAuthorization: "EAD",
      });
      return jsonResponse(PROFILE);
    });
    vi.stubGlobal("fetch", fetchMock);
    await updateProfile({
      headline: "x",
      linkedinUrl: "https://www.linkedin.com/in/dev-example",
      workAuthorization: "EAD",
    });
    expect(fetchMock).toHaveBeenCalled();
  });
});
