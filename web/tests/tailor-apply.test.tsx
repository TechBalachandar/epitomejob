// Feature 2 — Tailor + Apply: pure diff/state logic plus one modal
// happy-path test against a mocked BFF (fetch mocked at /api/*).
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import {
  buildTailoredState,
  diffLines,
  ensureTrackerApplied,
  jobDescriptionText,
  sectionDiffs,
  tailorTargetsFor,
  tailoredTextForOption,
} from "@/lib/tailor-apply";
import { TailorApplyModal } from "@/components/jobs/tailor-apply-modal";
import type {
  CopilotOption,
  Job,
  ResumeBuilderState,
} from "@/lib/types";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function state(overrides: Partial<ResumeBuilderState> = {}): ResumeBuilderState {
  return {
    contact: { fullName: "Alex", title: "Engineer", email: "", phone: "", location: "", linkedin: "" },
    summary: "Senior Java engineer with ten years of experience.",
    experience: [
      {
        id: "exp-1",
        title: "Senior Engineer",
        company: "Acme",
        startDate: "2020-01",
        endDate: "Present",
        bullets: ["Built payment APIs.", "Led a team of four."],
      },
    ],
    education: [],
    skills: ["Java", "Spring Boot"],
    ...overrides,
  };
}

function job(overrides: Partial<Job> = {}): Job {
  return {
    id: "job-1",
    title: "Senior AI Engineer",
    company: "Acme",
    location: "Reston, VA",
    workType: "Hybrid",
    salaryMin: 150000,
    salaryMax: 180000,
    currency: "USD",
    sponsorsH1b: true,
    skills: ["Java", "Spring Boot"],
    summary: "Build agentic AI services on Spring Boot.",
    role: "AI",
    postedAt: "2026-11-16T00:00:00Z",
    source: "SEED",
    sourceUrl: null,
    ...overrides,
  };
}

function option(overrides: Partial<CopilotOption> = {}): CopilotOption {
  return {
    id: "opt-1",
    angle: "Impact-led",
    bullets: ["Built payment APIs serving 2M users."],
    atsKeywords: ["Java"],
    ...overrides,
  };
}

describe("diffLines", () => {
  it("marks unchanged lines same and changed lines added/removed", () => {
    const lines = diffLines("Alpha.\nBeta.\nGamma.", "Alpha.\nBeta v2.\nGamma.");
    expect(lines.map((l) => l.type)).toEqual([
      "same",
      "removed",
      "added",
      "same",
    ]);
    expect(lines[1]?.text).toBe("Beta.");
    expect(lines[2]?.text).toBe("Beta v2.");
  });

  it("returns no changes for identical text", () => {
    const lines = diffLines("Same text.", "Same text.");
    expect(lines).toEqual([{ type: "same", text: "Same text." }]);
  });

  it("splits a single-line summary into sentences", () => {
    const lines = diffLines(
      "First sentence. Second sentence.",
      "First sentence. Second sentence rewritten.",
    );
    expect(lines.map((l) => l.type)).toEqual(["same", "removed", "added"]);
  });

  it("handles empty before/after", () => {
    expect(diffLines("", "")).toEqual([]);
    expect(diffLines("", "New.")).toEqual([{ type: "added", text: "New." }]);
  });
});

describe("tailorTargetsFor", () => {
  it("targets the summary and every experience item with bullets", () => {
    const targets = tailorTargetsFor(state());
    expect(targets.map((t) => t.label)).toEqual(["Summary", "Experience 1"]);
    expect(targets[0]?.originalText).toContain("Senior Java engineer");
  });

  it("skips empty sections", () => {
    const targets = tailorTargetsFor(
      state({ summary: "  ", experience: [] }),
    );
    expect(targets).toEqual([]);
  });
});

