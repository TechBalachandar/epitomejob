// Cover Letter Generator tests: the pure prompt / validation / local
// save / export-state helpers, plus the editor flow (select resume,
// paste JD, generate via Co-Pilot, pick a draft, edit inline, save on
// this device, export PDF through the builder export route) with fetch
// mocked at the /api/* boundary. No real backend.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import {
  COVER_LETTER_SECTION,
  buildCoverLetterExportState,
  buildCoverLetterPrompt,
  coverLetterFileName,
  coverLetterTitle,
  deleteCoverLetter,
  letterFromOption,
  loadCoverLetters,
  newCoverLetterDraft,
  saveCoverLetter,
  validateCoverLetterInputs,
} from "@/lib/cover-letter";
import type { CoverLetterStorage } from "@/lib/cover-letter";
import { emptyBuilderState } from "@/lib/resume-builder";
import { useAuthStore } from "@/lib/auth-store";
import { CoverLetterEditor } from "@/components/cover-letter/cover-letter-editor";
import { ResumeHome } from "@/components/resume-builder/resume-home";

const navMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: navMock.push }),
}));

function memoryStorage(): CoverLetterStorage & { store: Record<string, string> } {
  const store: Record<string, string> = {};
  return {
    store,
    getItem: (key) => store[key] ?? null,
    setItem: (key, value) => {
      store[key] = value;
    },
    removeItem: (key) => {
      delete store[key];
    },
  };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const DOCS = [
  {
    id: "doc-1",
    templateId: "modern",
    title: "Android Developer",
    state: {
      contact: {
        fullName: "Alex Morgan",
        title: "Senior Software Engineer",
        email: "alex@example.com",
        phone: "(571) 555-0142",
        location: "Reston, VA",
        linkedin: "linkedin.com/in/alexmorgan",
      },
      summary: "Java engineer with 8 years of experience.",
      experience: [],
      education: [],
      skills: ["Java", "Spring Boot"],
    },
    createdAt: null,
    updatedAt: null,
  },
];

const COPILOT_RESPONSE = {
  options: [
    {
      id: "option-1",
      angle: "Metrics & Impact",
      bullets: [
        "Dear Hiring Manager,",
        "I am writing to apply for the Senior Software Engineer role. In my current role I led a migration that cut latency by [X%].",
        "Thank you for your consideration — I would welcome the chance to discuss the role.",
      ],
      atsKeywords: ["Spring Boot"],
    },
    {
      id: "option-2",
      angle: "Concise / Executive Punch",
      bullets: [
        "Dear Hiring Manager,",
        "Eight years building Java services makes me a strong fit for this role.",
        "I look forward to hearing from you.",
      ],
      atsKeywords: [],
    },
  ],
};

beforeEach(() => {
  window.localStorage.clear();
  useAuthStore.getState().logout();
  useAuthStore.getState().login({
    userId: "user-1",
    email: "dev@example.com",
    role: "USER",
    accessToken: "a.b.c",
  });
  vi.stubGlobal("URL", {
    ...URL,
    createObjectURL: vi.fn(() => "blob:fake"),
    revokeObjectURL: vi.fn(),
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  useAuthStore.getState().logout();
  window.localStorage.clear();
});

describe("buildCoverLetterPrompt", () => {
  it("asks for a full letter from resume facts + JD, with no invented name", () => {
    const prompt = buildCoverLetterPrompt({
      resumeLabel: "Android Developer",
      jobDescription: "We need a Java engineer.",
      company: "Navy Federal",
      role: "Senior Software Engineer",
    });
    expect(prompt).toMatch(/cover letter/i);
    expect(prompt).toMatch(/Navy Federal/);
    expect(prompt).toMatch(/Senior Software Engineer/);
    expect(prompt).toMatch(/Dear Hiring Manager/);
    expect(prompt).toMatch(/do not invent/i);
    expect(prompt).toMatch(/\[X%\]/);
    expect(prompt).toMatch(/We need a Java engineer\./);
  });

  it("uses the given hiring manager when provided", () => {
    const prompt = buildCoverLetterPrompt({
      resumeLabel: "Android Developer",
      jobDescription: "We need a Java engineer with 5 years experience.",
      hiringManager: "Priya Shah",
    });
    expect(prompt).toMatch(/Priya Shah/);
  });
});

describe("letterFromOption", () => {
  it("joins the option paragraphs with blank lines", () => {
    expect(
      letterFromOption({
        id: "1",
        angle: "Metrics & Impact",
        bullets: ["Dear Hiring Manager,", "Body paragraph.", "Closing."],
        atsKeywords: [],
      }),
    ).toBe("Dear Hiring Manager,\n\nBody paragraph.\n\nClosing.");
    expect(COVER_LETTER_SECTION).toBe("cover_letter");
  });
});

describe("validateCoverLetterInputs", () => {
  it("requires a resume and a real job description", () => {
    expect(validateCoverLetterInputs({ resumeId: null, jobDescription: "x".repeat(50) })).toMatch(
      /Choose a resume/,
    );
    expect(validateCoverLetterInputs({ resumeId: "doc-1", jobDescription: "short" })).toMatch(
      /job description/,
    );
    expect(
      validateCoverLetterInputs({ resumeId: "doc-1", jobDescription: "A proper job description here." }),
    ).toBeNull();
  });
});

describe("coverLetterTitle + coverLetterFileName", () => {
  it("titles from role/company, falling back to the resume label", () => {
    expect(
      coverLetterTitle({ resumeLabel: "Android Developer", role: "Engineer", company: "Navy Federal" }),
    ).toBe("Cover Letter — Engineer at Navy Federal");
    expect(coverLetterTitle({ resumeLabel: "Android Developer" })).toBe(
      "Cover Letter — Android Developer",
    );
    expect(coverLetterFileName("Cover Letter — Engineer at Navy Federal", "pdf")).toBe(
      "cover-letter-engineer-at-navy-federal.pdf",
    );
  });
});

describe("local save (no server document-kind field exists)", () => {
  it("round-trips save / load / delete, newest first", () => {
    const storage = memoryStorage();
    const first = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Android Developer",
      jobDescription: "JD one here, long enough to be real.",
      body: "Dear Hiring Manager,\n\nFirst letter.",
    });
    const second = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Android Developer",
      jobDescription: "JD two here, long enough to be real.",
      body: "Dear Hiring Manager,\n\nSecond letter.",
    });
    saveCoverLetter(storage, first);
    saveCoverLetter(storage, second);
    const loaded = loadCoverLetters(storage);
    expect(loaded).toHaveLength(2);
    expect(loaded[0].body).toContain("Second letter.");
    expect(deleteCoverLetter(storage, second.id)).toHaveLength(1);
  });

  it("reads corrupt storage as empty instead of throwing", () => {
    const storage = memoryStorage();
    storage.setItem("epitomejob.cover-letters.v1", "{not json");
    expect(loadCoverLetters(storage)).toEqual([]);
  });
});

