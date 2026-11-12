// W2 API client + helper tests. fetch is mocked at the BFF boundary
// (/api/*) — the real backend is never involved.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import {
  answerInterviewQuestion,
  applyForJob,
  deleteTrackerEntry,
  saveJob,
  useJobs,
  useMatches,
  useTracker,
} from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import { buildJobsQuery, formatPostedAt, formatSalary, matchSourceLabel } from "@/lib/jobs";

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

const JOB = {
  id: "job-1",
  title: "Senior Software Engineer, Backend (Java)",
  company: "Northwind Health Systems",
  location: "Reston, VA",
  workType: "Hybrid",
  salaryMin: 145000,
  salaryMax: 175000,
  currency: "USD",
  sponsorsH1b: true,
  skills: ["Java 21", "Spring Boot"],
  summary: "Build Spring Boot microservices.",
  role: "Backend",
  postedAt: "2026-11-10T09:00:00Z",
};

beforeEach(() => {
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

describe("jobs helpers", () => {
  it("formats salary and posted dates", () => {
    expect(formatSalary(145000, 175000)).toBe("$145k – $175k");
    expect(formatPostedAt("2026-11-10T09:00:00Z", new Date("2026-11-12T09:00:00Z"))).toBe("Posted 2d ago");
    expect(formatPostedAt("2026-11-12T09:00:00Z", new Date("2026-11-12T10:00:00Z"))).toBe("Posted today");
    expect(formatPostedAt("not-a-date")).toBe("Posted recently");
  });

  it("labels match sources", () => {
    expect(matchSourceLabel("RESUME")).toBe("Matched on your resume");
    expect(matchSourceLabel("PROFILE")).toContain("no resume yet");
  });

  it("builds the jobs query string with only set filters", () => {
    expect(buildJobsQuery({})).toBe("");
    expect(buildJobsQuery({ query: "java", location: "Reston, VA", workType: "Hybrid", sponsorsH1b: true, page: 0, size: 20 }))
      .toBe("?query=java&location=Reston%2C+VA&workType=Hybrid&sponsorsH1b=true&page=0&size=20");
    expect(buildJobsQuery({ workType: "ALL" })).toBe("");
  });
});

describe("useJobs / useMatches / useTracker", () => {
  it("GETs the jobs page with the filter query", async () => {
    const fetchMock = mockFetch((url) => {
      if (url.startsWith("/api/jobs")) {
        expect(url).toBe("/api/jobs?query=java&sponsorsH1b=true&size=20");
        return jsonResponse({ content: [JOB], page: 0, size: 20, totalElements: 1, totalPages: 1 });
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(
      () => useJobs({ query: "java", sponsorsH1b: true, size: 20 }),
      { wrapper },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content[0].workType).toBe("Hybrid");
    expect(result.current.data?.content[0].sponsorsH1b).toBe(true);
    expect(result.current.data?.totalElements).toBe(1);
  });

  it("GETs matches with reasons, gaps and source", async () => {
    const fetchMock = mockFetch((url) => {
      if (url === "/api/matches/me") {
        return jsonResponse([
          { job: JOB, score: 82, reasons: [{ label: "Skills: Java 21 (+38)", points: 38 }], gaps: ["Missing skill: Azure"], source: "RESUME" },
        ]);
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useMatches(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0].score).toBe(82);
    expect(result.current.data?.[0].reasons[0].points).toBe(38);
    expect(result.current.data?.[0].source).toBe("RESUME");
  });

  it("GETs the tracker list", async () => {
    const fetchMock = mockFetch((url) => {
      if (url === "/api/tracker") {
        return jsonResponse([
          { id: "trk-1", jobId: "job-1", title: JOB.title, company: JOB.company, resumeId: "resume-1", status: "SAVED", notes: null, createdAt: "2026-11-11T00:00:00Z", updatedAt: "2026-11-11T00:00:00Z" },
        ]);
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useTracker(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0].status).toBe("SAVED");
  });
});

describe("saveJob", () => {
  it("POSTs the job and returns the tracker entry", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/tracker");
      expect(init?.method).toBe("POST");
      expect(init?.body).toBe(JSON.stringify({ jobId: "job-1", resumeId: "resume-1" }));
      return jsonResponse({ id: "trk-1", jobId: "job-1", status: "SAVED" }, 201);
    });
    vi.stubGlobal("fetch", fetchMock);

    const entry = await saveJob("job-1", "resume-1");
    expect(entry.id).toBe("trk-1");
  });

  it("on 409 returns the existing entry from the tracker list", async () => {
    const fetchMock = mockFetch((url, init) => {
      if (init?.method === "POST") return jsonResponse({ message: "duplicate" }, 409);
      return jsonResponse([{ id: "trk-9", jobId: "job-1", status: "SAVED" }]);
    });
    vi.stubGlobal("fetch", fetchMock);

    const entry = await saveJob("job-1");
    expect(entry.id).toBe("trk-9");
  });
});

describe("applyForJob (contract §6)", () => {
  it("saves with the first READY resume, then proposes APPLY_JOB", async () => {
    const calls: { url: string; body?: string }[] = [];
    const fetchMock = mockFetch((url, init) => {
      calls.push({ url, body: typeof init?.body === "string" ? init.body : undefined });
      if (url === "/api/resumes/me") {
        return jsonResponse([
          { id: "resume-1", fileName: "r.pdf", fileType: "application/pdf", fileSize: 1, status: "READY", createdAt: "2026-11-11T00:00:00Z" },
        ]);
      }
      if (url === "/api/tracker") {
        return jsonResponse({ id: "trk-1", jobId: "job-1", status: "SAVED" }, 201);
      }
      if (url === "/api/approvals") {
        const body = JSON.parse((init?.body as string) ?? "{}") as { action: string; payload: string };
        expect(body.action).toBe("APPLY_JOB");
        expect(JSON.parse(body.payload)).toEqual({ jobId: "job-1", resumeId: "resume-1", trackerId: "trk-1" });
        return jsonResponse({ id: "appr-1", action: "APPLY_JOB", payload: body.payload, status: "PENDING" }, 201);
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    const approval = await applyForJob("job-1");
    expect(approval.action).toBe("APPLY_JOB");
    expect(calls.map((c) => c.url)).toEqual(["/api/resumes/me", "/api/tracker", "/api/approvals"]);
  });

  it("omits resumeId (null) when no READY resume exists", async () => {
    const fetchMock = mockFetch((url) => {
      if (url === "/api/resumes/me") return jsonResponse([]);
      if (url === "/api/tracker") return jsonResponse({ id: "trk-1", jobId: "job-1", status: "SAVED" }, 201);
      if (url === "/api/approvals") return jsonResponse({ id: "appr-1", action: "APPLY_JOB", status: "PENDING" }, 201);
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    await applyForJob("job-1");
    const approvalCall = (fetchMock.mock.calls as [string, RequestInit?][]).find(
      ([url]) => url === "/api/approvals",
    );
    const payload = JSON.parse(
      (JSON.parse(approvalCall?.[1]?.body as string).payload as string),
    ) as Record<string, unknown>;
    expect(payload.resumeId).toBeNull();
  });
});

describe("interviews + tracker delete", () => {
  it("POSTs an answer and returns the rubric-scored answer", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/interviews/sessions/sess-1/answers");
      expect(init?.method).toBe("POST");
      return jsonResponse({
        id: "ans-1",
        questionId: "q-1",
        questionText: "Tell me about a time…",
        answerText: "In my last role…",
        scores: { structure: 7, relevance: 8, specificity: 6, conciseness: 9 },
        overallScore: 75,
        feedback: ["Add a concrete number or outcome (e.g. users impacted, latency reduced).", "Well-paced answer."],
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    const answer = await answerInterviewQuestion("sess-1", { questionId: "q-1", answerText: "In my last role…" });
    expect(answer.overallScore).toBe(75);
    expect(answer.scores.relevance).toBe(8);
    expect(answer.feedback).toHaveLength(2);
  });

  it("DELETEs a tracker entry (204)", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/tracker/trk-1");
      expect(init?.method).toBe("DELETE");
      return new Response(null, { status: 204 });
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(deleteTrackerEntry("trk-1")).resolves.toBeUndefined();
  });
});
