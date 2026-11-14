// Resume Builder web tests: pure helpers (validation, rendering, state
// patches, Co-Pilot apply, normalisers), the BFF client functions with
// fetch mocked at the /api/* boundary, and the page flow (entry →
// templates → editor → AI apply → download / save-to-profile) with the
// same mocked fetch. No real backend.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import {
  exportBuilderDocument,
  fetchBuilderTemplates,
  parseBuilderResume,
  requestCopilot,
  saveBuilderDocumentToProfile,
} from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import {
  BUILTIN_TEMPLATES,
  applyCopilotOption,
  copilotContextFor,
  emptyBuilderState,
  filenameFromContentDisposition,
  hasPlaceholders,
  highlightPlaceholders,
  normalizeBuilderDocument,
  normalizeBuilderTemplates,
  normalizeCopilotResponse,
  renderTemplateHtml,
  sampleBuilderState,
  splitPlaceholders,
  triggerBlobDownload,
  updateExperienceItem,
  validateBuilderFile,
} from "@/lib/resume-builder";
import type { ResumeBuilderState } from "@/lib/types";
import { TemplateGallery } from "@/components/resume-builder/template-gallery";
import ResumeBuilderPage from "@/app/resume-builder/page";

// The page reads ?documentId= via next/navigation; control it per test.
const navMock = vi.hoisted(() => ({
  params: new URLSearchParams(),
  replace: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => navMock.params,
  usePathname: () => "/resume-builder",
  useRouter: () => ({ replace: navMock.replace }),
}));

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

const TEMPLATES = BUILTIN_TEMPLATES.map((t) => ({
  id: t.id,
  name: t.name,
  description: t.description,
  html: t.html,
}));

const PARSED_DOC = {
  id: "doc-1",
  templateId: null,
  title: "Parsed resume",
  state: {
    contact: {
      fullName: "Alex Morgan",
      title: "Senior Software Engineer",
      email: "alex@example.com",
      phone: "555-0100",
      location: "Reston, VA",
      linkedin: "linkedin.com/in/alexmorgan",
    },
    summary: "Java engineer with 8 years of experience.",
    experience: [
      {
        id: "exp-1",
        title: "Senior Engineer",
        company: "Acme",
        startDate: "2021",
        endDate: "Present",
        bullets: ["Shipped the claims API."],
      },
    ],
    education: [],
    skills: ["Java", "Spring Boot"],
  },
};

const COPILOT = {
  options: [
    {
      id: "opt-1",
      angle: "Impact-led",
      bullets: ["Cut p95 latency by [X%] across 12 services."],
      atsKeywords: ["Spring Boot", "Kafka"],
    },
    {
      id: "opt-2",
      angle: "ATS-first",
      bullets: ["Led Spring Boot migration for 12 services."],
      atsKeywords: ["Java", "Microservices"],
    },
    {
      id: "opt-3",
      angle: "Concise",
      bullets: ["Delivered 12-service Spring Boot migration."],
      atsKeywords: ["Spring Boot"],
    },
  ],
};

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
  vi.restoreAllMocks();
  useAuthStore.getState().logout();
});

describe("validateBuilderFile", () => {
  it("accepts .pdf / .doc / .docx and rejects anything else", () => {
    expect(
      validateBuilderFile(new File(["x"], "cv.pdf", { type: "application/pdf" })),
    ).toBeNull();
    expect(
      validateBuilderFile(
        new File(["x"], "cv.docx", {
          type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        }),
      ),
    ).toBeNull();
    expect(
      validateBuilderFile(new File(["x"], "cv.doc", { type: "application/msword" })),
    ).toBeNull();
    expect(validateBuilderFile(new File(["x"], "cv.txt", { type: "text/plain" }))).toBe(
      "Choose a .pdf, .doc or .docx file.",
    );
    expect(validateBuilderFile(new File([], "empty.pdf"))).toMatch(/empty/);
  });
});