describe("buildCoverLetterExportState", () => {
  it("keeps the contact block, puts the letter in the body, empties the rest", () => {
    const state = emptyBuilderState();
    state.contact.fullName = "Alex Morgan";
    const exported = buildCoverLetterExportState(state.contact, "Dear Hiring Manager,\n\nBody.");
    expect(exported.contact.fullName).toBe("Alex Morgan");
    expect(exported.summary).toBe("Dear Hiring Manager,\n\nBody.");
    expect(exported.experience).toEqual([]);
    expect(exported.education).toEqual([]);
    expect(exported.skills).toEqual([]);
  });
});

describe("ResumeHome cover-letter card", () => {
  it("adds a New Cover Letter card that opens /cover-letter", async () => {
    const user = userEvent.setup();
    render(
      <ResumeHome
        documents={[]}
        loading={false}
        error={null}
        onNew={vi.fn()}
        onFromJobDescription={vi.fn()}
        onFromTemplate={vi.fn()}
        onImport={vi.fn()}
        onOpen={vi.fn()}
        onMatchJob={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("button", { name: /New Cover Letter/ }));
    expect(navMock.push).toHaveBeenCalledWith("/cover-letter");
  });
});

describe("CoverLetterEditor flow", () => {
  function mockFetch() {
    return vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input.toString();
      const method = init?.method ?? "GET";
      if (url === "/api/resume-builder/documents" && method === "GET") {
        return Promise.resolve(jsonResponse(DOCS));
      }
      if (url === "/api/resume-builder/documents/doc-1/copilot" && method === "POST") {
        return Promise.resolve(jsonResponse(COPILOT_RESPONSE));
      }
      if (url === "/api/resume-builder/documents" && method === "POST") {
        return Promise.resolve(jsonResponse({ ...DOCS[0], id: "doc-cover-1" }, 201));
      }
      if (url === "/api/resume-builder/documents/doc-cover-1/export?format=pdf") {
        return Promise.resolve(
          new Response("%PDF-fake", {
            status: 200,
            headers: {
              "Content-Type": "application/pdf",
              "Content-Disposition": 'attachment; filename="cover-letter.pdf"',
            },
          }),
        );
      }
      return Promise.resolve(jsonResponse({ message: `unexpected ${method} ${url}` }, 404));
    });
  }

  it("generates drafts, picks one, edits inline, saves locally, exports PDF", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });

    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.type(
      screen.getByLabelText("Job description"),
      "We are hiring a Senior Software Engineer with Java and Spring Boot experience.",
    );
    await user.click(screen.getByRole("button", { name: "Generate cover letter" }));

    // Both Co-Pilot drafts render; pick the first.
    expect(await screen.findByText("Metrics & Impact")).toBeInTheDocument();
    const drafts = screen.getByText("Drafts from Co-Pilot").closest("div");
    expect(drafts).not.toBeNull();
    await user.click(screen.getAllByRole("button", { name: "Use this draft" })[0]);

    const editor = screen.getByLabelText("Cover letter text") as HTMLTextAreaElement;
    expect(editor.value).toContain("I led a migration that cut latency by [X%]");
    // Inline editing works.
    await user.type(editor, "\n\nP.S. Happy to discuss at your convenience.");
    expect(editor.value).toContain("P.S. Happy to discuss");

    // Save on this device.
    await user.click(screen.getByRole("button", { name: "Save on this device" }));
    await waitFor(() =>
      expect(screen.getByText("Saved on this device")).toBeInTheDocument(),
    );
    const stored = JSON.parse(
      window.localStorage.getItem("epitomejob.cover-letters.v1") ?? "[]",
    ) as { body: string }[];
    expect(stored).toHaveLength(1);
    expect(stored[0].body).toContain("P.S. Happy to discuss");

    // Export PDF reuses the builder export route via a letter document.
    await user.click(screen.getByRole("button", { name: "Export PDF" }));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/resume-builder/documents/doc-cover-1/export?format=pdf",
        expect.anything(),
      ),
    );
    const createCall = fetchMock.mock.calls.find(
      ([url, init]) =>
        url === "/api/resume-builder/documents" && (init as RequestInit)?.method === "POST",
    );
    expect(createCall).toBeDefined();
    const createdBody = JSON.parse(String((createCall![1] as RequestInit).body));
    expect(createdBody.title).toMatch(/Cover Letter/);
    expect(createdBody.state.summary).toContain("I led a migration");
    expect(createdBody.state.experience).toEqual([]);
  });

  it("shows a validation error without calling Co-Pilot when no resume is chosen", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    await user.type(screen.getByLabelText("Job description"), "A proper job description here.");
    await user.click(screen.getByRole("button", { name: "Generate cover letter" }));
    expect(await screen.findByText("Choose a resume to base the cover letter on.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining("/copilot"),
      expect.anything(),
    );
  });

  it("reopens a locally saved letter", async () => {
    const storage = memoryStorage();
    const letter = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Android Developer",
      jobDescription: "Saved JD here, long enough to be real.",
      body: "Dear Hiring Manager,\n\nSaved letter body.",
    });
    saveCoverLetter(storage, letter);
    window.localStorage.setItem(
      "epitomejob.cover-letters.v1",
      storage.store["epitomejob.cover-letters.v1"],
    );
    vi.stubGlobal("fetch", mockFetch());
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByText(letter.title)).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: letter.title }));
    expect(
      (screen.getByLabelText("Cover letter text") as HTMLTextAreaElement).value,
    ).toContain("Saved letter body.");
    expect(within(document.body).queryByText("Drafts from Co-Pilot")).not.toBeInTheDocument();
  });
});