describe("tailored text and state", () => {
  it("jobDescriptionText carries title, company, summary and skills", () => {
    const jd = jobDescriptionText(job());
    expect(jd).toContain("Senior AI Engineer at Acme");
    expect(jd).toContain("Build agentic AI services");
    expect(jd).toContain("Skills: Java, Spring Boot");
  });

  it("tailoredTextForOption joins summary bullets as prose", () => {
    expect(
      tailoredTextForOption(
        { section: "summary", label: "Summary" },
        option({ bullets: ["One.", "Two."] }),
      ),
    ).toBe("One. Two.");
  });

  it("buildTailoredState applies the chosen options", () => {
    const next = buildTailoredState(state(), [
      { target: { section: "summary", label: "Summary" }, option: option({ bullets: ["New summary."] }) },
      { target: { section: "experience", index: 0, label: "Experience 1" }, option: option({ id: "opt-2", bullets: ["New bullet."] }) },
    ]);
    expect(next.summary).toBe("New summary.");
    expect(next.experience[0]?.bullets).toEqual(["New bullet."]);
  });

  it("sectionDiffs compares the real original and tailored text", () => {
    const diffs = sectionDiffs(state(), [
      { target: { section: "summary", label: "Summary" }, option: option({ bullets: ["Rewritten summary."] }) },
    ]);
    expect(diffs).toHaveLength(1);
    expect(diffs[0]?.originalText).toContain("Senior Java engineer");
    expect(diffs[0]?.tailoredText).toBe("Rewritten summary.");
    expect(diffs[0]?.unchanged).toBe(false);
    expect(diffs[0]?.lines.some((l) => l.type === "removed")).toBe(true);
    expect(diffs[0]?.lines.some((l) => l.type === "added")).toBe(true);
  });
});