describe("renderTemplateHtml", () => {
  it("substitutes tokens, escapes user HTML, and highlights [placeholders]", () => {
    const state = sampleBuilderState();
    state.contact.fullName = "<b>Ana</b>";
    state.summary = "Grew revenue by [X%] in one quarter.";
    const html = renderTemplateHtml(BUILTIN_TEMPLATES[0], state);
    expect(html).toContain("&lt;b&gt;Ana&lt;/b&gt;");
    expect(html).not.toContain("<b>Ana</b>");
    expect(html).toContain("Alex Morgan".replace("Alex Morgan", "Senior Software Engineer"));
    expect(html).toContain("Senior Software Engineer");
    expect(html).toContain("Meridian Care Technologies");
    expect(html).toContain("Java, Spring Boot, Kafka");
    expect(highlightPlaceholders("[X%]")).toContain("<mark");
    expect(html).toContain("[X%]");
    expect(html).toContain("background:#fde68a");
  });

  it("falls back to the built-in layout for a token-less template html", () => {
    const html = renderTemplateHtml(
      { id: "classic", name: "Classic", description: "", html: "<p>no tokens</p>" },
      sampleBuilderState(),
    );
    expect(html).toContain("Alex Morgan");
    expect(html).toContain("George Mason University");
  });

  it("renders the same HTML for gallery and preview", () => {
    const state = sampleBuilderState();
    expect(renderTemplateHtml(BUILTIN_TEMPLATES[1], state)).toContain("Alex Morgan");
    expect(hasPlaceholders({ ...emptyBuilderState(), summary: "Did [X]" })).toBe(true);
    expect(hasPlaceholders(emptyBuilderState())).toBe(false);
  });
});

describe("splitPlaceholders", () => {
  it("splits bracketed placeholders out of option text", () => {
    const parts = splitPlaceholders("Cut latency by [X%] in [Q1].");
    expect(parts.filter((p) => p.isPlaceholder).map((p) => p.text)).toEqual([
      "[X%]",
      "[Q1]",
    ]);
    expect(splitPlaceholders("plain text")).toEqual([
      { text: "plain text", isPlaceholder: false },
    ]);
  });
});

describe("state patches + Co-Pilot apply", () => {
  it("updates one experience item without touching the rest", () => {
    const state = sampleBuilderState();
    const next = updateExperienceItem(state, 0, { title: "Staff Engineer" });
    expect(next.experience[0].title).toBe("Staff Engineer");
    expect(next.experience[0].company).toBe("Meridian Care Technologies");
    expect(state.experience[0].title).toBe("Senior Software Engineer");
  });

  it("applies a summary option as prose and an experience option as bullets", () => {
    const state = sampleBuilderState();
    const summary = applyCopilotOption(
      state,
      { section: "summary", label: "Summary" },
      { id: "o1", angle: "Impact-led", bullets: ["A.", "B."], atsKeywords: [] },
    );
    expect(summary.summary).toBe("A. B.");

    const exp = applyCopilotOption(
      state,
      { section: "experience", index: 0, label: "Experience 1" },
      { id: "o2", angle: "Impact-led", bullets: ["Did [X%]."], atsKeywords: [] },
    );
    expect(exp.experience[0].bullets).toEqual(["Did [X%]."]);
    expect(exp.summary).toBe(state.summary);
  });

  it("applies a skills option as bullets + ATS keywords, de-duplicated", () => {
    const state = sampleBuilderState();
    const next = applyCopilotOption(
      state,
      { section: "skills", label: "Skills" },
      { id: "o3", angle: "ATS-first", bullets: ["Kafka"], atsKeywords: ["Java", "Kafka"] },
    );
    expect(next.skills).toEqual(["Kafka", "Java"]);
  });

  it("builds the right user_context per section", () => {
    const state = sampleBuilderState();
    expect(copilotContextFor(state, { section: "summary", label: "Summary" })).toContain(
      "8 years",
    );
    expect(
      copilotContextFor(state, { section: "experience", index: 0, label: "Experience 1" }),
    ).toContain("Meridian Care Technologies");
    expect(copilotContextFor(state, { section: "skills", label: "Skills" })).toBe(
      "Java, Spring Boot, Kafka, Azure, PostgreSQL",
    );
  });
});

describe("normalisers", () => {
  it("normalises a parsed document and tolerates snake_case + bare state", () => {
    const doc = normalizeBuilderDocument({
      documentId: "doc-9",
      template_id: "modern",
      state: { contact: { fullName: "Ana" }, summary: "Hi", experience: [], education: [], skills: [] },
    });
    expect(doc.id).toBe("doc-9");
    expect(doc.templateId).toBe("modern");
    expect(doc.state.contact.fullName).toBe("Ana");

    const bare = normalizeBuilderDocument({ summary: "Just a state" });
    expect(bare.id).toBeNull();
    expect(bare.state.summary).toBe("Just a state");
    expect(bare.state.experience).toEqual([]);
  });

  it("normalises templates ({templates:[...]} or bare array) and copilot options", () => {
    expect(normalizeBuilderTemplates({ templates: TEMPLATES }).length).toBe(3);
    expect(normalizeBuilderTemplates(TEMPLATES).map((t) => t.id)).toEqual([
      "modern",
      "classic",
      "minimal",
    ]);
    const res = normalizeCopilotResponse({
      suggestions: [
        { angle: "A", bullets: "line one\nline two", ats_keywords: ["Java"] },
      ],
    });
    expect(res.options[0].bullets).toEqual(["line one", "line two"]);
    expect(res.options[0].atsKeywords).toEqual(["Java"]);
  });

  it("reads the filename from Content-Disposition", () => {
    expect(
      filenameFromContentDisposition('attachment; filename="resume.pdf"', "resume.pdf"),
    ).toBe("resume.pdf");
    expect(filenameFromContentDisposition(null, "resume.docx")).toBe("resume.docx");
  });
});