describe("buildCoverLetterPrompt — variants (360°)", () => {
  it("includes extra notes verbatim and trims the JD", () => {
    const prompt = buildCoverLetterPrompt({
      resumeLabel: "Android Developer",
      jobDescription: "   We need a Java engineer with 5 years experience.   ",
      extraNotes: "Emphasise my API platform work",
    });
    expect(prompt).toMatch(/Additional instructions from the applicant: Emphasise my API platform work/);
    expect(prompt).toMatch(/Job description \(also in target_jd\):\nWe need a Java engineer/);
    expect(prompt).not.toMatch(/   We need/);
  });

  it("falls back to 'the owner of this resume' for an empty label", () => {
    const prompt = buildCoverLetterPrompt({
      resumeLabel: "",
      jobDescription: "We need a Java engineer with 5 years experience.",
    });
    expect(prompt).toMatch(/The applicant is the owner of this resume/);
  });

  it("states the role-only and company-only forms", () => {
    expect(
      buildCoverLetterPrompt({
        resumeLabel: "Resume",
        jobDescription: "We need a Java engineer with 5 years experience.",
        role: "Engineer",
      }),
    ).toMatch(/The role is Engineer\./);
    expect(
      buildCoverLetterPrompt({
        resumeLabel: "Resume",
        jobDescription: "We need a Java engineer with 5 years experience.",
        company: "Acme",
      }),
    ).toMatch(/The company is Acme\./);
  });

  it("always states the 3–5 options, under-400-words and placeholder rules", () => {
    const prompt = buildCoverLetterPrompt({
      resumeLabel: "Resume",
      jobDescription: "We need a Java engineer with 5 years experience.",
    });
    expect(prompt).toMatch(/3 to 5 distinct full-letter options/);
    expect(prompt).toMatch(/under 400 words/);
    expect(prompt).toMatch(/\[N\+ users\]/);
    expect(prompt).toMatch(/\[\$Y\]/);
    expect(prompt).toMatch(/greeting, 2–3 body paragraphs/);
  });
});

