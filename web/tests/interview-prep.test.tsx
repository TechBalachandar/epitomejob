// Feature 4 — Interview Prep from the job description.
// Pure-logic tests run without any network; component tests mock fetch
// at the BFF boundary (/api/*) — the Coach / real backend is never
// involved. The Coach needs Ollama on the user's Mac, so live answer
// generation is NOT verified here, only the wiring up to the real
// POST /api/coach/chat endpoint.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { useAuthStore } from "@/lib/auth-store";
import { InterviewPrep } from "@/components/interviews/interview-prep";
import {
  buildPrepPrompt,
  buildResumeContext,
  checkGrounding,
  parsePrepReply,
  validatePastedJD,
} from "@/lib/interview-prep";
import type { Job, Profile, ResumeBuilderDocument } from "@/lib/types";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockFetch(
  impl: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  return vi.fn((input: string | URL | Request, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    return Promise.resolve(impl(url, init));
  });
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const PROFILE: Profile = {
  id: "profile-1",
  userId: "user-1",
  headline: "Principal Mobile Architect",
  summary: "Banking apps at scale.",
  location: "Reston, VA",
  workType: "Hybrid",
  salaryMin: null,
  salaryMax: null,
  currency: "USD",
  visibility: "PUBLIC",
  skills: ["Kotlin", "Java", "Spring Boot"],
  h1bClockStartDate: null,
  linkedinUrl: null,
  workAuthorization: null,
  completeness: 80,
  createdAt: "2026-11-01T00:00:00Z",
  updatedAt: "2026-11-01T00:00:00Z",
};

const BUILDER_DOC: ResumeBuilderDocument = {
  id: "doc-1",
  templateId: "modern",
  title: "Android Developer",
  state: {
    contact: {
      fullName: "Balachandar Jeldi",
      title: "Principal Mobile Architect",
      email: "dev@example.com",
      phone: "555-0100",
      location: "Reston, VA",
      linkedin: "",
    },
    summary: "Banking apps at scale.",
    experience: [
      {
        id: "exp-1",
        title: "Principal Mobile Architect",
        company: "Navy Federal",
        startDate: "2023-09",
        endDate: "",
        bullets: ["Led 10+ engineers on banking apps."],
      },
    ],
    education: [
      {
        id: "edu-1",
        school: "JNTU Hyderabad",
        degree: "B.Tech",
        startDate: "2004",
        endDate: "2008",
      },
    ],
    skills: ["Kotlin", "Android"],
  },
  createdAt: "2026-11-10T00:00:00Z",
  updatedAt: "2026-11-10T00:00:00Z",
};

const JOB: Job = {
  id: "job-1",
  title: "Senior Android Engineer",
  company: "Acme",
  location: "Reston, VA",
  workType: "Hybrid",
  salaryMin: 140000,
  salaryMax: 170000,
  currency: "USD",
  sponsorsH1b: true,
  skills: ["Kotlin", "Android"],
  summary: "Build the Android banking app.",
  role: "Android",
  postedAt: "2026-11-10T09:00:00Z",
  source: "SEED",
  sourceUrl: null,
};

const QUESTIONS = [
  { id: "q-1", text: "Tell me about a time you led a team.", category: "Behavioral", role: "Android" },
  { id: "q-2", text: "How do you handle a production crash?", category: "Technical", role: "Android" },
];

const COACH_REPLY = [
  "Q: Tell me about a time you led a team.",
  "Model answer: As a Principal Mobile Architect at Navy Federal, I led 10+ engineers on banking apps, using Kotlin and Spring Boot to ship reliably.",
  "",
  "Q: How do you handle a production crash?",
  "Model answer: At Navy Federal I triaged the crash with the on-call team and shipped a fix the same day.",
].join("\n");

const COACH_RESPONSE = {
  reply: COACH_REPLY,
  citations: [
    {
      docId: "resume:10000000-0000-4000-8000-000000000001",
      chunk: "Principal Mobile Architect at Navy Federal, leading 10+ engineers.",
      score: 0.87,
      source: "resume",
    },
  ],
  actions: [],
  specialistsUsed: ["resume", "interview"],
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

describe("buildResumeContext", () => {
  it("prefers the builder document and names it as the source", () => {
    const ctx = buildResumeContext({
      profile: PROFILE,
      builderDocuments: [BUILDER_DOC],
    });
    expect(ctx.sourceLabel).toMatch(/Android Developer/);
    expect(ctx.companies).toEqual(["Navy Federal"]);
    expect(ctx.roles).toEqual(["Principal Mobile Architect"]);
    expect(ctx.schools).toEqual(["JNTU Hyderabad"]);
    expect(ctx.skills).toEqual(["Kotlin", "Java", "Spring Boot", "Android"]);
    expect(ctx.text).toMatch(/Led 10\+ engineers/);
  });

  it("falls back to the profile when there is no builder document", () => {
    const ctx = buildResumeContext({ profile: PROFILE, builderDocuments: [] });
    expect(ctx.sourceLabel).toBe("your profile");
    expect(ctx.companies).toEqual([]);
    expect(ctx.skills).toEqual(["Kotlin", "Java", "Spring Boot"]);
    expect(ctx.text).toMatch(/Principal Mobile Architect/);
  });

  it("is empty with no resume on file", () => {
    const ctx = buildResumeContext({ profile: null, builderDocuments: [] });
    expect(ctx.text).toBe("");
    expect(ctx.sourceLabel).toBe("No resume on file");
  });
});

describe("validatePastedJD", () => {
  it("rejects a title-length paste", () => {
    expect(validatePastedJD("Android Engineer")).toMatch(/at least 40 characters/);
  });

  it("accepts a full description", () => {
    expect(
      validatePastedJD(
        "We are hiring a Senior Android Engineer to build our banking app in Kotlin.",
      ),
    ).toBeNull();
  });
});

describe("buildPrepPrompt", () => {
  const ctx = buildResumeContext({
    profile: PROFILE,
    builderDocuments: [BUILDER_DOC],
  });

  it("tells the Coach to ground only in the resume and never invent experience", () => {
    const prompt = buildPrepPrompt({
      job: { title: JOB.title, company: JOB.company, summary: JOB.summary, skills: JOB.skills },
      questions: QUESTIONS.map((q) => q.text),
      resumeContext: ctx,
    });
    expect(prompt).toMatch(/grounded ONLY in my resume/i);
    expect(prompt).toMatch(/Never invent a role, company/);
    expect(prompt).toMatch(/No direct experience in your resume/);
    expect(prompt).toMatch(/Navy Federal/);
    expect(prompt).toMatch(/Tell me about a time you led a team/);
  });

  it("uses the pasted JD and asks the Coach to generate the questions", () => {
    const jd = "We need a Senior Android Engineer to own our Kotlin banking app in Reston.";
    const prompt = buildPrepPrompt({ pastedJD: jd, resumeContext: ctx });
    expect(prompt).toMatch(/JOB DESCRIPTION:/);
    expect(prompt).toMatch(/most likely interview questions/);
    expect(prompt).not.toMatch(/JOB: Senior Android Engineer at Acme/);
  });
});

describe("parsePrepReply", () => {
  it("parses Q: / Model answer: blocks", () => {
    const parsed = parsePrepReply(COACH_REPLY);
    expect(parsed.unparsed).toBeNull();
    expect(parsed.items).toHaveLength(2);
    expect(parsed.items[0].question).toBe("Tell me about a time you led a team.");
    expect(parsed.items[0].modelAnswer).toMatch(/led 10\+ engineers/);
    expect(parsed.items[1].modelAnswer).toMatch(/At Navy Federal I triaged/);
  });

  it("parses a numbered list with Model answer lines", () => {
    const parsed = parsePrepReply(
      [
        "1. Why this role?",
        "Model answer: I have built banking apps at Navy Federal for three years.",
        "2. What is your greatest strength?",
        "Model answer: Shipping reliably under pressure.",
      ].join("\n"),
    );
    expect(parsed.items).toHaveLength(2);
    expect(parsed.items[0].question).toBe("Why this role?");
  });

  it("returns free text unparsed instead of inventing Q/A pairs", () => {
    const reply = "Sure — happy to help you prepare for this interview!";
    const parsed = parsePrepReply(reply);
    expect(parsed.items).toEqual([]);
    expect(parsed.unparsed).toBe(reply);
  });

  it("returns no items for an empty reply", () => {
    expect(parsePrepReply("  ")).toEqual({ items: [], unparsed: null });
  });
});

describe("checkGrounding", () => {
  const known = {
    companies: ["Navy Federal"],
    roles: ["Principal Mobile Architect"],
    skills: ["Kotlin", "Spring Boot"],
    schools: ["JNTU Hyderabad"],
  };

  it("cites known companies, roles and skills", () => {
    const check = checkGrounding(
      "As a Principal Mobile Architect at Navy Federal, I led the team using Kotlin and Spring Boot.",
      known,
    );
    expect(check.citedCompanies).toEqual(["Navy Federal"]);
    expect(check.citedRoles).toEqual(["Principal Mobile Architect"]);
    expect(check.citedSkills).toEqual(["Kotlin", "Spring Boot"]);
    expect(check.flaggedPhrases).toEqual([]);
  });

  it("flags an employer that is not in the resume", () => {
    const check = checkGrounding(
      "At Google I shipped the payments app used by millions.",
      known,
    );
    expect(check.flaggedPhrases).toContain("Google");
    expect(check.citedCompanies).toEqual([]);
  });

  it("does not flag a school from the resume", () => {
    const check = checkGrounding(
      "I studied at JNTU Hyderabad before joining Navy Federal.",
      known,
    );
    expect(check.flaggedPhrases).toEqual([]);
  });

  it("does not flag lowercase 'at work' / 'at home'", () => {
    const check = checkGrounding("I learned this at work, not at home.", known);
    expect(check.flaggedPhrases).toEqual([]);
  });
});

describe("InterviewPrep component", () => {
  function prepFetch(
    coachImpl: (url: string, init?: RequestInit) => Response | Promise<Response>,
  ) {
    return mockFetch((url, init) => {
      if (url.startsWith("/api/profiles/me")) return jsonResponse(PROFILE);
      if (url.startsWith("/api/resume-builder/documents")) return jsonResponse([BUILDER_DOC]);
      if (url.startsWith("/api/interviews/questions")) return jsonResponse(QUESTIONS);
      if (url === "/api/coach/chat") return coachImpl(url, init);
      return jsonResponse({ message: "not found" }, 404);
    });
  }

  it("generates model answers for the picked job and shows grounding + citations", async () => {
    const user = userEvent.setup();
    let coachBody = "";
    vi.stubGlobal(
      "fetch",
      prepFetch((url, init) => {
        coachBody = typeof init?.body === "string" ? init.body : "";
        return jsonResponse(COACH_RESPONSE);
      }),
    );

    render(<InterviewPrep jobs={[JOB]} />, { wrapper });

    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));

    await waitFor(() =>
      expect(screen.getByText(/led 10\+ engineers/)).toBeInTheDocument(),
    );
    // The prompt sent to the Coach carries the job and the resume context.
    expect(coachBody).toMatch(/Senior Android Engineer at Acme/);
    expect(coachBody).toMatch(/Navy Federal/);
    expect(coachBody).toMatch(/Tell me about a time you led a team/);
    // Grounding chips name the cited experience (both answers cite Navy Federal); citations render too.
    expect(screen.getAllByText("Cites: Navy Federal").length).toBe(2);
    expect(screen.getByText("Cites: Kotlin")).toBeInTheDocument();
    // The phrase appears in both the model answer and the cited chunk.
    expect(
      screen.getAllByText(/Principal Mobile Architect at Navy Federal/).length,
    ).toBeGreaterThanOrEqual(2);
    expect(screen.getByText(/score 0.8700/)).toBeInTheDocument();
    expect(screen.getByText("Worked with: resume, interview")).toBeInTheDocument();
  });

  it("flags a model answer that names an employer not in the resume", async () => {
    const user = userEvent.setup();
    const reply = [
      "Q: Tell me about a time you led a team.",
      "Model answer: At Google I led the payments team of 20 engineers.",
    ].join("\n");
    vi.stubGlobal(
      "fetch",
      prepFetch(() => jsonResponse({ ...COACH_RESPONSE, reply })),
    );

    render(<InterviewPrep jobs={[JOB]} />, { wrapper });

    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));

    await waitFor(() =>
      expect(screen.getByText(/Check before using/)).toBeInTheDocument(),
    );
    // "Google" appears in both the model answer and the flag warning.
    expect(screen.getAllByText(/Google/).length).toBe(2);
    expect(
      screen.getByText("No resume experience cited in this answer"),
    ).toBeInTheDocument();
  });

  it("prepares from a pasted job description and asks the Coach to generate questions", async () => {
    const user = userEvent.setup();
    let coachBody = "";
    vi.stubGlobal(
      "fetch",
      prepFetch((url, init) => {
        coachBody = typeof init?.body === "string" ? init.body : "";
        return jsonResponse(COACH_RESPONSE);
      }),
    );

    render(<InterviewPrep jobs={[JOB]} />, { wrapper });

    await user.click(screen.getByRole("button", { name: "Paste a job description" }));
    await user.type(
      screen.getByLabelText("Paste a job description"),
      "We are hiring a Senior Android Engineer to build our Kotlin banking app in Reston, Virginia.",
    );
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));

    await waitFor(() =>
      expect(screen.getByText(/led 10\+ engineers/)).toBeInTheDocument(),
    );
    expect(coachBody).toMatch(/JOB DESCRIPTION:/);
    expect(coachBody).toMatch(/most likely interview questions/);
  });

  it("rejects a pasted title without calling the Coach", async () => {
    const user = userEvent.setup();
    const fetchMock = prepFetch(() => jsonResponse(COACH_RESPONSE));
    vi.stubGlobal("fetch", fetchMock);

    render(<InterviewPrep jobs={[JOB]} />, { wrapper });

    await user.click(screen.getByRole("button", { name: "Paste a job description" }));
    await user.type(screen.getByLabelText("Paste a job description"), "Android Engineer");
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));

    await waitFor(() =>
      expect(screen.getByText(/at least 40 characters/)).toBeInTheDocument(),
    );
    expect(fetchMock.mock.calls.some(([url]) => url === "/api/coach/chat")).toBe(false);
  });

  it("reports honestly when the Coach is unreachable (needs Ollama on the Mac)", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      prepFetch(() =>
        jsonResponse({ message: "Coach agent is not reachable" }, 502),
      ),
    );

    render(<InterviewPrep jobs={[JOB]} />, { wrapper });

    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));

    await waitFor(() =>
      expect(screen.getByText(/Could not reach the Coach/)).toBeInTheDocument(),
    );
    expect(screen.getByText(/needs Ollama running on your Mac/)).toBeInTheDocument();
    expect(screen.queryByText(/led 10\+ engineers/)).not.toBeInTheDocument();
  });

  it("shows an unparsed Coach reply as received instead of reshaping it", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      prepFetch(() =>
        jsonResponse({
          ...COACH_RESPONSE,
          reply: "Sure — happy to help you prepare for this interview!",
        }),
      ),
    );

    render(<InterviewPrep jobs={[JOB]} />, { wrapper });

    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));

    await waitFor(() =>
      expect(
        screen.getByText(/didn.t follow the expected question-and-answer format/),
      ).toBeInTheDocument(),
    );
    expect(
      screen.getByText("Sure — happy to help you prepare for this interview!"),
    ).toBeInTheDocument();
  });
});