describe("builder API client", () => {
  it("falls back to built-in templates when the list call fails", async () => {
    const fetchMock = mockFetch(() => jsonResponse({ message: "boom" }, 503));
    vi.stubGlobal("fetch", fetchMock);
    const templates = await fetchBuilderTemplates();
    expect(templates.map((t) => t.id)).toEqual(["modern", "classic", "minimal"]);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/resume-builder/templates",
      expect.anything(),
    );
  });

  it("returns the server template list when it succeeds", async () => {
    vi.stubGlobal("fetch", mockFetch(() => jsonResponse(TEMPLATES)));
    const templates = await fetchBuilderTemplates();
    expect(templates.map((t) => t.name)).toEqual(["Modern", "Classic", "Minimal"]);
  });

  it("POSTs the file as multipart to /resume-builder/parse", async () => {
    const fetchMock = mockFetch((_url, init) => {
      expect(init?.method).toBe("POST");
      expect((init?.body as FormData).get("file")).toBeInstanceOf(File);
      return jsonResponse(PARSED_DOC);
    });
    vi.stubGlobal("fetch", fetchMock);
    const doc = await parseBuilderResume(new File(["%PDF"], "cv.pdf"));
    expect(doc.id).toBe("doc-1");
    expect(doc.state.contact.fullName).toBe("Alex Morgan");
  });

  it("POSTs the copilot body with active_section, user_context and resume_state", async () => {
    let sent: Record<string, unknown> | null = null;
    vi.stubGlobal(
      "fetch",
      mockFetch((url, init) => {
        expect(url).toBe("/api/resume-builder/documents/doc-1/copilot");
        sent = JSON.parse(init?.body as string) as Record<string, unknown>;
        return jsonResponse(COPILOT);
      }),
    );
    const state = sampleBuilderState();
    const res = await requestCopilot("doc-1", {
      active_section: "summary",
      user_context: state.summary,
      target_jd: "Senior Java Engineer",
      resume_state: state,
    });
    expect(sent).toMatchObject({
      active_section: "summary",
      target_jd: "Senior Java Engineer",
    });
    expect((sent as unknown as Record<string, unknown>).resume_state).toBeDefined();
    expect(res.options).toHaveLength(3);
    expect(res.options[0].angle).toBe("Impact-led");
  });

  it("downloads the export blob with the server filename", async () => {
    // String body, not new Blob(...): a Response built from a jsdom Blob
    // breaks under Node 22 — jsdom's Blob has no .stream() (CI #34).
    const res = new Response("%PDF-fake", {
      status: 200,
      headers: {
        "Content-Type": "application/pdf",
        "Content-Disposition": 'attachment; filename="alex-morgan.pdf"',
      },
    });
    vi.stubGlobal("fetch", mockFetch(() => res));
    const out = await exportBuilderDocument("doc-1", "pdf");
    expect(out.filename).toBe("alex-morgan.pdf");
    expect(out.blob.size).toBeGreaterThan(0);
  });

  it("POSTs {label} to save-to-profile and returns the resume id", async () => {
    vi.stubGlobal(
      "fetch",
      mockFetch((url, init) => {
        expect(url).toBe("/api/resume-builder/documents/doc-1/save-to-profile");
        expect(init?.body).toBe(JSON.stringify({ label: "Modern resume" }));
        return jsonResponse({ resumeId: "resume-7", label: "Modern resume" });
      }),
    );
    const result = await saveBuilderDocumentToProfile("doc-1", "Modern resume");
    expect(result.resumeId).toBe("resume-7");
  });
});

describe("triggerBlobDownload", () => {
  it("creates an object URL and clicks an anchor with the filename", () => {
    const createSpy = vi
      .spyOn(URL, "createObjectURL")
      .mockReturnValue("blob:fake-url");
    const revokeSpy = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    triggerBlobDownload(new Blob(["x"]), "resume.pdf");
    expect(createSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeSpy).not.toHaveBeenCalled();
  });
});