describe("letterFromOption — edge cases (360°)", () => {
  it("returns '' for no bullets and drops whitespace-only lines", () => {
    expect(
      letterFromOption({ id: "1", angle: "A", bullets: [], atsKeywords: [] }),
    ).toBe("");
    expect(
      letterFromOption({
        id: "1",
        angle: "A",
        bullets: ["  ", "Dear Hiring Manager,", "", "Body."],
        atsKeywords: [],
      }),
    ).toBe("Dear Hiring Manager,\n\nBody.");
  });

  it("trims each paragraph", () => {
    expect(
      letterFromOption({
        id: "1",
        angle: "A",
        bullets: ["  Dear Hiring Manager,  ", "  Body. "],
        atsKeywords: [],
      }),
    ).toBe("Dear Hiring Manager,\n\nBody.");
  });
});

describe("validateCoverLetterInputs — boundaries (360°)", () => {
  it("accepts exactly 20 characters and rejects 19", () => {
    expect(
      validateCoverLetterInputs({ resumeId: "doc-1", jobDescription: "x".repeat(20) }),
    ).toBeNull();
    expect(
      validateCoverLetterInputs({ resumeId: "doc-1", jobDescription: "x".repeat(19) }),
    ).toMatch(/job description/);
  });

  it("rejects whitespace-only JD and an empty resume id", () => {
    expect(
      validateCoverLetterInputs({ resumeId: "doc-1", jobDescription: "     " }),
    ).toMatch(/job description/);
    expect(
      validateCoverLetterInputs({ resumeId: "", jobDescription: "x".repeat(50) }),
    ).toMatch(/Choose a resume/);
  });
});