describe("buildResumeContext — edge cases (360°)", () => {
  it("de-duplicates skills case-insensitively, keeping the first spelling", () => {
    const ctx = buildResumeContext({
      profile: { ...PROFILE, skills: ["Kotlin", "kotlin", "KOTLIN", "Java"] },
      builderDocuments: [
        {
          ...BUILDER_DOC,
          state: { ...BUILDER_DOC.state, skills: ["kotlin", "Android", "ANDROID"] },
        },
      ],
    });
    expect(ctx.skills).toEqual(["Kotlin", "Java", "Android"]);
  });

  it("picks the first builder document that has a state, skipping stateless ones", () => {
    const stateless = {
      ...BUILDER_DOC,
      id: "doc-0",
      title: "Empty shell",
      state: null,
    } as unknown as ResumeBuilderDocument;
    const ctx = buildResumeContext({
      profile: PROFILE,
      builderDocuments: [stateless, BUILDER_DOC],
    });
    expect(ctx.sourceLabel).toMatch(/Android Developer/);
    expect(ctx.companies).toEqual(["Navy Federal"]);
  });

  it("skips empty bullets and blank companies/roles, and formats dates", () => {
    const doc: ResumeBuilderDocument = {
      ...BUILDER_DOC,
      state: {
        ...BUILDER_DOC.state,
        experience: [
          {
            id: "exp-1",
            title: "Engineer",
            company: "Acme",
            startDate: "2021-01",
            endDate: "2022-06",
            bullets: ["Shipped X.", "   ", ""],
          },
          { id: "exp-2", title: "  ", company: "", startDate: "", endDate: "", bullets: [] },
        ],
      },
    };
    const ctx = buildResumeContext({ profile: null, builderDocuments: [doc] });
    expect(ctx.companies).toEqual(["Acme"]);
    expect(ctx.roles).toEqual(["Engineer"]);
    expect(ctx.text).toMatch(/\(2021-01 – 2022-06\)/);
    expect(ctx.text).not.toMatch(/  - \n/);
    expect(ctx.text).toMatch(/Shipped X\./);
  });

  it("omits the date range when both dates are missing", () => {
    const doc: ResumeBuilderDocument = {
      ...BUILDER_DOC,
      state: {
        ...BUILDER_DOC.state,
        experience: [
          { id: "exp-1", title: "Engineer", company: "Acme", startDate: "", endDate: "", bullets: [] },
        ],
      },
    };
    const ctx = buildResumeContext({ profile: null, builderDocuments: [doc] });
    expect(ctx.text).toMatch(/Role: Engineer at Acme\n/);
    expect(ctx.text).not.toMatch(/\(/);
  });

  it("handles null builderDocuments and a profile with no skills", () => {
    const ctx = buildResumeContext({
      profile: { ...PROFILE, skills: [], headline: "", summary: "" },
      builderDocuments: null,
    });
    expect(ctx.text).toBe("");
    expect(ctx.sourceLabel).toBe("your profile");
    expect(ctx.skills).toEqual([]);
  });

  it("uses the builder title fallback when the document has no title", () => {
    const doc = {
      ...BUILDER_DOC,
      title: null,
    } as unknown as ResumeBuilderDocument;
    const ctx = buildResumeContext({ profile: null, builderDocuments: [doc] });
    expect(ctx.sourceLabel).toMatch(/Untitled/);
  });
});

describe("validatePastedJD — boundaries (360°)", () => {
  it("accepts exactly 40 characters and rejects 39", () => {
    expect(validatePastedJD("x".repeat(40))).toBeNull();
    expect(validatePastedJD("x".repeat(39))).toMatch(/at least 40 characters/);
  });

  it("rejects whitespace-only and counts trimmed length", () => {
    expect(validatePastedJD("   ")).toMatch(/at least 40 characters/);
    expect(validatePastedJD(`  ${"x".repeat(40)}  `)).toBeNull();
    expect(validatePastedJD(`  ${"x".repeat(39)}  `)).toMatch(/at least 40 characters/);
  });
});

describe("buildPrepPrompt — variants (360°)", () => {
  const ctx = buildResumeContext({ profile: PROFILE, builderDocuments: [BUILDER_DOC] });

  it("uses the pasted JD even when a job is also supplied", () => {
    const prompt = buildPrepPrompt({
      job: { title: JOB.title, company: JOB.company, summary: JOB.summary, skills: JOB.skills },
      pastedJD: "We need a Senior Android Engineer to own our Kotlin banking app in Reston.",
      questions: ["Why this role?"],
      resumeContext: ctx,
    });
    expect(prompt).toMatch(/JOB DESCRIPTION:/);
    expect(prompt).not.toMatch(/JOB: Senior Android Engineer at Acme/);
    expect(prompt).toMatch(/Why this role\?/);
  });

  it("omits empty summary/skills lines for a bare job", () => {
    const prompt = buildPrepPrompt({
      job: { title: "Engineer", company: "Acme", summary: "", skills: [] },
      questions: ["Why this role?"],
      resumeContext: ctx,
    });
    expect(prompt).toMatch(/JOB: Engineer at Acme/);
    expect(prompt).not.toMatch(/Summary:/);
    expect(prompt).not.toMatch(/Skills listed:/);
  });

  it("with no job, no JD and no questions, asks the Coach to generate questions", () => {
    const prompt = buildPrepPrompt({ resumeContext: ctx });
    expect(prompt).toMatch(/most likely interview questions/);
    expect(prompt).toMatch(/MY RESUME/);
    expect(prompt).toMatch(/Navy Federal/);
    expect(prompt).toMatch(/Reply in this exact format/);
    expect(prompt).toMatch(/Q: <the question>/);
    expect(prompt).toMatch(/Model answer: <the model answer>/);
  });

  it("numbers supplied questions in order", () => {
    const prompt = buildPrepPrompt({
      job: { title: JOB.title, company: JOB.company, summary: JOB.summary, skills: JOB.skills },
      questions: ["First?", "Second?"],
      resumeContext: ctx,
    });
    expect(prompt).toMatch(/1\. First\?/);
    expect(prompt).toMatch(/2\. Second\?/);
  });

  it("keeps the 3–5 sentence rule and the gap rule in every prompt", () => {
    const prompt = buildPrepPrompt({ resumeContext: ctx });
    expect(prompt).toMatch(/3–5 sentences/);
    expect(prompt).toMatch(/first person/);
  });
});

describe("parsePrepReply — variants and adversarial input (360°)", () => {
  it("parses 'Question:' and 'Answer:' / 'Suggested answer:' variants", () => {
    const parsed = parsePrepReply(
      [
        "Question: Why this role?",
        "Answer: I built banking apps at Navy Federal.",
        "Question 2: What is your strength?",
        "Suggested answer: Shipping reliably.",
      ].join("\n"),
    );
    expect(parsed.items).toHaveLength(2);
    expect(parsed.items[0].question).toBe("Why this role?");
    expect(parsed.items[1].modelAnswer).toBe("Shipping reliably.");
  });

  it("parses 'Q1.' and 'Q2:' prefixes", () => {
    const parsed = parsePrepReply(
      "Q1. Why this role?\nModel answer: Because I can.\nQ2: Strength?\nModel answer: Focus.",
    );
    expect(parsed.items).toHaveLength(2);
    expect(parsed.items[0].question).toBe("Why this role?");
    expect(parsed.items[1].question).toBe("Strength?");
  });

  it("keeps multi-line model answers together", () => {
    const parsed = parsePrepReply(
      "Q: Tell me about a challenge.\nModel answer: First line.\nSecond line, still the same answer.\nThird line.",
    );
    expect(parsed.items).toHaveLength(1);
    expect(parsed.items[0].modelAnswer).toBe(
      "First line.\nSecond line, still the same answer.\nThird line.",
    );
  });

  it("handles CRLF line endings", () => {
    const parsed = parsePrepReply(
      "Q: Why this role?\r\nModel answer: I built banking apps.\r\n",
    );
    expect(parsed.items).toHaveLength(1);
    expect(parsed.items[0].modelAnswer).toBe("I built banking apps.");
  });

  it("returns a question with no answer as unparsed instead of inventing one", () => {
    const reply = "Q: Why this role?\nI really want this job.";
    const parsed = parsePrepReply(reply);
    expect(parsed.items).toEqual([]);
    expect(parsed.unparsed).toBe(reply);
  });

  it("keeps a parsed question even when a later block has no answer", () => {
    const parsed = parsePrepReply(
      "Q: Why this role?\nModel answer: I built banking apps.\nQ: What is your weakness?\nNo answer here.",
    );
    expect(parsed.items).toHaveLength(1);
    expect(parsed.unparsed).toBeNull();
  });

  it("does not treat a bare number or a 'Q' inside prose as a question", () => {
    const parsed = parsePrepReply(
      "I have 5 years of experience and Q4 was my best quarter.",
    );
    expect(parsed.items).toEqual([]);
    expect(parsed.unparsed).toBe("I have 5 years of experience and Q4 was my best quarter.");
  });

  it("handles a very long reply without dropping later pairs", () => {
    const blocks = Array.from({ length: 10 }, (_, i) => [
      `Q: Question ${i + 1}?`,
      `Model answer: Answer ${i + 1} with ${"detail ".repeat(50)}.`,
    ].join("\n"));
    const parsed = parsePrepReply(blocks.join("\n\n"));
    expect(parsed.items).toHaveLength(10);
    expect(parsed.items[9].question).toBe("Question 10?");
  });
});

describe("checkGrounding — adversarial answers (360°)", () => {
  const known = {
    companies: ["Navy Federal"],
    roles: ["Principal Mobile Architect"],
    skills: ["Kotlin", "Spring Boot"],
    schools: ["JNTU Hyderabad"],
  };

  it("flags a Senior/Lead/Principal role prefix not in the resume", () => {
    const check = checkGrounding(
      "As a Senior Data Engineer, I built pipelines at scale.",
      known,
    );
    expect(check.flaggedPhrases).toContain("Senior Data Engineer");
    const lead = checkGrounding("As a Lead Mobile Architect, I shipped apps.", known);
    expect(lead.flaggedPhrases).toContain("Lead Mobile Architect");
  });

  it("flags 'as an' roles and dedupes repeated flags", () => {
    const check = checkGrounding(
      "As an Engineering Manager at Google I led teams. As an Engineering Manager I also hired at Google.",
      known,
    );
    expect(check.flaggedPhrases).toContain("Engineering Manager");
    expect(check.flaggedPhrases).toContain("Google");
    expect(check.flaggedPhrases.filter((p) => p === "Google")).toHaveLength(1);
    expect(check.flaggedPhrases.filter((p) => p === "Engineering Manager")).toHaveLength(1);
  });

  it("cites skills case-insensitively", () => {
    const check = checkGrounding("i used kotlin and spring boot daily.", known);
    expect(check.citedSkills).toEqual(["Kotlin", "Spring Boot"]);
  });

  it("does not flag a known company mentioned mid-sentence", () => {
    const check = checkGrounding(
      "I spent three years at Navy Federal building banking apps.",
      known,
    );
    expect(check.flaggedPhrases).toEqual([]);
    expect(check.citedCompanies).toEqual(["Navy Federal"]);
  });

  it("does not flag a paraphrased known role that contains the known role", () => {
    const check = checkGrounding(
      "As a Principal Mobile Architect and team lead, I shipped the app.",
      known,
    );
    expect(check.flaggedPhrases).not.toContain("Principal Mobile Architect");
  });

  it("returns empty results for an empty answer", () => {
    expect(
      checkGrounding("", known),
    ).toEqual({ citedCompanies: [], citedRoles: [], citedSkills: [], flaggedPhrases: [] });
  });

  it("does not flag 'at' followed by a lowercase word or a number", () => {
    const check = checkGrounding("I was at home at 5pm working at scale.", known);
    expect(check.flaggedPhrases).toEqual([]);
  });
});

describe("InterviewPrep component — failure and empty states (360°)", () => {
  function prepFetch(
    coachImpl: (url: string, init?: RequestInit) => Response | Promise<Response>,
    overrides: {
      profile?: Response | null;
      documents?: Response | null;
      questions?: Response | null;
    } = {},
  ) {
    return mockFetch((url, init) => {
      if (url.startsWith("/api/profiles/me"))
        return overrides.profile ?? jsonResponse(PROFILE);
      if (url.startsWith("/api/resume-builder/documents"))
        return overrides.documents ?? jsonResponse([BUILDER_DOC]);
      if (url.startsWith("/api/interviews/questions"))
        return overrides.questions ?? jsonResponse(QUESTIONS);
      if (url === "/api/coach/chat") return coachImpl(url, init);
      return jsonResponse({ message: "not found" }, 404);
    });
  }

  it("blocks generation with no resume on file and never calls the Coach", async () => {
    const user = userEvent.setup();
    const fetchMock = prepFetch(() => jsonResponse(COACH_RESPONSE), {
      profile: jsonResponse({ message: "not found" }, 404),
      documents: jsonResponse([]),
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));

    await waitFor(() =>
      expect(
        screen.getByRole("alert"),
      ).toHaveTextContent(/No resume on file — add your experience/),
    );
    expect(fetchMock.mock.calls.some(([url]) => url === "/api/coach/chat")).toBe(false);
  });

  it("disables Generate when there is no job to prepare for", () => {
    vi.stubGlobal("fetch", prepFetch(() => jsonResponse(COACH_RESPONSE)));
    render(<InterviewPrep jobs={[]} />, { wrapper });
    expect(
      screen.getByRole("button", { name: "Generate interview prep" }),
    ).toBeDisabled();
  });

  it("falls back to Coach-generated questions when the questions endpoint fails", async () => {
    const user = userEvent.setup();
    let coachBody = "";
    vi.stubGlobal(
      "fetch",
      prepFetch(
        (url, init) => {
          coachBody = typeof init?.body === "string" ? init.body : "";
          return jsonResponse(COACH_RESPONSE);
        },
        { questions: jsonResponse({ message: "boom" }, 500) },
      ),
    );

    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await waitFor(() =>
      expect(
        screen.getByText(/Could not load the role's questions/),
      ).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    await waitFor(() =>
      expect(screen.getByText(/led 10\+ engineers/)).toBeInTheDocument(),
    );
    expect(coachBody).toMatch(/most likely interview questions/);
  });

  it("reports a Coach 503 the same honest way as a 502", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      prepFetch(() => jsonResponse({ message: "Coach agent is not reachable" }, 503)),
    );
    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    await waitFor(() =>
      expect(screen.getByText(/Could not reach the Coach/)).toBeInTheDocument(),
    );
  });

  it("reports a non-Coach-down ApiError with the backend message", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      prepFetch(() => jsonResponse({ message: "Bad request" }, 400)),
    );
    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    await waitFor(() =>
      expect(screen.getByText(/Could not generate prep: Bad request/)).toBeInTheDocument(),
    );
  });

  it("does not crash on a null Coach reply — and shows neither answers nor an unparsed block", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      prepFetch(() => jsonResponse({ ...COACH_RESPONSE, reply: null })),
    );
    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    // The generate button returns to its idle label once the call settles.
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Generate interview prep" }),
      ).toBeInTheDocument(),
    );
    expect(screen.queryByText(/led 10\+ engineers/)).not.toBeInTheDocument();
    expect(screen.queryByText(/didn.t follow the expected/)).not.toBeInTheDocument();
  });

  it("hides the citations and specialists lines when the Coach returns none", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      prepFetch(() =>
        jsonResponse({ ...COACH_RESPONSE, citations: [], specialistsUsed: [] }),
      ),
    );
    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    await waitFor(() =>
      expect(screen.getByText(/led 10\+ engineers/)).toBeInTheDocument(),
    );
    expect(screen.queryByText(/Sources the Coach cited/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Worked with:/)).not.toBeInTheDocument();
  });

  it("truncates a long citation chunk at 140 characters", async () => {
    const user = userEvent.setup();
    const longChunk = "x".repeat(200);
    vi.stubGlobal(
      "fetch",
      prepFetch(() =>
        jsonResponse({
          ...COACH_RESPONSE,
          citations: [
            { docId: "resume:10000000-0000-4000-8000-000000000001", chunk: longChunk, score: 0.5, source: "resume" },
          ],
        }),
      ),
    );
    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    await waitFor(() =>
      expect(screen.getByText(/led 10\+ engineers/)).toBeInTheDocument(),
    );
    // The chunk is cut at 140 chars: 140 x's, then "…", never all 200.
    const citation = screen.getByText(/score 0\.5000/).closest("p");
    expect(citation?.textContent).toContain(`${"x".repeat(140)}…`);
    expect(citation?.textContent).not.toContain("x".repeat(141));
  });

  it("switching back to 'Pick a job' after a paste error lets generation proceed", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("fetch", prepFetch(() => jsonResponse(COACH_RESPONSE)));
    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Paste a job description" }));
    await user.type(screen.getByLabelText("Paste a job description"), "Android Engineer");
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    expect(await screen.findByText(/at least 40 characters/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Pick a job" }));
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    await waitFor(() =>
      expect(screen.getByText(/led 10\+ engineers/)).toBeInTheDocument(),
    );
    expect(screen.queryByText(/at least 40 characters/)).not.toBeInTheDocument();
  });

  it("flags a Senior role the Coach invents, with the role and the employer both named", async () => {
    const user = userEvent.setup();
    const reply = [
      "Q: Tell me about a time you led a team.",
      "Model answer: As a Senior Data Engineer at Google I led the payments team.",
    ].join("\n");
    vi.stubGlobal(
      "fetch",
      prepFetch(() => jsonResponse({ ...COACH_RESPONSE, reply })),
    );
    render(<InterviewPrep jobs={[JOB]} />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Generate interview prep" }));
    await waitFor(() =>
      expect(screen.getByText(/Check before using/)).toBeInTheDocument(),
    );
    expect(screen.getByText(/Google, Senior Data Engineer/)).toBeInTheDocument();
  });
});