describe("ensureTrackerApplied (post-approval step only)", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  it("flips an existing entry to APPLIED without creating one", async () => {
    fetchMock.mockImplementation((input: string | URL | Request, init?: RequestInit) => {
      const url = input.toString();
      if (url === "/api/tracker" && !init?.method) {
        return Promise.resolve(
          json([{ id: "trk-1", jobId: "job-1", title: "T", company: "C", resumeId: null, status: "SAVED", notes: null, createdAt: "", updatedAt: "" }]),
        );
      }
      if (url === "/api/tracker/trk-1" && init?.method === "PUT") {
        expect(JSON.parse(String(init.body))).toEqual({ status: "APPLIED" });
        return Promise.resolve(json({ id: "trk-1", status: "APPLIED" }));
      }
      throw new Error(`unexpected fetch ${url}`);
    });
    await ensureTrackerApplied("job-1");
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/tracker"),
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("creates the entry first when none exists, then flips it", async () => {
    const calls: string[] = [];
    fetchMock.mockImplementation((input: string | URL | Request, init?: RequestInit) => {
      const url = input.toString();
      calls.push(`${init?.method ?? "GET"} ${url}`);
      if (url === "/api/tracker" && !init?.method) return Promise.resolve(json([]));
      if (url === "/api/tracker" && init?.method === "POST") {
        return Promise.resolve(json({ id: "trk-9", jobId: "job-1", status: "SAVED" }));
      }
      if (url === "/api/tracker/trk-9" && init?.method === "PUT") {
        return Promise.resolve(json({ id: "trk-9", status: "APPLIED" }));
      }
      throw new Error(`unexpected fetch ${url}`);
    });
    await ensureTrackerApplied("job-1");
    expect(calls).toEqual([
      "GET /api/tracker",
      "POST /api/tracker",
      "PUT /api/tracker/trk-9",
    ]);
  });
});

describe("TailorApplyModal", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  const doc = {
    id: "doc-1",
    templateId: "modern",
    title: "My Resume",
    state: state(),
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  };

  function mockHappyPath() {
    fetchMock.mockImplementation((input: string | URL | Request, init?: RequestInit) => {
      const url = input.toString();
      const method = init?.method ?? "GET";
      if (url === "/api/resume-builder/documents" && method === "GET") {
        return Promise.resolve(json([doc]));
      }
      if (url.includes("/copilot") && method === "POST") {
        return Promise.resolve(
          json({
            options: [
              {
                id: "opt-1",
                angle: "Impact-led",
                bullets: ["Built payment APIs serving 2M users."],
                atsKeywords: ["Java"],
              },
            ],
          }),
        );
      }
      if (url === "/api/resume-builder/documents/doc-1" && method === "PUT") {
        return Promise.resolve(json(doc));
      }
      if (url === "/api/resume-builder/documents/doc-1/save-to-profile" && method === "POST") {
        return Promise.resolve(json({ resumeId: "resume-9", label: "Tailored" }));
      }
      if (url === "/api/tracker" && method === "POST") {
        return Promise.resolve(
          json({ id: "trk-1", jobId: "job-1", status: "SAVED" }),
        );
      }
      if (url === "/api/approvals" && method === "POST") {
        return Promise.resolve(json({ id: "appr-1", action: "APPLY_JOB", status: "PENDING" }));
      }
      throw new Error(`unexpected fetch ${method} ${url}`);
    });
  }

  it("tailors, shows the real diff, and requests approval via the real apply path", async () => {
    mockHappyPath();
    const user = userEvent.setup();
    render(
      <TailorApplyModal job={job()} open onClose={() => {}} />,
      { wrapper },
    );

    await waitFor(() =>
      expect(screen.getByLabelText("Resume")).toBeInTheDocument(),
    );
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.click(screen.getByRole("button", { name: "Tailor with Co-Pilot" }));

    // Real diff: original summary shown as removed, tailored bullet as added.
    // Two sections diffed (summary + experience), so two diff views.
    const diffs = await waitFor(() =>
      screen.getAllByTestId("tailor-diff"),
    );
    expect(diffs).toHaveLength(2);
    const diffText = diffs.map((d) => d.textContent).join(" ");
    expect(diffText).toContain("Senior Java engineer with ten years of experience.");
    expect(diffText).toContain("Built payment APIs serving 2M users.");

    await user.click(
      screen.getByRole("button", { name: "Save tailored resume & request apply" }),
    );
    await waitFor(() =>
      expect(screen.getByText(/Approval requested for/)).toBeInTheDocument(),
    );
    // The approval-gated apply was proposed (tracker create + approval),
    // and the modal says the tracker flips on approval — not before.
    expect(screen.getByText(/moves this job to Applied automatically/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/approvals",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("shows an error and changes nothing when Co-Pilot fails everywhere", async () => {
    fetchMock.mockImplementation((input: string | URL | Request, init?: RequestInit) => {
      const url = input.toString();
      if (url === "/api/resume-builder/documents") {
        return Promise.resolve(json([doc]));
      }
      if (url.includes("/copilot")) {
        return Promise.resolve(json({ message: "Co-Pilot down" }, 500));
      }
      throw new Error(`unexpected fetch ${url}`);
    });
    const user = userEvent.setup();
    render(
      <TailorApplyModal job={job()} open onClose={() => {}} />,
      { wrapper },
    );
    await waitFor(() =>
      expect(screen.getByLabelText("Resume")).toBeInTheDocument(),
    );
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.click(screen.getByRole("button", { name: "Tailor with Co-Pilot" }));
    await waitFor(() =>
      expect(
        screen.getByText(/could not tailor any section/i),
      ).toBeInTheDocument(),
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      "/api/approvals",
      expect.anything(),
    );
  });
});

describe("splitDiffUnits", () => {
  it("keeps multi-line text as lines", async () => {
    const { splitDiffUnits } = await import("@/lib/tailor-apply");
    expect(splitDiffUnits("One.\nTwo.\nThree.")).toEqual(["One.", "Two.", "Three."]);
  });

  it("drops blank lines and trims whitespace", async () => {
    const { splitDiffUnits } = await import("@/lib/tailor-apply");
    expect(splitDiffUnits("  Alpha.  \n\n  Beta. \n")).toEqual(["Alpha.", "Beta."]);
  });

  it("splits a single line on ? and ! as well as .", async () => {
    const { splitDiffUnits } = await import("@/lib/tailor-apply");
    expect(splitDiffUnits("Really? Yes! Done.")).toEqual(["Really?", "Yes!", "Done."]);
  });

  it("does not sentence-split a single sentence with no terminator", async () => {
    const { splitDiffUnits } = await import("@/lib/tailor-apply");
    expect(splitDiffUnits("Just a phrase")).toEqual(["Just a phrase"]);
  });

  it("returns no units for empty or whitespace-only text", async () => {
    const { splitDiffUnits } = await import("@/lib/tailor-apply");
    expect(splitDiffUnits("")).toEqual([]);
    expect(splitDiffUnits("   \n  ")).toEqual([]);
  });
});

describe("diffLines — adversarial", () => {
  it("marks a pure insertion and a pure deletion", () => {
    expect(diffLines("Alpha.\nGamma.", "Alpha.\nBeta.\nGamma.")).toEqual([
      { type: "same", text: "Alpha." },
      { type: "added", text: "Beta." },
      { type: "same", text: "Gamma." },
    ]);
    expect(diffLines("Alpha.\nBeta.\nGamma.", "Alpha.\nGamma.")).toEqual([
      { type: "same", text: "Alpha." },
      { type: "removed", text: "Beta." },
      { type: "same", text: "Gamma." },
    ]);
  });

  it("treats reordered lines as removed + added (LCS, no move detection)", () => {
    const lines = diffLines("A.\nB.\nC.", "C.\nB.\nA.");
    expect(lines.filter((l) => l.type === "same")).toHaveLength(1);
    expect(lines.filter((l) => l.type === "removed")).toHaveLength(2);
    expect(lines.filter((l) => l.type === "added")).toHaveLength(2);
  });

  it("handles duplicate lines without collapsing them", () => {
    const lines = diffLines("Same.\nSame.", "Same.\nSame.\nSame.");
    expect(lines).toEqual([
      { type: "same", text: "Same." },
      { type: "same", text: "Same." },
      { type: "added", text: "Same." },
    ]);
  });

  it("trims surrounding whitespace, so padded text is unchanged", () => {
    expect(diffLines("  Alpha.  ", "Alpha.")).toEqual([
      { type: "same", text: "Alpha." },
    ]);
  });

  it("is case-sensitive: a case change is a real change", () => {
    const lines = diffLines("Alpha.", "ALPHA.");
    expect(lines.map((l) => l.type)).toEqual(["removed", "added"]);
  });

  it("handles a very long single-line paragraph sentence by sentence", () => {
    const before = Array.from({ length: 50 }, (_, i) => `Sentence ${i}.`).join(" ");
    const after = before.replace("Sentence 25.", "Sentence 25 rewritten.");
    const lines = diffLines(before, after);
    expect(lines.filter((l) => l.type === "same")).toHaveLength(49);
    expect(lines.some((l) => l.type === "removed" && l.text === "Sentence 25.")).toBe(true);
    expect(lines.some((l) => l.type === "added" && l.text === "Sentence 25 rewritten.")).toBe(true);
  });

  it("handles special characters and unicode without mangling", () => {
    const lines = diffLines("C++ & C# — 100%.", "C++ & C# — 100%!");
    expect(lines.map((l) => l.type)).toEqual(["removed", "added"]);
    expect(lines[1].text).toBe("C++ & C# — 100%!");
  });
});

describe("tailorTargetsFor — adversarial", () => {
  it("skips an experience item whose bullets are all blank", () => {
    const targets = tailorTargetsFor(
      state({
        summary: "",
        experience: [
          {
            id: "exp-1",
            title: "Engineer",
            company: "Acme",
            startDate: "2020-01",
            endDate: "Present",
            bullets: ["   ", ""],
          },
        ],
      }),
    );
    expect(targets).toEqual([]);
  });

  it("labels multiple experience items in order and keeps a blank-bullet item out", () => {
    const targets = tailorTargetsFor(
      state({
        summary: "",
        experience: [
          { id: "e1", title: "A", company: "Acme", startDate: "", endDate: "", bullets: ["Did A."] },
          { id: "e2", title: "B", company: "Beta", startDate: "", endDate: "", bullets: ["  "] },
          { id: "e3", title: "C", company: "Gamma", startDate: "", endDate: "", bullets: ["Did C."] },
        ],
      }),
    );
    expect(targets.map((t) => t.label)).toEqual(["Experience 1", "Experience 3"]);
  });

  it("includes the experience context (title, company, bullets) as original text", () => {
    const targets = tailorTargetsFor(state());
    const exp = targets.find((t) => t.label === "Experience 1");
    expect(exp?.originalText).toContain("Senior Engineer");
    expect(exp?.originalText).toContain("Acme");
    expect(exp?.originalText).toContain("Built payment APIs.");
  });
});

describe("jobDescriptionText — edge cases", () => {
  it("omits the Skills line when the job has no skills", () => {
    const jd = jobDescriptionText(job({ skills: [], summary: "Do things." }));
    expect(jd).not.toContain("Skills:");
    expect(jd).toContain("Do things.");
  });

  it("omits the empty summary line", () => {
    const jd = jobDescriptionText(job({ summary: "" }));
    expect(jd).toContain("Senior AI Engineer at Acme");
    expect(jd.split("\n")).toHaveLength(3);
  });

  it("preserves special characters in title and company", () => {
    const jd = jobDescriptionText(
      job({ title: "C++ Engineer (R&D)", company: "A&B <Corp>" }),
    );
    expect(jd).toContain("C++ Engineer (R&D) at A&B <Corp>");
  });
});

describe("tailored text and state — adversarial", () => {
  it("tailoredTextForOption joins experience bullets with newlines", () => {
    expect(
      tailoredTextForOption(
        { section: "experience", index: 0, label: "Experience 1" },
        option({ bullets: ["One.", "Two."] }),
      ),
    ).toBe("One.\nTwo.");
  });

  it("tailoredTextForOption returns empty for an option with no bullets", () => {
    expect(
      tailoredTextForOption(
        { section: "summary", label: "Summary" },
        option({ bullets: [] }),
      ),
    ).toBe("");
  });

  it("buildTailoredState does not mutate the original state", () => {
    const original = state();
    const before = JSON.stringify(original);
    buildTailoredState(original, [
      { target: { section: "summary", label: "Summary" }, option: option({ bullets: ["New summary."] }) },
    ]);
    expect(JSON.stringify(original)).toBe(before);
  });

  it("buildTailoredState with no picks returns an equivalent state", () => {
    const original = state();
    expect(buildTailoredState(original, [])).toEqual(original);
  });

  it("buildTailoredState ignores an experience pick whose index does not exist", () => {
    const next = buildTailoredState(state(), [
      { target: { section: "experience", index: 9, label: "Experience 10" }, option: option({ bullets: ["Ghost."] }) },
    ]);
    expect(next).toEqual(state());
  });

  it("sectionDiffs flags an unchanged section when Co-Pilot returns identical text", () => {
    const diffs = sectionDiffs(state(), [
      {
        target: { section: "summary", label: "Summary" },
        option: option({ bullets: ["Senior Java engineer with ten years of experience."] }),
      },
    ]);
    expect(diffs).toHaveLength(1);
    expect(diffs[0]?.unchanged).toBe(true);
    expect(diffs[0]?.lines.every((l) => l.type === "same")).toBe(true);
  });

  it("sectionDiffs handles multiple picks in one pass", () => {
    const diffs = sectionDiffs(state(), [
      { target: { section: "summary", label: "Summary" }, option: option({ bullets: ["New summary."] }) },
      { target: { section: "experience", index: 0, label: "Experience 1" }, option: option({ id: "opt-2", bullets: ["New bullet."] }) },
    ]);
    expect(diffs.map((d) => d.label)).toEqual(["Summary", "Experience 1"]);
    expect(diffs[1]?.tailoredText).toBe("New bullet.");
    expect(diffs.every((d) => !d.unchanged)).toBe(true);
  });
});

describe("ensureTrackerApplied — adversarial", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  it("does nothing when the entry is already APPLIED", async () => {
    fetchMock.mockImplementation((input: string | URL | Request) => {
      if (input.toString() === "/api/tracker") {
        return Promise.resolve(
          json([{ id: "trk-1", jobId: "job-1", title: "T", company: "C", resumeId: null, status: "APPLIED", notes: null, createdAt: "", updatedAt: "" }]),
        );
      }
      throw new Error(`unexpected fetch ${input}`);
    });
    await ensureTrackerApplied("job-1");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/tracker/"),
      expect.anything(),
    );
  });

  it("propagates a tracker fetch failure instead of silently flipping", async () => {
    fetchMock.mockImplementation(() => Promise.resolve(json({ message: "Down" }, 500)));
    await expect(ensureTrackerApplied("job-1")).rejects.toThrow();
  });

  it("recovers from a 409 on create by reusing the existing entry", async () => {
    const calls: string[] = [];
    fetchMock.mockImplementation((input: string | URL | Request, init?: RequestInit) => {
      const url = input.toString();
      calls.push(`${init?.method ?? "GET"} ${url}`);
      if (url === "/api/tracker" && !init?.method && calls.filter((c) => c === "GET /api/tracker").length === 1) {
        return Promise.resolve(json([]));
      }
      if (url === "/api/tracker" && init?.method === "POST") {
        return Promise.resolve(json({ message: "already exists" }, 409));
      }
      if (url === "/api/tracker" && !init?.method) {
        return Promise.resolve(
          json([{ id: "trk-7", jobId: "job-1", title: "T", company: "C", resumeId: null, status: "SAVED", notes: null, createdAt: "", updatedAt: "" }]),
        );
      }
      if (url === "/api/tracker/trk-7" && init?.method === "PUT") {
        return Promise.resolve(json({ id: "trk-7", status: "APPLIED" }));
      }
      throw new Error(`unexpected fetch ${url}`);
    });
    await ensureTrackerApplied("job-1");
    expect(calls).toEqual([
      "GET /api/tracker",
      "POST /api/tracker",
      "GET /api/tracker",
      "PUT /api/tracker/trk-7",
    ]);
  });
});