describe("coverLetterTitle + coverLetterFileName — edge cases (360°)", () => {
  it("titles from role-only or company-only, trimming whitespace", () => {
    expect(
      coverLetterTitle({ resumeLabel: "Resume", role: "  Engineer  " }),
    ).toBe("Cover Letter — Engineer");
    expect(
      coverLetterTitle({ resumeLabel: "Resume", company: "  Acme  " }),
    ).toBe("Cover Letter — Acme");
    expect(
      coverLetterTitle({ resumeLabel: "  Android Developer  " }),
    ).toBe("Cover Letter —   Android Developer  ");
  });

  it("slugs special characters, empty titles and very long titles", () => {
    expect(coverLetterFileName("Cover Letter — Café & Co.!", "pdf")).toBe(
      "cover-letter-caf-co.pdf",
    );
    expect(coverLetterFileName("", "pdf")).toBe("cover-letter.pdf");
    expect(coverLetterFileName("!!!", "docx")).toBe("cover-letter.docx");
    const long = coverLetterFileName(`Cover Letter — ${"x".repeat(100)}`, "pdf");
    expect(long).toBe(`cover-letter-${"x".repeat(60 - "cover-letter-".length)}.pdf`);
    expect(long.length).toBeLessThanOrEqual(60 + ".pdf".length);
  });
});

describe("newCoverLetterId / newCoverLetterDraft (360°)", () => {
  it("ids are unique and local-prefixed", () => {
    const a = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Resume",
      jobDescription: "JD",
      body: "Body.",
    });
    const b = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Resume",
      jobDescription: "JD",
      body: "Body.",
    });
    expect(a.id).not.toBe(b.id);
    expect(a.id).toMatch(/^local-/);
    expect(a.updatedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
  });

  it("trims company/role and titles from them", () => {
    const draft = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Resume",
      jobDescription: "JD",
      company: "  Acme  ",
      role: "  Engineer  ",
      body: "Body.",
    });
    expect(draft.company).toBe("Acme");
    expect(draft.role).toBe("Engineer");
    expect(draft.title).toBe("Cover Letter — Engineer at Acme");
  });
});

describe("local save — update, sort and corrupt shapes (360°)", () => {
  it("saving the same id replaces it in place — still one letter, newest first", () => {
    const storage = memoryStorage();
    const draft = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Android Developer",
      jobDescription: "JD one here, long enough to be real.",
      body: "First version.",
    });
    saveCoverLetter(storage, draft);
    saveCoverLetter(storage, { ...draft, body: "Edited version." });
    const loaded = loadCoverLetters(storage);
    expect(loaded).toHaveLength(1);
    expect(loaded[0].body).toBe("Edited version.");
  });

  it("sorts by updatedAt even when stored out of order", () => {
    const storage = memoryStorage();
    // Write raw storage: saveCoverLetter would overwrite updatedAt, so
    // the sort is only provable from storage written out of order.
    storage.setItem(
      "epitomejob.cover-letters.v1",
      JSON.stringify([
        { id: "a", body: "Newer.", updatedAt: "2026-11-17T00:00:00Z" },
        { id: "b", body: "Older.", updatedAt: "2026-11-01T00:00:00Z" },
      ]),
    );
    const loaded = loadCoverLetters(storage);
    expect(loaded[0].body).toBe("Newer.");
    expect(loaded[1].body).toBe("Older.");
  });

  it("drops entries missing an id or a body, and a non-array payload", () => {
    const storage = memoryStorage();
    storage.setItem(
      "epitomejob.cover-letters.v1",
      JSON.stringify([
        { id: "ok-1", body: "Good.", updatedAt: "2026-11-17T00:00:00Z" },
        { body: "No id." },
        { id: "no-body" },
        "a string",
        null,
      ]),
    );
    expect(loadCoverLetters(storage)).toHaveLength(1);
    storage.setItem("epitomejob.cover-letters.v1", JSON.stringify({ id: "x" }));
    expect(loadCoverLetters(storage)).toEqual([]);
    storage.setItem("epitomejob.cover-letters.v1", "");
    expect(loadCoverLetters(storage)).toEqual([]);
  });

  it("deleting an unknown id leaves the list unchanged; deleting from empty stays empty", () => {
    const storage = memoryStorage();
    const draft = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Resume",
      jobDescription: "JD",
      body: "Body.",
    });
    saveCoverLetter(storage, draft);
    expect(deleteCoverLetter(storage, "nope")).toHaveLength(1);
    expect(deleteCoverLetter(storage, draft.id)).toHaveLength(0);
    expect(deleteCoverLetter(storage, draft.id)).toHaveLength(0);
  });

  it("save-then-delete-then-save round-trips cleanly", () => {
    const storage = memoryStorage();
    const first = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Resume",
      jobDescription: "JD one.",
      body: "First.",
    });
    saveCoverLetter(storage, first);
    deleteCoverLetter(storage, first.id);
    const second = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Resume",
      jobDescription: "JD two.",
      body: "Second.",
    });
    saveCoverLetter(storage, second);
    const loaded = loadCoverLetters(storage);
    expect(loaded).toHaveLength(1);
    expect(loaded[0].body).toBe("Second.");
  });
});