describe("TemplateGallery", () => {
  it("renders ≥3 templates with live thumbnails from the template HTML", () => {
    const state = sampleBuilderState();
    render(
      <TemplateGallery
        templates={TEMPLATES}
        state={state}
        selectedId={null}
        onSelect={() => {}}
      />,
    );
    expect(screen.getByText("Modern")).toBeInTheDocument();
    expect(screen.getByText("Classic")).toBeInTheDocument();
    expect(screen.getByText("Minimal")).toBeInTheDocument();
    const frames = screen.getAllByTitle(/template preview/);
    expect(frames).toHaveLength(3);
    for (const frame of frames) {
      expect(frame.getAttribute("srcDoc")).toContain("Alex Morgan");
    }
    expect(screen.getAllByRole("button", { name: /Use / })).toHaveLength(3);
  });

  it("marks the selected template and calls onSelect", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(
      <TemplateGallery
        templates={TEMPLATES}
        state={sampleBuilderState()}
        selectedId="classic"
        onSelect={onSelect}
      />,
    );
    expect(screen.getByText("Selected")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Use Minimal" }));
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ id: "minimal" }),
    );
  });
});

describe("ResumeBuilderPage flow", () => {
  function stubBuilderPage(copilotImpl?: () => Response) {
    const fetchMock = mockFetch((url, init) => {
      if (url === "/api/resume-builder/templates") return jsonResponse(TEMPLATES);
      if (url === "/api/resume-builder/parse" && init?.method === "POST") {
        return jsonResponse(PARSED_DOC, 201);
      }
      if (url === "/api/resume-builder/documents" && init?.method === "POST") {
        return jsonResponse({ ...PARSED_DOC, id: "doc-1", templateId: "modern" }, 201);
      }
      if (url === "/api/resume-builder/documents/doc-1" && init?.method === "PUT") {
        return jsonResponse({ ...PARSED_DOC, templateId: "modern" });
      }
      if (url === "/api/resume-builder/documents/doc-1/copilot") {
        if (copilotImpl) return copilotImpl();
        return jsonResponse(COPILOT);
      }
      if (url.startsWith("/api/resume-builder/documents/doc-1/export")) {
        // String body, not new Blob(...) — see the note above (CI #34).
        return new Response("%PDF-fake", {
          status: 200,
          headers: {
            "Content-Type": "application/pdf",
            "Content-Disposition": 'attachment; filename="alex-morgan.pdf"',
          },
        });
      }
      if (url === "/api/resume-builder/documents/doc-1/save-to-profile") {
        return jsonResponse({ resumeId: "resume-7", label: "Modern resume" });
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
  }

  it("entry → Build fresh → template pick → editor; typing updates the live preview", async () => {
    const user = userEvent.setup();
    stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });

    expect(
      screen.getByText(/Upload your resume to start from what you have/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Upload a resume/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Build fresh/ })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Build fresh/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    expect(screen.getByText("Modern")).toBeInTheDocument();
    expect(screen.getByText("Classic")).toBeInTheDocument();
    expect(screen.getByText("Minimal")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(screen.getByLabelText("Full name")).toBeInTheDocument(),
    );
    expect(screen.getByText("Template: Modern")).toBeInTheDocument();
    expect(navMock.replace).toHaveBeenCalledWith(
      expect.stringContaining("documentId=doc-1"),
      expect.anything(),
    );

    // Typing in a field re-renders the preview iframe on every keystroke.
    const nameInput = screen.getByLabelText("Full name") as HTMLInputElement;
    await user.type(nameInput, "Alex Morgan");
    const preview = screen.getByTestId("resume-preview") as HTMLIFrameElement;
    expect(preview.getAttribute("srcDoc")).toContain("Alex Morgan");
    expect(screen.getByRole("button", { name: "Download PDF" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download DOCX" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save to profile" })).toBeInTheDocument();
  });

  it("upload → parse → templates with the parsed state", async () => {
    stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });

    await userEvent.setup().click(screen.getByRole("button", { name: /Upload a resume/ }));
    const input = screen.getByTestId("builder-file-input") as HTMLInputElement;
    const file = new File(["%PDF"], "cv.pdf", { type: "application/pdf" });
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Use Classic" }));
    await waitFor(() =>
      expect(screen.getByLabelText("Full name")).toBeInTheDocument(),
    );
    expect((screen.getByLabelText("Full name") as HTMLInputElement).value).toBe(
      "Alex Morgan",
    );
    const preview = screen.getByTestId("resume-preview") as HTMLIFrameElement;
    expect(preview.getAttribute("srcDoc")).toContain("Java engineer with 8 years");
  });

  it("rejects a .txt upload client-side without calling the server", async () => {
    const fetchMock = stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });
    await userEvent.setup().click(screen.getByRole("button", { name: /Upload a resume/ }));
    const input = screen.getByTestId("builder-file-input") as HTMLInputElement;
    fireEvent.change(input, {
      target: { files: [new File(["x"], "cv.txt", { type: "text/plain" })] },
    });
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/\.pdf, \.doc or \.docx/),
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      "/api/resume-builder/parse",
      expect.anything(),
    );
  });

  it("AI options appear, apply patches the block, [placeholder] is highlighted", async () => {
    const user = userEvent.setup();
    stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });

    await user.click(screen.getByRole("button", { name: /Build fresh/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(screen.getByLabelText("Summary")).toBeInTheDocument(),
    );

    await user.click(screen.getByRole("button", { name: "Improve summary with AI" }));
    await waitFor(() =>
      expect(screen.getByTestId("copilot-panel")).toBeInTheDocument(),
    );
    await waitFor(() => expect(screen.getByText("Impact-led")).toBeInTheDocument());
    expect(screen.getByText("ATS-first")).toBeInTheDocument();
    expect(screen.getByText("Concise")).toBeInTheDocument();
    // [X%] inside the option is highlighted amber.
    const panel = screen.getByTestId("copilot-panel");
    expect(within(panel).getByText("[X%]").tagName).toBe("MARK");

    const optionCards = within(panel).getAllByRole("button", { name: "Apply this option" });
    await user.click(optionCards[0]);
    await waitFor(() => expect(screen.getByText("Applied")).toBeInTheDocument());
    expect((screen.getByLabelText("Summary") as HTMLTextAreaElement).value).toBe(
      "Cut p95 latency by [X%] across 12 services.",
    );
    // The applied placeholder is highlighted in the live preview too.
    const preview = screen.getByTestId("resume-preview") as HTMLIFrameElement;
    expect(preview.getAttribute("srcDoc")).toContain("background:#fde68a");
    expect(screen.getByText(/still need a real value/)).toBeInTheDocument();
  });

  it("shows a plain message (no crash) when Co-Pilot is unavailable", async () => {
    const user = userEvent.setup();
    stubBuilderPage(() => jsonResponse({ message: "Co-Pilot unavailable" }, 503));
    render(<ResumeBuilderPage />, { wrapper });

    await user.click(screen.getByRole("button", { name: /Build fresh/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(screen.getByLabelText("Summary")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Improve summary with AI" }));
    await waitFor(() =>
      expect(screen.getByTestId("copilot-panel")).toBeInTheDocument(),
    );
    await waitFor(() =>
      expect(within(screen.getByTestId("copilot-panel")).getByRole("alert")).toHaveTextContent(
        "Co-Pilot unavailable",
      ),
    );
    // The page is still usable — the editor is intact.
    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
  });

  it("download triggers a file download and save-to-profile shows the resume id + link", async () => {
    const user = userEvent.setup();
    const createSpy = vi
      .spyOn(URL, "createObjectURL")
      .mockReturnValue("blob:fake-url");
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });

    await user.click(screen.getByRole("button", { name: /Build fresh/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Download PDF" })).toBeInTheDocument(),
    );

    await user.click(screen.getByRole("button", { name: "Download PDF" }));
    await waitFor(() => expect(createSpy).toHaveBeenCalled());

    const labelInput = screen.getByLabelText("Save to profile as") as HTMLInputElement;
    expect(labelInput.value).toBe("Modern resume");
    await user.click(screen.getByRole("button", { name: "Save to profile" }));
    await waitFor(() =>
      expect(screen.getByText(/Saved as resume resume-7/)).toBeInTheDocument(),
    );
    expect(screen.getByRole("link", { name: "View in Resumes" })).toHaveAttribute(
      "href",
      "/resumes",
    );
  });

  it("add / remove experience items updates state and preview", async () => {
    const user = userEvent.setup();
    stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });

    await user.click(screen.getByRole("button", { name: /Build fresh/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Use Minimal" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Add experience" })).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Add experience" }));
    expect(screen.getByLabelText("Job title")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Job title"), "Staff Engineer");
    const preview = screen.getByTestId("resume-preview") as HTMLIFrameElement;
    expect(preview.getAttribute("srcDoc")).toContain("Staff Engineer");
    await user.click(screen.getByRole("button", { name: "Remove experience 1" }));
    expect(screen.queryByLabelText("Job title")).not.toBeInTheDocument();
  });
});