describe("TailorApplyModal — adversarial", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  const doc = {
    id: "doc-1",
    templateId: "modern",
    title: "My Resume",
    state: state(),
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  };

  function mockCopilot(
    handler: (section: string) => { options: CopilotOption[] } | { error: number },
  ) {
    fetchMock.mockImplementation((input: string | URL | Request, init?: RequestInit) => {
      const url = input.toString();
      const method = init?.method ?? "GET";
      if (url === "/api/resume-builder/documents" && method === "GET") {
        return Promise.resolve(json([doc]));
      }
      if (url.includes("/copilot") && method === "POST") {
        const body = JSON.parse(String(init?.body ?? "{}")) as { active_section?: string };
        const section = body.active_section ?? "";
        const res = handler(section);
        if ("error" in res) return Promise.resolve(json({ message: "Co-Pilot down" }, res.error));
        return Promise.resolve(json(res));
      }
      if (url === "/api/resume-builder/documents/doc-1" && method === "PUT") {
        return Promise.resolve(json(doc));
      }
      if (url === "/api/resume-builder/documents/doc-1/save-to-profile" && method === "POST") {
        return Promise.resolve(json({ resumeId: "resume-9", label: "Tailored" }));
      }
      if (url === "/api/tracker" && method === "POST") {
        return Promise.resolve(json({ id: "trk-1", jobId: "job-1", status: "SAVED" }));
      }
      if (url === "/api/approvals" && method === "POST") {
        return Promise.resolve(json({ id: "appr-1", action: "APPLY_JOB", status: "PENDING" }));
      }
      throw new Error(`unexpected fetch ${method} ${url}`);
    });
  }

  function twoOptions(): CopilotOption[] {
    return [
      { id: "opt-1", angle: "Impact-led", bullets: ["Built payment APIs serving 2M users."], atsKeywords: ["Java"] },
      { id: "opt-2", angle: "Metrics-led", bullets: ["Built payment APIs serving 5M users."], atsKeywords: ["Kafka"] },
    ];
  }

  async function reachReview(user: ReturnType<typeof userEvent.setup>) {
    render(<TailorApplyModal job={job()} open onClose={() => {}} />, { wrapper });
    await waitFor(() => expect(screen.getByLabelText("Resume")).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.click(screen.getByRole("button", { name: "Tailor with Co-Pilot" }));
    await waitFor(() => expect(screen.getAllByTestId("tailor-diff").length).toBeGreaterThan(0));
  }

  it("renders nothing when closed", () => {
    render(<TailorApplyModal job={job()} open={false} onClose={() => {}} />, { wrapper });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("shows the empty state when the user has no builder documents", async () => {
    fetchMock.mockImplementation((input: string | URL | Request) => {
      if (input.toString() === "/api/resume-builder/documents") {
        return Promise.resolve(json([]));
      }
      throw new Error(`unexpected fetch ${input}`);
    });
    render(<TailorApplyModal job={job()} open onClose={() => {}} />, { wrapper });
    await waitFor(() =>
      expect(
        screen.getByText(/No Resume Builder documents yet/),
      ).toBeInTheDocument(),
    );
    expect(
      screen.getByRole("button", { name: "Tailor with Co-Pilot" }),
    ).toBeDisabled();
  });

  it("does not crash when the documents fetch fails", async () => {
    fetchMock.mockImplementation(() => Promise.resolve(json({ message: "Down" }, 500)));
    render(<TailorApplyModal job={job()} open onClose={() => {}} />, { wrapper });
    await waitFor(() =>
      expect(
        screen.getByText(/No Resume Builder documents yet/),
      ).toBeInTheDocument(),
    );
  });

  it("disables Tailor until a resume is picked", async () => {
    fetchMock.mockImplementation((input: string | URL | Request) => {
      if (input.toString() === "/api/resume-builder/documents") {
        return Promise.resolve(json([doc]));
      }
      throw new Error(`unexpected fetch ${input}`);
    });
    render(<TailorApplyModal job={job()} open onClose={() => {}} />, { wrapper });
    await waitFor(() => expect(screen.getByLabelText("Resume")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Tailor with Co-Pilot" })).toBeDisabled();
  });

  it("errors when the chosen document has nothing to tailor", async () => {
    const emptyDoc = { ...doc, state: state({ summary: "  ", experience: [] }) };
    fetchMock.mockImplementation((input: string | URL | Request) => {
      if (input.toString() === "/api/resume-builder/documents") {
        return Promise.resolve(json([emptyDoc]));
      }
      throw new Error(`unexpected fetch ${input}`);
    });
    const user = userEvent.setup();
    render(<TailorApplyModal job={job()} open onClose={() => {}} />, { wrapper });
    await waitFor(() => expect(screen.getByLabelText("Resume")).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.click(screen.getByRole("button", { name: "Tailor with Co-Pilot" }));
    await waitFor(() =>
      expect(
        screen.getByText(/has no summary or experience bullets to tailor/),
      ).toBeInTheDocument(),
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining("/copilot"),
      expect.anything(),
    );
  });

  it("reviews the good section when Co-Pilot fails for one section only", async () => {
    mockCopilot((section) =>
      section === "summary"
        ? { options: [twoOptions()[0]!] }
        : { error: 500 },
    );
    const user = userEvent.setup();
    render(<TailorApplyModal job={job()} open onClose={() => {}} />, { wrapper });
    await waitFor(() => expect(screen.getByLabelText("Resume")).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.click(screen.getByRole("button", { name: "Tailor with Co-Pilot" }));
    const diffs = await waitFor(() => screen.getAllByTestId("tailor-diff"));
    expect(diffs).toHaveLength(1);
    expect(screen.getByText(/Experience 1: Co-Pilot down/)).toBeInTheDocument();
    expect(screen.getByText(/Those sections keep their original text/)).toBeInTheDocument();
  });

  it("notes a section Co-Pilot returns with zero options, and still reviews the rest", async () => {
    mockCopilot((section) =>
      section === "experience" ? { options: [] } : { options: [twoOptions()[0]!] },
    );
    const user = userEvent.setup();
    render(<TailorApplyModal job={job()} open onClose={() => {}} />, { wrapper });
    await waitFor(() => expect(screen.getByLabelText("Resume")).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.click(screen.getByRole("button", { name: "Tailor with Co-Pilot" }));
    const diffs = await waitFor(() => screen.getAllByTestId("tailor-diff"));
    expect(diffs).toHaveLength(1);
    expect(screen.getByText(/Experience 1: Co-Pilot returned no options/)).toBeInTheDocument();
  });

  it("switching Co-Pilot option updates the diff to the chosen option", async () => {
    mockCopilot(() => ({ options: twoOptions() }));
    const user = userEvent.setup();
    await reachReview(user);
    const diffs = screen.getAllByTestId("tailor-diff");
    expect(diffs[0]?.textContent).toContain("2M users");
    await user.click(screen.getAllByRole("button", { name: "Metrics-led" })[0]!);
    await waitFor(() =>
      expect(screen.getAllByTestId("tailor-diff")[0]?.textContent).toContain("5M users"),
    );
  });

  it("shows the unchanged note when Co-Pilot returns the section verbatim", async () => {
    mockCopilot((section) =>
      section === "summary"
        ? { options: [{ id: "opt-1", angle: "Same", bullets: ["Senior Java engineer with ten years of experience."], atsKeywords: [] }] }
        : { options: [twoOptions()[0]!] },
    );
    const user = userEvent.setup();
    render(<TailorApplyModal job={job()} open onClose={() => {}} />, { wrapper });
    await waitFor(() => expect(screen.getByLabelText("Resume")).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.click(screen.getByRole("button", { name: "Tailor with Co-Pilot" }));
    await waitFor(() =>
      expect(
        screen.getByText("Co-Pilot returned this section unchanged."),
      ).toBeInTheDocument(),
    );
  });

  it("stays in review with an error when saving the tailored document fails", async () => {
    fetchMock.mockImplementation((input: string | URL | Request, init?: RequestInit) => {
      const url = input.toString();
      const method = init?.method ?? "GET";
      if (url === "/api/resume-builder/documents" && method === "GET") {
        return Promise.resolve(json([doc]));
      }
      if (url.includes("/copilot") && method === "POST") {
        return Promise.resolve(json({ options: [twoOptions()[0]!] }));
      }
      if (url === "/api/resume-builder/documents/doc-1" && method === "PUT") {
        return Promise.resolve(json({ message: "Save failed" }, 500));
      }
      throw new Error(`unexpected fetch ${method} ${url}`);
    });
    const user = userEvent.setup();
    await reachReview(user);
    await user.click(screen.getByRole("button", { name: "Save tailored resume & request apply" }));
    await waitFor(() => expect(screen.getByText("Save failed")).toBeInTheDocument());
    expect(screen.queryByText(/Approval requested for/)).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      "/api/approvals",
      expect.anything(),
    );
  });

  it("stays in review with an error when the approval request fails", async () => {
    fetchMock.mockImplementation((input: string | URL | Request, init?: RequestInit) => {
      const url = input.toString();
      const method = init?.method ?? "GET";
      if (url === "/api/resume-builder/documents" && method === "GET") {
        return Promise.resolve(json([doc]));
      }
      if (url.includes("/copilot") && method === "POST") {
        return Promise.resolve(json({ options: [twoOptions()[0]!] }));
      }
      if (url === "/api/resume-builder/documents/doc-1" && method === "PUT") {
        return Promise.resolve(json(doc));
      }
      if (url === "/api/resume-builder/documents/doc-1/save-to-profile" && method === "POST") {
        return Promise.resolve(json({ resumeId: "resume-9", label: "Tailored" }));
      }
      if (url === "/api/tracker" && method === "POST") {
        return Promise.resolve(json({ id: "trk-1", jobId: "job-1", status: "SAVED" }));
      }
      if (url === "/api/approvals" && method === "POST") {
        return Promise.resolve(json({ message: "Approval service down" }, 503));
      }
      throw new Error(`unexpected fetch ${method} ${url}`);
    });
    const user = userEvent.setup();
    await reachReview(user);
    await user.click(screen.getByRole("button", { name: "Save tailored resume & request apply" }));
    await waitFor(() =>
      expect(screen.getByText("Approval service down")).toBeInTheDocument(),
    );
    expect(screen.queryByText(/Approval requested for/)).not.toBeInTheDocument();
  });

  it("'Pick a different resume' returns to the pick step", async () => {
    mockCopilot(() => ({ options: [twoOptions()[0]!] }));
    const user = userEvent.setup();
    await reachReview(user);
    await user.click(screen.getByRole("button", { name: "Pick a different resume" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Tailor with Co-Pilot" })).toBeInTheDocument(),
    );
    expect(screen.queryByTestId("tailor-diff")).not.toBeInTheDocument();
  });
});