describe("buildCoverLetterExportState — no mutation (360°)", () => {
  it("copies the contact block instead of aliasing it", () => {
    const state = emptyBuilderState();
    state.contact.fullName = "Alex Morgan";
    const exported = buildCoverLetterExportState(state.contact, "Dear Hiring Manager,\n\nBody.");
    exported.contact.fullName = "Changed Name";
    expect(state.contact.fullName).toBe("Alex Morgan");
    expect(exported.contact.email).toBe(state.contact.email);
  });

  it("accepts an empty body and keeps every contact field", () => {
    const state = emptyBuilderState();
    state.contact.email = "alex@example.com";
    state.contact.linkedin = "linkedin.com/in/alexmorgan";
    const exported = buildCoverLetterExportState(state.contact, "");
    expect(exported.summary).toBe("");
    expect(exported.contact.linkedin).toBe("linkedin.com/in/alexmorgan");
  });
});

describe("CoverLetterEditor flow — failures and save cycles (360°)", () => {
  function mockFetchWith(overrides: {
    copilot?: Response | (() => Response);
    exportFormat?: string;
  } = {}) {
    return vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input.toString();
      const method = init?.method ?? "GET";
      if (url === "/api/resume-builder/documents" && method === "GET") {
        return Promise.resolve(jsonResponse(DOCS));
      }
      if (url === "/api/resume-builder/documents/doc-1/copilot" && method === "POST") {
        const res =
          typeof overrides.copilot === "function"
            ? overrides.copilot()
            : overrides.copilot;
        return Promise.resolve(res ?? jsonResponse(COPILOT_RESPONSE));
      }
      if (url === "/api/resume-builder/documents" && method === "POST") {
        return Promise.resolve(jsonResponse({ ...DOCS[0], id: "doc-cover-1" }, 201));
      }
      if (url === "/api/resume-builder/documents/doc-cover-1/export?format=pdf") {
        return Promise.resolve(
          new Response("%PDF-fake", {
            status: 200,
            headers: {
              "Content-Type": "application/pdf",
              "Content-Disposition": 'attachment; filename="cover-letter.pdf"',
            },
          }),
        );
      }
      if (url === "/api/resume-builder/documents/doc-cover-1/export?format=docx") {
        return Promise.resolve(
          new Response("DOCX-fake", {
            status: 200,
            headers: {
              "Content-Type":
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "Content-Disposition": 'attachment; filename="cover-letter.docx"',
            },
          }),
        );
      }
      return Promise.resolve(jsonResponse({ message: `unexpected ${method} ${url}` }, 404));
    });
  }

  async function generateAndPick(user: ReturnType<typeof userEvent.setup>) {
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.type(
      screen.getByLabelText("Job description"),
      "We are hiring a Senior Software Engineer with Java and Spring Boot experience.",
    );
    await user.click(screen.getByRole("button", { name: "Generate cover letter" }));
    expect(await screen.findByText("Metrics & Impact")).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: "Use this draft" })[0]);
  }

  it("shows the Co-Pilot error when generation fails (503)", async () => {
    const fetchMock = mockFetchWith({
      copilot: jsonResponse({ message: "Co-Pilot unavailable" }, 503),
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.type(
      screen.getByLabelText("Job description"),
      "We are hiring a Senior Software Engineer with Java and Spring Boot experience.",
    );
    await user.click(screen.getByRole("button", { name: "Generate cover letter" }));
    expect(await screen.findByText("Co-Pilot unavailable")).toBeInTheDocument();
    expect(screen.queryByText("Drafts from Co-Pilot")).not.toBeInTheDocument();
  });

  it("asks for more detail when Co-Pilot returns zero options", async () => {
    vi.stubGlobal("fetch", mockFetchWith({ copilot: jsonResponse({ options: [] }) }));
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.type(
      screen.getByLabelText("Job description"),
      "We are hiring a Senior Software Engineer with Java and Spring Boot experience.",
    );
    await user.click(screen.getByRole("button", { name: "Generate cover letter" }));
    expect(
      await screen.findByText(/asked for more detail before drafting/),
    ).toBeInTheDocument();
  });

  it("shows a generic message when the Co-Pilot call itself throws", async () => {
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url.includes("/copilot")) return Promise.reject(new Error("Network down"));
      if (url === "/api/resume-builder/documents") return Promise.resolve(jsonResponse(DOCS));
      return Promise.resolve(jsonResponse({ message: "not found" }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.type(
      screen.getByLabelText("Job description"),
      "We are hiring a Senior Software Engineer with Java and Spring Boot experience.",
    );
    await user.click(screen.getByRole("button", { name: "Generate cover letter" }));
    expect(
      await screen.findByText("Could not generate the cover letter."),
    ).toBeInTheDocument();
  });

  it("sends the hiring manager and extra notes in the Co-Pilot prompt", async () => {
    const fetchMock = mockFetchWith();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    await user.selectOptions(screen.getByLabelText("Resume"), "doc-1");
    await user.type(
      screen.getByLabelText("Job description"),
      "We are hiring a Senior Software Engineer with Java and Spring Boot experience.",
    );
    await user.type(screen.getByLabelText("Hiring manager (optional)"), "Priya Shah");
    await user.type(
      screen.getByLabelText("Extra instructions (optional)"),
      "Emphasise my API platform work",
    );
    await user.click(screen.getByRole("button", { name: "Generate cover letter" }));
    expect(await screen.findByText("Metrics & Impact")).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) =>
      (url as string).includes("/copilot"),
    );
    const body = JSON.parse(String((call![1] as RequestInit).body));
    expect(body.active_section).toBe("cover_letter");
    expect(body.user_context).toMatch(/Priya Shah/);
    expect(body.user_context).toMatch(/Emphasise my API platform work/);
    expect(body.target_jd).toMatch(/Senior Software Engineer/);
  });

  it("picking the second draft replaces the first draft's body", async () => {
    vi.stubGlobal("fetch", mockFetchWith());
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await generateAndPick(user);
    expect(
      (screen.getByLabelText("Cover letter text") as HTMLTextAreaElement).value,
    ).toContain("I led a migration that cut latency by [X%]");
    await user.click(screen.getAllByRole("button", { name: "Use this draft" })[1]);
    const editor = screen.getByLabelText("Cover letter text") as HTMLTextAreaElement;
    expect(editor.value).toContain("Eight years building Java services");
    expect(editor.value).not.toContain("I led a migration");
  });

  it("disables Save and Export while the letter is empty", async () => {
    vi.stubGlobal("fetch", mockFetchWith());
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    expect(screen.getByRole("button", { name: "Save on this device" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Export PDF" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Download DOCX" })).toBeDisabled();
  });

  it("save → edit → save again keeps exactly one letter, with the new body", async () => {
    const fetchMock = mockFetchWith();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await generateAndPick(user);
    await user.click(screen.getByRole("button", { name: "Save on this device" }));
    await waitFor(() =>
      expect(screen.getByText("Saved on this device")).toBeInTheDocument(),
    );
    const editor = screen.getByLabelText("Cover letter text") as HTMLTextAreaElement;
    await user.type(editor, "\n\nUpdated line.");
    await user.click(screen.getByRole("button", { name: "Save on this device" }));
    const stored = JSON.parse(
      window.localStorage.getItem("epitomejob.cover-letters.v1") ?? "[]",
    ) as { body: string }[];
    expect(stored).toHaveLength(1);
    expect(stored[0].body).toContain("Updated line.");
  });

  it("deleting a saved letter removes it from the list and storage", async () => {
    const storage = memoryStorage();
    const letter = newCoverLetterDraft({
      resumeId: "doc-1",
      resumeLabel: "Android Developer",
      jobDescription: "Saved JD here, long enough to be real.",
      body: "Dear Hiring Manager,\n\nTo delete.",
    });
    saveCoverLetter(storage, letter);
    window.localStorage.setItem(
      "epitomejob.cover-letters.v1",
      storage.store["epitomejob.cover-letters.v1"],
    );
    vi.stubGlobal("fetch", mockFetchWith());
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() => expect(screen.getByText(letter.title)).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Delete" }));
    expect(screen.queryByText(letter.title)).not.toBeInTheDocument();
    expect(
      JSON.parse(window.localStorage.getItem("epitomejob.cover-letters.v1") ?? "[]"),
    ).toHaveLength(0);
  });

  it("a custom title is kept on save", async () => {
    vi.stubGlobal("fetch", mockFetchWith());
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await generateAndPick(user);
    await user.type(screen.getByLabelText("Title"), "My Navy Federal letter");
    await user.click(screen.getByRole("button", { name: "Save on this device" }));
    await waitFor(() =>
      expect(screen.getByText("Saved on this device")).toBeInTheDocument(),
    );
    const stored = JSON.parse(
      window.localStorage.getItem("epitomejob.cover-letters.v1") ?? "[]",
    ) as { title: string }[];
    expect(stored[0].title).toBe("My Navy Federal letter");
    expect(screen.getByText("My Navy Federal letter")).toBeInTheDocument();
  });

  it("exports DOCX through the builder export route", async () => {
    const fetchMock = mockFetchWith();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await generateAndPick(user);
    await user.click(screen.getByRole("button", { name: "Download DOCX" }));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/resume-builder/documents/doc-cover-1/export?format=docx",
        expect.anything(),
      ),
    );
  });

  it("export with no resume chosen reports the problem instead of calling the API", async () => {
    const fetchMock = mockFetchWith();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    // Write a letter by hand with no resume selected.
    await user.type(
      screen.getByLabelText("Cover letter text"),
      "Dear Hiring Manager,\n\nHand-written letter.",
    );
    await user.click(screen.getByRole("button", { name: "Export PDF" }));
    expect(
      await screen.findByText(/Choose a resume first — the export carries its contact details/),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      "/api/resume-builder/documents",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("shows the no-resumes hint and blocks generation when the list is empty", async () => {
    const fetchMock = vi.fn((input: string | URL | Request) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url === "/api/resume-builder/documents") return Promise.resolve(jsonResponse([]));
      return Promise.resolve(jsonResponse({ message: "not found" }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<CoverLetterEditor />, { wrapper });
    expect(
      await screen.findByText("No resumes yet — create one in Resume Builder first."),
    ).toBeInTheDocument();
    await user.type(screen.getByLabelText("Job description"), "A proper job description here.");
    await user.click(screen.getByRole("button", { name: "Generate cover letter" }));
    expect(await screen.findByText("Choose a resume to base the cover letter on.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining("/copilot"),
      expect.anything(),
    );
  });

  it("corrupt localStorage does not crash the editor or show a saved list", async () => {
    window.localStorage.setItem("epitomejob.cover-letters.v1", "{not json");
    vi.stubGlobal("fetch", mockFetchWith());
    render(<CoverLetterEditor />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "Android Developer" })).toBeInTheDocument(),
    );
    expect(screen.queryByText("Saved on this device")).not.toBeInTheDocument();
  });

  it("shows an Untitled resume option for a document with a blank title", async () => {
    const fetchMock = vi.fn((input: string | URL | Request) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url === "/api/resume-builder/documents") {
        return Promise.resolve(jsonResponse([{ ...DOCS[0], title: "   " }]));
      }
      return Promise.resolve(jsonResponse({ message: "not found" }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<CoverLetterEditor />, { wrapper });
    expect(
      await screen.findByRole("option", { name: "Untitled resume" }),
    ).toBeInTheDocument();
  });
});
