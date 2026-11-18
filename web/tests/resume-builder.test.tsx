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
  DEFAULT_DESIGN,
  analyzeResume,
  applyCopilotOption,
  applyDesignOverrides,
  containsKeyword,
  copilotContextFor,
  designToCss,
  emptyBuilderState,
  filenameFromContentDisposition,
  filterBuilderDocuments,
  hasPlaceholders,
  highlightPlaceholders,
  keywordsFromJobDescription,
  matchJobDescription,
  normalizeBuilderDocument,
  normalizeBuilderDocuments,
  normalizeBuilderTemplates,
  normalizeCopilotResponse,
  renderTemplateHtml,
  sampleBuilderState,
  sortBuilderDocuments,
  splitPlaceholders,
  templateLayoutOf,
  templateStyleOf,
  triggerBlobDownload,
  updateExperienceItem,
  validateBuilderFile,
} from "@/lib/resume-builder";
import type { BuilderTemplate, ResumeBuilderState } from "@/lib/types";
import { TemplateGallery } from "@/components/resume-builder/template-gallery";
import { TemplateLibraryModal } from "@/components/resume-builder/template-library-modal";
import { DesignerPanel } from "@/components/resume-builder/designer-panel";
import { AnalyzerPanel } from "@/components/resume-builder/analyzer-panel";
import { JobMatcherPanel } from "@/components/resume-builder/job-matcher-panel";
import { BuilderMenu } from "@/components/resume-builder/builder-menu";
import { ResumeHome } from "@/components/resume-builder/resume-home";
import {
  BUILDER_TOUR_SEEN_KEY,
  QuickTourModal,
} from "@/components/resume-builder/quick-tour";
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

  it("renders the backend template tokens ({{experienceHtml}} etc.)", () => {
    // The resume-builder-service templates use the *Html suffix; the web
    // preview must substitute them too, or Experience / Education / Skills
    // render empty even though the state has the data (issue #10).
    const backendStyle: BuilderTemplate = {
      id: "modern",
      name: "Modern",
      description: "",
      html: `<!doctype html><html><head><meta charset="utf-8"></head><body><h1>{{fullName}}</h1><h2>Summary</h2><p>{{summary}}</p><h2>Experience</h2>{{experienceHtml}}<h2>Education</h2>{{educationHtml}}<h2>Skills</h2><p>{{skillsHtml}}</p></body></html>`,
    };
    const html = renderTemplateHtml(backendStyle, sampleBuilderState());
    expect(html).toContain("Alex Morgan");
    expect(html).toContain("Meridian Care Technologies");
    expect(html).toContain("George Mason University");
    expect(html).toContain("Java, Spring Boot, Kafka");
    expect(html).not.toContain("{{experienceHtml}}");
    expect(html).not.toContain("{{educationHtml}}");
    expect(html).not.toContain("{{skillsHtml}}");
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

  it("hiding contact removes the whole header block, not just the fields (arch #2)", () => {
    // Contact has no <h2>, so the heading-strip cannot hide it. Hiding
    // it must not leave a separator-only line (" ·  ·  · ") or an empty
    // <h1> / title in the preview.
    const state = sampleBuilderState();
    const html = renderTemplateHtml(BUILTIN_TEMPLATES[0], state, {
      hideSections: ["contact"],
    });
    expect(html).not.toContain("Alex Morgan");
    expect(html).not.toMatch(/<h1[^>]*>\s*<\/h1>/);
    expect(html).not.toContain("·  ·  ·");
    expect(html).not.toContain("|  |  |");
    // The rest of the document is untouched.
    expect(html).toContain("Senior Software Engineer");
    expect(html).toContain("Meridian Care Technologies");
    // Same for a backend-shaped template (header div + | separators).
    const backendStyle: BuilderTemplate = {
      id: "modern",
      name: "Modern",
      description: "",
      html: `<!doctype html><html><head><meta charset="utf-8"></head><body><div class="header"><h1>{{fullName}}</h1><p>{{email}} | {{phone}} | {{linkedin}} | {{location}}</p></div><h2>Summary</h2><p>{{summary}}</p></body></html>`,
    };
    const backendHtml = renderTemplateHtml(backendStyle, state, {
      hideSections: ["contact"],
    });
    expect(backendHtml).not.toContain("Alex Morgan");
    expect(backendHtml).not.toMatch(/<h1[^>]*>\s*<\/h1>/);
    expect(backendHtml).not.toContain("|  |  |");
    // The rest of the document is untouched (this template has no
    // {{title}} token, so the summary is the check).
    expect(backendHtml).toContain("Software engineer with 8 years");
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
    // Select-only grid: one select button per template, no commit here.
    expect(
      screen.getAllByRole("button", { name: /Select .* template/ }),
    ).toHaveLength(3);
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
    await user.click(screen.getByRole("button", { name: "Select Minimal template" }));
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ id: "minimal" }),
    );
  });
});

describe("ResumeBuilderPage flow", () => {
  function stubBuilderPage(copilotImpl?: () => Response) {
    // The page shows the Quick Tour on first visit; the flow tests cover
    // the editor, not the tour, so mark it seen first.
    window.localStorage.setItem(BUILDER_TOUR_SEEN_KEY, "1");
    const fetchMock = mockFetch((url, init) => {
      if (url === "/api/resume-builder/templates") return jsonResponse(TEMPLATES);
      if (
        url === "/api/resume-builder/documents" &&
        (!init?.method || init.method === "GET")
      ) {
        return jsonResponse([]);
      }
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

  it("home → Start from template → template pick → editor; typing updates the live preview", async () => {
    const user = userEvent.setup();
    stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });

    // The landing is the resume list with the action cards.
    expect(screen.getByRole("heading", { name: "Resume Builder" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /New Resume/ }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole("button", { name: /Start from job description/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Start from template/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Import it/ })).toBeInTheDocument();
    expect(screen.getByText("Recent Resumes")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Start from template/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    // Names appear both as style filters and as gallery cards.
    expect(screen.getAllByText("Modern").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Classic").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Minimal").length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "Select Modern template" }));
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
    expect(screen.getByRole("button", { name: "Export PDF" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download DOCX" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save to profile" })).toBeInTheDocument();
  });

  it("home → Import resume → upload → parse → templates with the parsed state", async () => {
    stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });

    // Import opens the entry step with the upload already shown.
    await userEvent.setup().click(screen.getByRole("button", { name: /Import it/ }));
    const input = screen.getByTestId("builder-file-input") as HTMLInputElement;
    const file = new File(["%PDF"], "cv.pdf", { type: "application/pdf" });
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Select Classic template" }));
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
    await userEvent.setup().click(screen.getByRole("button", { name: /Import it/ }));
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

    await user.click(screen.getByRole("button", { name: /Start from template/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Select Modern template" }));
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Improve summary with AI" }),
      ).toBeInTheDocument(),
    );

    // "Improve with AI" also expands the collapsed Summary section, so the
    // Co-Pilot panel (inside the section form) becomes visible.
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

    await user.click(screen.getByRole("button", { name: /Start from template/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Select Modern template" }));
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Improve summary with AI" }),
      ).toBeInTheDocument(),
    );
    // "Improve with AI" also expands the collapsed Summary section, so the
    // Co-Pilot panel (inside the section form) becomes visible.
    await user.click(screen.getByRole("button", { name: "Improve summary with AI" }));
    await waitFor(() =>
      expect(screen.getByTestId("copilot-panel")).toBeInTheDocument(),
    );
    await waitFor(() =>
      expect(within(screen.getByTestId("copilot-panel")).getByRole("alert")).toHaveTextContent(
        "Co-Pilot unavailable",
      ),
    );
    // The page is still usable — the editor is intact (Summary is the
    // section that was just expanded by "Improve with AI").
    expect(screen.getByLabelText("Summary")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Export PDF" })).toBeInTheDocument();
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

    await user.click(screen.getByRole("button", { name: /Start from template/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Select Modern template" }));
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Export PDF" })).toBeInTheDocument(),
    );

    await user.click(screen.getByRole("button", { name: "Export PDF" }));
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

    await user.click(screen.getByRole("button", { name: /Start from template/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Select Minimal template" }));
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

  it("starting fresh resets design and section visibility (N-17)", async () => {
    // Customise doc-1 (serif font + hide Experience), go home, then
    // start fresh from a template: the new document must have the
    // default design and all sections visible — the previous document's
    // preview choices must not leak into it.
    const user = userEvent.setup();
    window.localStorage.setItem(BUILDER_TOUR_SEEN_KEY, "1");
    const fetchMock = mockFetch((url, init) => {
      if (url === "/api/resume-builder/templates") return jsonResponse(TEMPLATES);
      if (
        url === "/api/resume-builder/documents" &&
        (!init?.method || init.method === "GET")
      ) {
        return jsonResponse([
          { ...PARSED_DOC, id: "doc-1", templateId: "modern", title: "Modern resume" },
        ]);
      }
      if (url === "/api/resume-builder/documents" && init?.method === "POST") {
        return jsonResponse({ ...PARSED_DOC, id: "doc-2", templateId: "modern" }, 201);
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<ResumeBuilderPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByText("Modern resume")).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId("builder-doc-title"));
    await waitFor(() =>
      expect(screen.getByLabelText("Full name")).toBeInTheDocument(),
    );

    await user.click(screen.getByRole("tab", { name: "Designer" }));
    await user.selectOptions(screen.getByLabelText("Font"), "serif");
    await user.click(screen.getByRole("tab", { name: "Sections" }));
    await user.click(screen.getByLabelText("Show Experience section"));
    expect(
      (screen.getByLabelText("Show Experience section") as HTMLInputElement).checked,
    ).toBe(false);

    await user.click(screen.getByRole("button", { name: "Back to Resumes" }));
    await waitFor(() =>
      expect(screen.getByText("Recent Resumes")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: /Start from template/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Select Modern template" }));
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    // Mode is session state, not per-document: it is still "designer"
    // from the customisation above, so switch back to Content first.
    await user.click(screen.getByRole("tab", { name: "Content" }));
    await waitFor(() =>
      expect(screen.getByLabelText("Full name")).toBeInTheDocument(),
    );

    await user.click(screen.getByRole("tab", { name: "Designer" }));
    expect((screen.getByLabelText("Font") as HTMLSelectElement).value).toBe("sans");
    await user.click(screen.getByRole("tab", { name: "Sections" }));
    expect(
      (screen.getByLabelText("Show Experience section") as HTMLInputElement).checked,
    ).toBe(true);
  });
});

describe("design settings (Designer mode)", () => {
  it("designToCss overrides font, line-height, accent, alignment, location alignment, skills layout and margins", () => {
    const css = designToCss({
      ...DEFAULT_DESIGN,
      font: "serif",
      lineHeight: 1.8,
      accent: "#0f766e",
      headerAlign: "center",
      locationAlign: "right",
      skillsLayout: "list",
      margins: { top: 48, right: 48, bottom: 48, left: 48 },
    });
    expect(css).toContain("Georgia");
    expect(css).toContain("line-height: 1.8");
    expect(css).toContain("#0f766e");
    expect(css).toContain("text-align: center");
    expect(css).toContain("padding: 48px 48px 48px 48px");
    expect(css).toContain("justify-content: space-between");
    expect(css).toContain(".skills-list");
  });

  it("designToCss left location alignment keeps dates inline", () => {
    const css = designToCss({ ...DEFAULT_DESIGN, locationAlign: "left" });
    expect(css).toContain("display: block");
    expect(css).toContain(".job-dates { display: inline");
  });

  it("designToCss columns skills layout emits column CSS", () => {
    const css = designToCss({ ...DEFAULT_DESIGN, skillsLayout: "columns" });
    expect(css).toContain("column-count: 3");
    expect(css).toContain(".skills-cols");
  });

  it("applyDesignOverrides injects one override style block", () => {
    const html = renderTemplateHtml(BUILTIN_TEMPLATES[0], sampleBuilderState(), {
      design: { ...DEFAULT_DESIGN, accent: "#0f766e", lineHeight: 1.75 },
    });
    expect(html).toContain("data-design");
    expect(html).toContain("#0f766e");
    expect(html).toContain("line-height: 1.75");
    expect(html).toContain("Alex Morgan");
  });

  it("renderTemplateHtml honours the date separator and hidden sections", () => {
    const state = sampleBuilderState();
    const withTo = renderTemplateHtml(BUILTIN_TEMPLATES[0], state, {
      design: { ...DEFAULT_DESIGN, dateSeparator: " to " },
    });
    expect(withTo).toContain("2021 to Present");
    const hidden = renderTemplateHtml(BUILTIN_TEMPLATES[0], state, {
      hideSections: ["skills", "education"],
    });
    expect(hidden).not.toContain("Java, Spring Boot");
    expect(hidden).not.toContain("George Mason University");
    expect(hidden).toContain("Alex Morgan");
    // B-1: a hidden section's heading goes with its content — no orphan <h2>.
    expect(hidden).not.toContain("<h2>Skills</h2>");
    expect(hidden).not.toContain("<h2>Education</h2>");
    expect(hidden).toContain("<h2>Experience</h2>");
    expect(hidden).toContain("<h2>Summary</h2>");
    // Without options the render is unchanged.
    const plain = renderTemplateHtml(BUILTIN_TEMPLATES[0], state);
    expect(plain).toContain("2021 – Present");
    expect(plain).toContain("Java, Spring Boot");
  });

  it("templateStyleOf / templateLayoutOf derive real filter values", () => {
    expect(templateStyleOf(BUILTIN_TEMPLATES[0])).toBe("Modern");
    expect(templateStyleOf(BUILTIN_TEMPLATES[1])).toBe("Classic");
    expect(templateStyleOf(BUILTIN_TEMPLATES[2])).toBe("Minimal");
    for (const template of BUILTIN_TEMPLATES) {
      expect(templateLayoutOf(template)).toBe("Single column");
    }
  });

  it("templateStyleOf falls back to Other; templateLayoutOf detects two columns (arch #15)", () => {
    expect(
      templateStyleOf({ id: "x1", name: "Bespoke", description: "", html: "" }),
    ).toBe("Other");
    const twoCol = (html: string): BuilderTemplate => ({
      id: "x2",
      name: "TwoCol",
      description: "",
      html,
    });
    expect(
      templateLayoutOf(twoCol("<div style='grid-template-columns: 1fr 2fr'></div>")),
    ).toBe("Two column");
    expect(templateLayoutOf(twoCol("<div class='sidebar'></div>"))).toBe("Two column");
    expect(templateLayoutOf(twoCol("<div style='columns: 2'></div>"))).toBe("Two column");
  });
});

describe("analyzeResume", () => {
  it("scores a complete resume high and counts measurable bullets", () => {
    const analysis = analyzeResume(sampleBuilderState());
    expect(analysis.score).toBeGreaterThanOrEqual(80);
    const measurable = analysis.checks.find((c) => c.id === "measurable");
    expect(measurable?.detail).toBe("2 of 2 bullets include a number or metric");
    expect(analysis.weakest).toBeNull();
  });

  it("scores an empty resume 0 and names the weakest check", () => {
    const analysis = analyzeResume(emptyBuilderState());
    expect(analysis.score).toBe(0);
    expect(analysis.checks.length).toBeGreaterThan(0);
    expect(analysis.checks.every((c) => !c.passed)).toBe(true);
    expect(analysis.weakest?.id).toBe("contact");
  });

  it("counts keyword coverage from a pasted job description", () => {
    const jd = "We need a Java engineer with Spring Boot and Kafka experience, plus Azure and PostgreSQL.";
    const analysis = analyzeResume(sampleBuilderState(), jd);
    const keywords = analysis.checks.find((c) => c.id === "keywords");
    expect(keywords).toBeDefined();
    expect(keywords?.passed).toBe(true);
    expect(keywords?.detail).toContain("job keywords appear");
    expect(keywordsFromJobDescription(jd)).toContain("spring");
  });

  it("omits the keyword check when no job description is pasted", () => {
    const analysis = analyzeResume(sampleBuilderState());
    expect(analysis.checks.find((c) => c.id === "keywords")).toBeUndefined();
  });
});

describe("DesignerPanel", () => {
  function renderPanel() {
    const onDesignChange = vi.fn();
    const onToggleSection = vi.fn();
    render(
      <DesignerPanel
        design={DEFAULT_DESIGN}
        onDesignChange={onDesignChange}
        visibleSections={{
          contact: true,
          summary: true,
          experience: true,
          education: true,
          skills: true,
        }}
        onToggleSection={onToggleSection}
        onResetDesign={() => {}}
      />,
    );
    return { onDesignChange, onToggleSection };
  }

  it("changing the font, line-height and accent calls onDesignChange", async () => {
    const user = userEvent.setup();
    const { onDesignChange } = renderPanel();
    await user.selectOptions(screen.getByLabelText("Font"), "serif");
    expect(onDesignChange).toHaveBeenCalledWith(
      expect.objectContaining({ font: "serif" }),
    );
    await user.click(screen.getByRole("button", { name: "Accent color Teal" }));
    expect(onDesignChange).toHaveBeenCalledWith(
      expect.objectContaining({ accent: "#0f766e" }),
    );
    const slider = screen.getByLabelText("Line height") as HTMLInputElement;
    fireEvent.change(slider, { target: { value: "180" } });
    expect(onDesignChange).toHaveBeenCalledWith(
      expect.objectContaining({ lineHeight: 1.8 }),
    );
  });

  it("sections tab toggles a section and settings tab steps a margin", async () => {
    const user = userEvent.setup();
    const { onDesignChange, onToggleSection } = renderPanel();
    await user.click(screen.getByRole("tab", { name: "Sections" }));
    await user.click(screen.getByLabelText("Show Skills section"));
    expect(onToggleSection).toHaveBeenCalledWith("skills");
    await user.click(screen.getByRole("tab", { name: "Settings" }));
    await user.click(screen.getByRole("button", { name: "Increase Top margin" }));
    expect(onDesignChange).toHaveBeenCalledWith(
      expect.objectContaining({ margins: expect.objectContaining({ top: 36 }) }),
    );
  });
});

describe("TemplateLibraryModal", () => {
  function renderModal() {
    const onSelect = vi.fn();
    const onUse = vi.fn();
    const onClose = vi.fn();
    render(
      <TemplateLibraryModal
        templates={TEMPLATES}
        state={sampleBuilderState()}
        selectedId={null}
        onSelect={onSelect}
        onUse={onUse}
        onClose={onClose}
      />,
    );
    return { onSelect, onUse, onClose };
  }

  it("filters by search and style, and the commit is disabled until a template is picked", async () => {
    const user = userEvent.setup();
    const { onSelect, onUse } = renderModal();
    expect(screen.getByRole("button", { name: "Use Template" })).toBeDisabled();
    await user.type(screen.getByLabelText("Search templates"), "classic");
    expect(
      screen.getByRole("button", { name: "Select Classic template" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Select Modern template" }),
    ).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText("Search templates"));
    await user.click(screen.getByRole("button", { name: "Minimal" }));
    expect(
      screen.queryByRole("button", { name: "Select Modern template" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Select Minimal template" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Select Minimal template" }));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: "minimal" }));
    // Commit stays disabled until the parent records the selection.
    expect(screen.getByRole("button", { name: "Use Template" })).toBeDisabled();
    expect(onUse).not.toHaveBeenCalled();
  });

  it("shows the selected template's live preview and commits with its name", async () => {
    const user = userEvent.setup();
    const onUse = vi.fn();
    render(
      <TemplateLibraryModal
        templates={TEMPLATES}
        state={sampleBuilderState()}
        selectedId="classic"
        onSelect={() => {}}
        onUse={onUse}
        onClose={() => {}}
      />,
    );
    expect(screen.getByText("Preview — Classic")).toBeInTheDocument();
    const commit = screen.getByRole("button", { name: "Use Classic" });
    expect(commit).not.toBeDisabled();
    await user.click(commit);
    expect(onUse).toHaveBeenCalledTimes(1);
  });
});

describe("AnalyzerPanel", () => {
  it("renders the score gauge, countable rows and a tip for the weakest check", () => {
    const state = emptyBuilderState();
    render(<AnalyzerPanel state={state} targetJd="" onTargetJdChange={() => {}} />);
    expect(screen.getByRole("img", { name: /Resume score 0 out of 100/ })).toBeInTheDocument();
    expect(screen.getByText("Contact details complete")).toBeInTheDocument();
    expect(screen.getByText("Add your name and email")).toBeInTheDocument();
    expect(screen.getByText(/Tip — fix this first/)).toBeInTheDocument();
    expect(screen.getByLabelText("Target job description")).toBeInTheDocument();
  });

  it("shows the keyword row once a job description is pasted", () => {
    const jd = "Java Spring Boot Kafka Azure PostgreSQL engineer";
    render(
      <AnalyzerPanel state={sampleBuilderState()} targetJd={jd} onTargetJdChange={() => {}} />,
    );
    expect(screen.getByText("Job keywords covered")).toBeInTheDocument();
    expect(screen.getByText(/job keywords appear/)).toBeInTheDocument();
  });
});

describe("matchJobDescription", () => {
  const state = sampleBuilderState();

  it("scores 100 when every job keyword appears in the resume", () => {
    const match = matchJobDescription(
      state,
      "Java Spring Boot Kafka Azure PostgreSQL engineer",
    );
    expect(match.score).toBe(100);
    expect(match.totalKeywords).toBe(7);
    expect(match.matchedKeywords).toBe(7);
    expect(match.missingKeywords).toEqual([]);
    expect(match.skills.every((row) => row.status === "matched")).toBe(true);
  });

  it("scores from keyword coverage and labels Hard / Soft + matched / missing", () => {
    const match = matchJobDescription(
      state,
      "Java Spring Boot Rust Kubernetes leadership",
    );
    // boot, java, spring hit; kubernetes, leadership, rust miss → 3/6 = 50.
    expect(match.score).toBe(50);
    expect(match.totalKeywords).toBe(6);
    expect(match.matchedKeywords).toBe(3);
    expect(match.missingKeywords).toEqual([
      "kubernetes",
      "leadership",
      "rust",
    ]);
    const byKeyword = new Map(match.skills.map((row) => [row.keyword, row]));
    expect(byKeyword.get("java")).toEqual({ keyword: "java", type: "Hard Skill", status: "matched" });
    expect(byKeyword.get("rust")).toEqual({ keyword: "rust", type: "Hard Skill", status: "missing" });
    expect(byKeyword.get("leadership")).toEqual({ keyword: "leadership", type: "Soft Skill", status: "missing" });
    // Matched rows first, then alphabetical within each group.
    expect(match.skills.map((row) => row.keyword)).toEqual([
      "boot",
      "java",
      "spring",
      "kubernetes",
      "leadership",
      "rust",
    ]);
  });

  it("returns a null score when the job description has no keywords", () => {
    const match = matchJobDescription(state, "We are");
    expect(match.score).toBeNull();
    expect(match.totalKeywords).toBe(0);
    expect(match.skills).toEqual([]);
  });

  it("caps the skills table at 12 rows", () => {
    const match = matchJobDescription(
      state,
      "alpha beta gamma delta epsilon zeta theta iota kappa lambda omicron sigma upsilon",
    );
    expect(match.totalKeywords).toBe(13);
    expect(match.skills).toHaveLength(12);
  });
});

describe("builder documents list helpers", () => {
  const docs = normalizeBuilderDocuments([
    {
      id: "doc-1",
      title: "Modern resume",
      templateId: "modern",
      updatedAt: "2026-11-17T10:00:00Z",
      createdAt: "2026-11-10T10:00:00Z",
      state: sampleBuilderState(),
    },
    {
      id: "doc-2",
      title: "Classic resume",
      templateId: "classic",
      updatedAt: "2026-11-16T10:00:00Z",
      createdAt: "2026-11-09T10:00:00Z",
      state: sampleBuilderState(),
    },
    {
      id: "doc-3",
      title: "Data Analyst",
      templateId: "minimal",
      updatedAt: "2026-11-15T10:00:00Z",
      createdAt: "2026-11-08T10:00:00Z",
      state: sampleBuilderState(),
    },
  ]);

  it("normalises a bare array and a {documents: [...]} envelope, and tolerates garbage", () => {
    expect(normalizeBuilderDocuments(docs)).toHaveLength(3);
    expect(
      normalizeBuilderDocuments({ documents: docs.slice(0, 1) }),
    ).toHaveLength(1);
    expect(normalizeBuilderDocuments(null)).toEqual([]);
    expect(normalizeBuilderDocuments({})).toEqual([]);
  });

  it("filters by title, case-insensitively; an empty query returns everything", () => {
    expect(filterBuilderDocuments(docs, "modern")).toHaveLength(1);
    expect(filterBuilderDocuments(docs, "RESUME")).toHaveLength(2);
    expect(filterBuilderDocuments(docs, "  ")).toHaveLength(3);
    expect(filterBuilderDocuments(docs, "nope")).toHaveLength(0);
  });

  it("sorts most-recent-first by default and A–Z by title", () => {
    const shuffled = [docs[2], docs[0], docs[1]];
    expect(sortBuilderDocuments(shuffled, "recent").map((d) => d.id)).toEqual([
      "doc-1",
      "doc-2",
      "doc-3",
    ]);
    expect(sortBuilderDocuments(shuffled, "title").map((d) => d.title)).toEqual([
      "Classic resume",
      "Data Analyst",
      "Modern resume",
    ]);
  });
});

describe("ResumeHome", () => {
  const docs = normalizeBuilderDocuments([
    {
      id: "doc-1",
      title: "Modern resume",
      templateId: "modern",
      updatedAt: "2026-11-17T10:00:00Z",
      createdAt: "2026-11-10T10:00:00Z",
      state: sampleBuilderState(),
    },
    {
      id: "doc-2",
      title: "Classic resume",
      templateId: "classic",
      updatedAt: "2026-11-16T10:00:00Z",
      createdAt: "2026-11-09T10:00:00Z",
      state: sampleBuilderState(),
    },
    {
      id: "doc-3",
      title: "Data Analyst",
      templateId: "minimal",
      updatedAt: "2026-11-15T10:00:00Z",
      createdAt: "2026-11-08T10:00:00Z",
      state: sampleBuilderState(),
    },
  ]);

  function renderHome(overrides: Partial<Parameters<typeof ResumeHome>[0]> = {}) {
    const handlers = {
      onNew: vi.fn(),
      onFromJobDescription: vi.fn(),
      onFromTemplate: vi.fn(),
      onImport: vi.fn(),
      onOpen: vi.fn(),
      onMatchJob: vi.fn(),
      ...overrides,
    };
    render(
      <ResumeHome documents={docs} loading={false} error={null} {...handlers} />,
    );
    return handlers;
  }

  const titles = () =>
    screen.getAllByTestId("builder-doc-title").map((el) => el.textContent);

  it("renders the action cards, the import link and Recent Resumes — and no cover-letter card", () => {
    renderHome();
    expect(screen.getByRole("button", { name: /New Resume/ })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Start from job description/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Start from template/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Import it/ })).toBeInTheDocument();
    expect(screen.getByText("Recent Resumes")).toBeInTheDocument();
    expect(screen.queryByText(/Cover Letter/)).not.toBeInTheDocument();
    // Most recent first by default.
    expect(titles()).toEqual(["Modern resume", "Classic resume", "Data Analyst"]);
    expect(screen.getByText("Edited: 11/17/2026")).toBeInTheDocument();
  });

  it("search filters the list and sort reorders it by title", async () => {
    const user = userEvent.setup();
    renderHome();
    await user.type(screen.getByLabelText("Search resumes"), "classic");
    expect(titles()).toEqual(["Classic resume"]);
    await user.clear(screen.getByLabelText("Search resumes"));
    await user.selectOptions(screen.getByLabelText("Sort resumes"), "title");
    expect(titles()).toEqual(["Classic resume", "Data Analyst", "Modern resume"]);
  });

  it("opening a resume and Match a job call the right handlers", async () => {
    const user = userEvent.setup();
    const handlers = renderHome();
    await user.click(screen.getAllByRole("button", { name: "Match a job" })[0]);
    expect(handlers.onMatchJob).toHaveBeenCalledWith(
      expect.objectContaining({ id: "doc-1" }),
    );
    await user.click(screen.getAllByTestId("builder-doc-title")[1]);
    expect(handlers.onOpen).toHaveBeenCalledWith(
      expect.objectContaining({ id: "doc-2" }),
    );
  });

  it("shows the empty state with a New Resume button when there are no resumes", () => {
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
    expect(screen.getByText("No resumes yet")).toBeInTheDocument();
    expect(screen.queryByTestId("builder-doc-title")).not.toBeInTheDocument();
  });
});

describe("QuickTourModal", () => {
  it("renders nothing when closed", () => {
    const { container } = render(<QuickTourModal open={false} onClose={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the welcome card, then steps through, and Skip / Done close it", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<QuickTourModal open onClose={onClose} />);
    expect(screen.getByText("Ready to Improve Your Resume!")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start Quick Tour" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Start Quick Tour" }));
    expect(screen.getByText("Step 1 of 4")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Step 2 of 4")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Skip the Tour" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe("JobMatcherPanel", () => {
  const JOBS_PAGE = {
    content: [
      {
        id: "job-1",
        title: "Senior Java Engineer",
        company: "Acme",
        location: "Reston, VA",
        workType: "FULL_TIME",
        salaryMin: 120000,
        salaryMax: 160000,
        currency: "USD",
        sponsorsH1b: true,
        skills: ["Java", "Spring Boot"],
        summary: "Build Java services.",
        role: "ENGINEERING",
        postedAt: "2026-11-17T00:00:00Z",
        source: "SEED",
        sourceUrl: null,
      },
    ],
    page: 0,
    size: 5,
    totalElements: 1,
    totalPages: 1,
  };

  function renderMatcher(targetJd = "", onTargetJdChange = vi.fn()) {
    vi.stubGlobal(
      "fetch",
      mockFetch((url) => {
        if (url.startsWith("/api/jobs")) return jsonResponse(JOBS_PAGE);
        return jsonResponse({ message: "not found" }, 404);
      }),
    );
    render(
      <JobMatcherPanel
        state={sampleBuilderState()}
        targetJd={targetJd}
        onTargetJdChange={onTargetJdChange}
      />,
      { wrapper },
    );
    return onTargetJdChange;
  }

  it("prompts for a job description when there is no score yet", () => {
    renderMatcher();
    expect(
      screen.getByText(/Paste a job description above/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: /Match score/ })).not.toBeInTheDocument();
  });

  it("shows the gauge and the skills table once a job description is pasted", () => {
    renderMatcher("Java Spring Boot Kafka Azure PostgreSQL engineer");
    expect(
      screen.getByRole("img", { name: "Match score 100 percent" }),
    ).toBeInTheDocument();
    expect(screen.getByText("7 of 7 job keywords appear in your resume.")).toBeInTheDocument();
    expect(screen.getByText("Skills")).toBeInTheDocument();
    // A matched hard skill and its status are shown, not invented.
    expect(screen.getByText("java")).toBeInTheDocument();
    // "boot" is Hard now: it is a word of the multi-word tech term "spring boot".
    expect(screen.getAllByText("Hard Skill")).toHaveLength(6);
    expect(screen.getAllByText("Soft Skill")).toHaveLength(1);
    expect(screen.getAllByText("Matched")).toHaveLength(7);
  });

  it("shows the missing rows and the missing-keywords hint for a partial match", () => {
    renderMatcher("Java Spring Boot Rust Kubernetes leadership");
    expect(
      screen.getByRole("img", { name: "Match score 50 percent" }),
    ).toBeInTheDocument();
    expect(screen.getByText("3 of 6 job keywords appear in your resume.")).toBeInTheDocument();
    expect(screen.getByText("leadership")).toBeInTheDocument();
    expect(screen.getAllByText("Soft Skill")).toHaveLength(1);
    expect(screen.getAllByText("Hard Skill")).toHaveLength(5);
    expect(screen.getAllByText("Matched")).toHaveLength(3);
    expect(screen.getAllByText("Missing")).toHaveLength(3);
    expect(screen.getByText(/Missing keywords to add/)).toBeInTheDocument();
  });

  it("picking a job from search fills the job description", async () => {
    const user = userEvent.setup();
    const onTargetJdChange = renderMatcher();
    await user.type(screen.getByLabelText("Search titles or companies"), "Java");
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /Senior Java Engineer/ }),
      ).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: /Senior Java Engineer/ }));
    expect(onTargetJdChange).toHaveBeenCalledWith(
      "Senior Java Engineer at Acme\nBuild Java services.\nSkills: Java, Spring Boot",
    );
  });
});

describe("content section toggles", () => {
  function stubBuilderPage() {
    window.localStorage.setItem(BUILDER_TOUR_SEEN_KEY, "1");
    vi.stubGlobal(
      "fetch",
      mockFetch((url, init) => {
        if (url === "/api/resume-builder/templates") return jsonResponse(TEMPLATES);
        if (
          url === "/api/resume-builder/documents" &&
          (!init?.method || init.method === "GET")
        ) {
          return jsonResponse([]);
        }
        if (url === "/api/resume-builder/documents" && init?.method === "POST") {
          return jsonResponse({ ...PARSED_DOC, id: "doc-1", templateId: "modern" }, 201);
        }
        return jsonResponse({ message: "not found" }, 404);
      }),
    );
  }

  it("add-entry opens the section, and unchecking a section hides it from the preview", async () => {
    const user = userEvent.setup();
    stubBuilderPage();
    render(<ResumeBuilderPage />, { wrapper });

    await user.click(screen.getByRole("button", { name: /Start from template/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Select Modern template" }));
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Add experience" })).toBeInTheDocument(),
    );

    // The header + adds an entry and opens the (collapsed) section.
    await user.click(screen.getByRole("button", { name: "Add experience" }));
    expect(screen.getByLabelText("Job title")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Job title"), "Staff Engineer");
    const preview = screen.getByTestId("resume-preview") as HTMLIFrameElement;
    expect(preview.getAttribute("srcDoc")).toContain("Staff Engineer");

    // Uncheck the section: it is muted and hidden from the preview —
    // content AND heading (B-1: no orphan <h2> left behind).
    await user.click(screen.getByLabelText("Include Experience section"));
    expect(preview.getAttribute("srcDoc")).not.toContain("Staff Engineer");
    expect(preview.getAttribute("srcDoc")).not.toContain("<h2>Experience</h2>");
    // Re-check it: the entry and its heading are back in the preview.
    await user.click(screen.getByLabelText("Include Experience section"));
    expect(preview.getAttribute("srcDoc")).toContain("Staff Engineer");
    expect(preview.getAttribute("srcDoc")).toContain("<h2>Experience</h2>");
  });
});

describe("containsKeyword (B-3 regression)", () => {
  it("matches whole tokens, not substrings", () => {
    expect(containsKeyword("JavaScript and React", "java")).toBe(false);
    expect(containsKeyword("Java developer", "java")).toBe(true);
    expect(containsKeyword("reaction time", "react")).toBe(false);
    expect(containsKeyword("React engineer", "react")).toBe(true);
    expect(containsKeyword("trust me", "rust")).toBe(false);
    expect(containsKeyword("Rust engineer", "rust")).toBe(true);
    expect(containsKeyword("going to the store", "go")).toBe(false);
    expect(containsKeyword("Go developer", "go")).toBe(true);
  });

  it("matches keywords ending in a non-word character (c#, c++)", () => {
    expect(containsKeyword("C# developer", "c#")).toBe(true);
    expect(containsKeyword("C++ and C#", "c++")).toBe(true);
    expect(containsKeyword("C#/.NET stack", "c#")).toBe(true);
    expect(containsKeyword("", "java")).toBe(false);
    expect(containsKeyword("Java developer", "")).toBe(false);
  });
});

describe("keywordsFromJobDescription short tech terms (B-2 regression)", () => {
  it("keeps aws, sql, api and git instead of dropping them", () => {
    expect(
      keywordsFromJobDescription(
        "We need AWS, SQL, API design and Git experience for this role.",
      ),
    ).toEqual(["api", "aws", "design", "git", "need", "sql"]);
  });

  it("keeps c# and .net (as net) as keywords", () => {
    expect(keywordsFromJobDescription("C# and .NET developer")).toEqual([
      "c#",
      "developer",
      "net",
    ]);
  });

  it("keeps single-letter languages c and r, and node.js (arch #6)", () => {
    expect(keywordsFromJobDescription("C developer with R and Node.js")).toEqual([
      "c",
      "developer",
      "node.js",
      "r",
    ]);
  });

  it("keyword c does not match the C in C# / C++ (N-16)", () => {
    // "+" and "#" are non-word characters, so the usual (?!\w) boundary
    // passes after "C" in "C++"/"C#" — the guard excludes them.
    expect(containsKeyword("C++ programming", "c")).toBe(false);
    expect(containsKeyword("C# developer", "c")).toBe(false);
    expect(containsKeyword("C programming", "c")).toBe(true);
    // But c# and c++ still match as exact keywords.
    expect(containsKeyword("C++ developer", "c++")).toBe(true);
    expect(containsKeyword("C# developer", "c#")).toBe(true);
  });

  it("single-letter keywords do not match CS or R&D (N-18, N-19)", () => {
    // "c" + "s?" would match the token "CS" (Computer Science) — a
    // resume with "B.S. in CS" has no C. Single-character forms take
    // no plural.
    expect(containsKeyword("CS degree from State", "c")).toBe(false);
    expect(containsKeyword("B.S. in CS", "c")).toBe(false);
    expect(containsKeyword("C programming", "c")).toBe(true);
    // "&" is a non-word boundary, so "r" would match the "R" in "R&D".
    expect(containsKeyword("R&D engineer", "r")).toBe(false);
    expect(containsKeyword("R&D team", "r")).toBe(false);
    expect(containsKeyword("R programming", "r")).toBe(true);
    // But a genuine C mention still counts ("C/C++" contains C).
    expect(containsKeyword("C/C++ developer", "c")).toBe(true);
  });

  it("drops singular/plural junk words symmetric with the stopwords (arch #5)", () => {
    // "years"/"day"/"role" were already stopwords; their other forms
    // ("year"/"days"/"roles") and "jobs" must not become keywords either.
    const keywords = keywordsFromJobDescription(
      "1 year of experience, 2 days a week, senior roles, many jobs available.",
    );
    expect(keywords).not.toContain("year");
    expect(keywords).not.toContain("days");
    expect(keywords).not.toContain("roles");
    expect(keywords).not.toContain("jobs");
    expect(keywords).not.toContain("candidates");
    expect(keywords).not.toContain("teams");
  });
});

describe("containsKeyword SQL-family compounds (arch #7)", () => {
  it("keyword sql matches MySQL/PostgreSQL/NoSQL/SQLite, not the reverse", () => {
    expect(containsKeyword("MySQL and PostgreSQL", "sql")).toBe(true);
    expect(containsKeyword("SQLite database", "sql")).toBe(true);
    expect(containsKeyword("NoSQL store", "sql")).toBe(true);
    expect(containsKeyword("MSSQL Server", "sql")).toBe(true);
    // The reverse does not hold: a JD asking for MySQL specifically
    // wants MySQL, so a generic "sql" resume does not match it.
    expect(containsKeyword("SQL queries", "mysql")).toBe(false);
    expect(containsKeyword("SQL queries", "postgresql")).toBe(false);
  });
});

describe("matchJobDescription whole-token matching (B-3 regression)", () => {
  it("does not match 'java' against a JavaScript-only resume", () => {
    const state: ResumeBuilderState = {
      ...sampleBuilderState(),
      skills: ["JavaScript", "React"],
      summary: "Frontend engineer working in JavaScript and React.",
      experience: [],
    };
    const match = matchJobDescription(
      state,
      "Looking for a Java developer with Spring experience.",
    );
    // Keywords are developer, java, spring — none of them is in the resume.
    expect(match.totalKeywords).toBe(3);
    expect(match.matchedKeywords).toBe(0);
    expect(match.score).toBe(0);
    expect(match.skills.find((row) => row.keyword === "java")?.status).toBe(
      "missing",
    );
  });

  it("still matches real whole-word keywords (Spring Boot)", () => {
    const match = matchJobDescription(sampleBuilderState(), "Spring Boot developer");
    expect(match.skills.find((row) => row.keyword === "spring")?.status).toBe(
      "matched",
    );
    expect(match.skills.find((row) => row.keyword === "boot")?.status).toBe(
      "matched",
    );
  });

  it("classifies multi-word tech terms by their words (M-4)", () => {
    const match = matchJobDescription(
      sampleBuilderState(),
      "Machine learning and Power BI experience",
    );
    // "machine" and "learning" come from the multi-word term "machine learning";
    // "power" comes from "power bi" — all Hard, not Soft.
    expect(match.skills.find((row) => row.keyword === "machine")?.type).toBe(
      "Hard Skill",
    );
    expect(match.skills.find((row) => row.keyword === "learning")?.type).toBe(
      "Hard Skill",
    );
    expect(match.skills.find((row) => row.keyword === "power")?.type).toBe(
      "Hard Skill",
    );
  });
});

describe("BuilderMenu", () => {
  function renderMenu(duplicateDisabled = false) {
    const handlers = {
      onImport: vi.fn(),
      onDuplicate: vi.fn(),
      onQuickTour: vi.fn(),
      onBackToResumes: vi.fn(),
    };
    render(<BuilderMenu duplicateDisabled={duplicateDisabled} {...handlers} />);
    return handlers;
  }

  it("fires each item's handler and closes the menu", async () => {
    const user = userEvent.setup();
    const handlers = renderMenu();
    await user.click(screen.getByRole("button", { name: "Menu" }));
    await user.click(screen.getByRole("menuitem", { name: "Import resume" }));
    expect(handlers.onImport).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Menu" }));
    await user.click(screen.getByRole("menuitem", { name: "Duplicate this resume" }));
    expect(handlers.onDuplicate).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "Menu" }));
    await user.click(screen.getByRole("menuitem", { name: "Quick Tour" }));
    expect(handlers.onQuickTour).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "Menu" }));
    await user.click(screen.getByRole("menuitem", { name: "Back to All Resumes" }));
    expect(handlers.onBackToResumes).toHaveBeenCalledTimes(1);
  });

  it("disables Duplicate when there is nothing to copy and closes on Escape", async () => {
    const user = userEvent.setup();
    const handlers = renderMenu(true);
    await user.click(screen.getByRole("button", { name: "Menu" }));
    const duplicate = screen.getByRole("menuitem", { name: "Duplicate this resume" });
    expect(duplicate).toBeDisabled();
    await user.click(duplicate);
    expect(handlers.onDuplicate).not.toHaveBeenCalled();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("has no Delete or LinkedIn item", async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Menu" }));
    expect(screen.queryByRole("menuitem", { name: /Delete/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /LinkedIn/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole("menuitem")).toHaveLength(4);
  });
});

describe("JobMatcherPanel jobs fetching (M-1, M-2)", () => {
  const JOBS_PAGE = {
    content: [
      {
        id: "job-1",
        title: "Senior Java Engineer",
        company: "Acme",
        location: "Reston, VA",
        workType: "FULL_TIME",
        salaryMin: 120000,
        salaryMax: 160000,
        currency: "USD",
        sponsorsH1b: true,
        skills: ["Java", "Spring Boot"],
        summary: "Build Java services.",
        role: "ENGINEERING",
        postedAt: "2026-11-17T00:00:00Z",
        source: "SEED",
        sourceUrl: null,
      },
    ],
    page: 0,
    size: 5,
    totalElements: 1,
    totalPages: 1,
  };

  function stubJobs() {
    const fetchMock = mockFetch((url) => {
      if (url.startsWith("/api/jobs")) return jsonResponse(JOBS_PAGE);
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
  }

  it("does not fetch jobs until the user types a query (M-1)", async () => {
    const fetchMock = stubJobs();
    render(
      <JobMatcherPanel
        state={sampleBuilderState()}
        targetJd=""
        onTargetJdChange={() => {}}
      />,
      { wrapper },
    );
    // Let any mount effects settle.
    await waitFor(() => expect(screen.getByLabelText("Search titles or companies")).toBeInTheDocument());
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/jobs"),
      expect.anything(),
    );
  });

  it("debounces the search: one request for a whole typed query (M-2)", async () => {
    const user = userEvent.setup();
    const fetchMock = stubJobs();
    render(
      <JobMatcherPanel
        state={sampleBuilderState()}
        targetJd=""
        onTargetJdChange={() => {}}
      />,
      { wrapper },
    );
    await user.type(screen.getByLabelText("Search titles or companies"), "Java");
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /Senior Java Engineer/ }),
      ).toBeInTheDocument(),
    );
    const jobCalls = fetchMock.mock.calls.filter(([url]) =>
      (url as string).startsWith("/api/jobs"),
    );
    expect(jobCalls).toHaveLength(1);
    expect(jobCalls[0][0]).toContain("query=Java");
  });
});

describe("page-level minors (M-3, M-5, M-6)", () => {
  function stubPage() {
    window.localStorage.setItem(BUILDER_TOUR_SEEN_KEY, "1");
    const fetchMock = mockFetch((url, init) => {
      if (url === "/api/resume-builder/templates") return jsonResponse(TEMPLATES);
      if (
        url === "/api/resume-builder/documents" &&
        (!init?.method || init.method === "GET")
      ) {
        return jsonResponse([]);
      }
      if (url === "/api/resume-builder/documents" && init?.method === "POST") {
        return jsonResponse({ ...PARSED_DOC, id: "doc-1", templateId: "modern" }, 201);
      }
      if (url === "/api/resume-builder/documents/doc-1") {
        // ?documentId= load: a document with NO template (M-6).
        return jsonResponse({ ...PARSED_DOC, id: "doc-1", templateId: null });
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
  }

  it("New Resume opens the editor without creating a backend document (M-3)", async () => {
    const user = userEvent.setup();
    const fetchMock = stubPage();
    render(<ResumeBuilderPage />, { wrapper });
    await user.click(screen.getAllByRole("button", { name: /New Resume/ })[0]);
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Export PDF" })).toBeInTheDocument(),
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      "/api/resume-builder/documents",
      expect.objectContaining({ method: "POST" }),
    );
    expect(screen.getByText("Template: Modern")).toBeInTheDocument();
  });

  it("Duplicate is enabled when only a contact field (email) is filled (M-5)", async () => {
    const user = userEvent.setup();
    stubPage();
    render(<ResumeBuilderPage />, { wrapper });
    await user.click(screen.getByRole("button", { name: /Start from template/ }));
    await waitFor(() =>
      expect(screen.getByText("Choose a template")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Select Modern template" }));
    await user.click(screen.getByRole("button", { name: "Use Modern" }));
    await waitFor(() =>
      expect(screen.getByLabelText("Email")).toBeInTheDocument(),
    );
    await user.type(screen.getByLabelText("Email"), "alex@example.com");
    await user.click(screen.getByRole("button", { name: "Menu" }));
    expect(
      screen.getByRole("menuitem", { name: "Duplicate this resume" }),
    ).not.toBeDisabled();
  });

  it("?documentId= load with no template opens the editor with the Modern fallback (M-6)", async () => {
    stubPage();
    navMock.params = new URLSearchParams("documentId=doc-1");
    render(<ResumeBuilderPage />, { wrapper });
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Export PDF" })).toBeInTheDocument(),
    );
    expect(screen.queryByText("Choose a template")).not.toBeInTheDocument();
    expect(screen.getByText("Template: Modern")).toBeInTheDocument();
  });
});

describe("Quester re-test follow-ups (N-1..N-4)", () => {
  it("hides headings for wrapped and uppercase headings too (N-1)", () => {
    const custom: BuilderTemplate = {
      id: "custom",
      name: "Custom",
      description: "test template",
      html: `<!doctype html><html><body><h2>Summary</h2><p>{{summary}}</p><h2>EXPERIENCE</h2><section>{{experienceHtml}}</section><h2>Skills</h2><div class="skills-list">{{skillsHtml}}</div></body></html>`,
    };
    const hidden = renderTemplateHtml(custom, sampleBuilderState(), {
      hideSections: ["experience", "skills"],
    });
    expect(hidden).not.toContain("EXPERIENCE");
    expect(hidden).not.toContain("<h2>Skills</h2>");
    expect(hidden).not.toContain("skills-list");
    expect(hidden).not.toContain("<section>");
    expect(hidden).toContain("<h2>Summary</h2>");
    expect(hidden).toContain("Software engineer with 8 years");
  });

  it("keeps ci/cd as a keyword and drops slash junk (N-2)", () => {
    expect(keywordsFromJobDescription("CI/CD pipeline engineer")).toEqual([
      "ci/cd",
      "engineer",
      "pipeline",
    ]);
    expect(
      keywordsFromJobDescription("Remote and/or hybrid work"),
    ).not.toContain("and/or");
  });

  it("keeps 'bi' from Power BI via TECH_WORDS (N-3)", () => {
    const keywords = keywordsFromJobDescription(
      "Machine learning and Power BI experience",
    );
    expect(keywords).toContain("bi");
    expect(keywords).toContain("machine");
    expect(keywords).toContain("learning");
    expect(keywords).toContain("power");
  });

  it("matches plurals in both directions (N-4)", () => {
    expect(containsKeyword("REST APIs and GraphQL", "api")).toBe(true);
    expect(containsKeyword("API experience", "apis")).toBe(true);
    expect(containsKeyword("REST services", "service")).toBe(true);
    expect(containsKeyword("testing the app", "test")).toBe(false);
  });

  it("matches 'api' against a resume listing REST APIs (N-4)", () => {
    const state: ResumeBuilderState = {
      ...sampleBuilderState(),
      skills: ["REST APIs", "GraphQL"],
      summary: "Backend engineer.",
      experience: [],
    };
    const match = matchJobDescription(state, "Looking for API experience.");
    expect(match.skills.find((row) => row.keyword === "api")?.status).toBe(
      "matched",
    );
    expect(match.score).toBe(100);
  });
});

describe("containsKeyword singular fallback + -es plurals (N-5, N-6)", () => {
  it("does not let short acronyms fall back to a colliding singular (N-5)", () => {
    // "css" must not match "CS" (Computer Science), "ios" must not match
    // "io", "aws" must not match "aw" — the singular fallback is only for
    // words longer than 3 chars.
    expect(containsKeyword("CS degree from State", "css")).toBe(false);
    expect(containsKeyword("io streams", "ios")).toBe(false);
    expect(containsKeyword("aw shucks", "aws")).toBe(false);
    // The intended plurals still work for longer words.
    expect(containsKeyword("API experience", "apis")).toBe(true);
    expect(containsKeyword("REST services", "service")).toBe(true);
    expect(containsKeyword("REST APIs", "api")).toBe(true);
  });

  it("matches -es plurals in both directions (N-6)", () => {
    expect(containsKeyword("classes in Java", "class")).toBe(true);
    expect(containsKeyword("Java class", "classes")).toBe(true);
    expect(containsKeyword("status updates", "statuses")).toBe(true);
    expect(containsKeyword("statuses page", "status")).toBe(true);
    // But a word that does not take -es does not gain a false plural.
    expect(containsKeyword("testing the app", "test")).toBe(false);
  });
});

describe("containsKeyword singular words ending in s (N-7, N-8)", () => {
  it("does not strip a singular word ending in s down to a nonsense stem (N-7)", () => {
    // "status", "class", "boss", "pass", "glass" are singular — stripping
    // one "s" gives "statu"/"clas"/"bos"/"pas"/"glas", which must not match.
    expect(containsKeyword("statu", "status")).toBe(false);
    expect(containsKeyword("clas", "class")).toBe(false);
    expect(containsKeyword("Bos and colleagues", "boss")).toBe(false);
    expect(containsKeyword("pas the salt", "pass")).toBe(false);
    expect(containsKeyword("glas", "glass")).toBe(false);
    // NOTE (N-13): the ss/us branch now tries both forms, so "classes"
    // also generates "classe" (the single-"s" strip) — that is the price
    // of "houses" matching "house". "classe"/"statuse" never occur in
    // real text, so matching them is harmless. Pinned (arch #10): the
    // trade-off is enforced, not just described.
    expect(containsKeyword("classe", "classes")).toBe(true);
    expect(containsKeyword("statuse", "statuses")).toBe(true);
    // The real forms still match both ways.
    expect(containsKeyword("status report", "status")).toBe(true);
    expect(containsKeyword("statuses page", "status")).toBe(true);
    expect(containsKeyword("class project", "class")).toBe(true);
    expect(containsKeyword("classes in Java", "class")).toBe(true);
  });

  it("matches 3-char -es stems in both directions (N-8)", () => {
    // The -es form is not gated by length: "box" (3 chars) must match "boxes".
    expect(containsKeyword("boxes of cables", "box")).toBe(true);
    expect(containsKeyword("box of cables", "boxes")).toBe(true);
    expect(containsKeyword("buses and trains", "bus")).toBe(true);
    expect(containsKeyword("bus stop", "buses")).toBe(true);
    // The N-5 gate still holds for short acronyms.
    expect(containsKeyword("CS degree from State", "css")).toBe(false);
  });
});

describe("containsKeyword o-stem -es plurals (N-9)", () => {
  it("matches o-stem -es plurals in both directions (N-9)", () => {
    expect(containsKeyword("tomatoes in the garden", "tomato")).toBe(true);
    expect(containsKeyword("tomato soup", "tomatoes")).toBe(true);
    expect(containsKeyword("potatoes in the garden", "potato")).toBe(true);
    expect(containsKeyword("potato soup", "potatoes")).toBe(true);
    expect(containsKeyword("heroes of the story", "hero")).toBe(true);
    expect(containsKeyword("hero of the story", "heroes")).toBe(true);
    expect(containsKeyword("echoes in the hall", "echo")).toBe(true);
    expect(containsKeyword("echo in the hall", "echoes")).toBe(true);
    // NOTE (N-10): the o-stem rule now tries both forms, so "tomatoes"
    // also generates "tomatoe" (the single-"s" strip) — that is the
    // price of "canoes" matching "canoe". "tomatoe" never occurs in
    // real text, so matching it is harmless. Pinned (arch #10): the
    // trade-off is enforced, not just described.
    expect(containsKeyword("tomatoe", "tomatoes")).toBe(true);
    expect(containsKeyword("heroe", "heroes")).toBe(true);
  });

  it("does not let the o-stem rule turn verbs into their base form (N-9)", () => {
    // "go" is the Go language, not a verb — it must not gain "goes",
    // and "goes"/"does" must not strip to "go"/"do".
    expect(containsKeyword("goes above and beyond", "go")).toBe(false);
    expect(containsKeyword("go developer", "goes")).toBe(false);
    expect(containsKeyword("do the work", "does")).toBe(false);
  });
});

describe("containsKeyword e-word plurals ending in es (N-10, N-11)", () => {
  it("matches e-word plurals by stripping one s, not es (N-10)", () => {
    // "shoes" is a regular plural of "shoe", not an -es plural of "sho".
    expect(containsKeyword("shoe store", "shoes")).toBe(true);
    expect(containsKeyword("canoe trip", "canoes")).toBe(true);
    expect(containsKeyword("oboe player", "oboes")).toBe(true);
    expect(containsKeyword("dose of medicine", "doses")).toBe(true);
    expect(containsKeyword("case study", "cases")).toBe(true);
    // The truncated stems must not match real words. "dos" (DOS the OS)
    // is not generated, so "doses" does not match a DOS resume.
    expect(containsKeyword("DOS operating system", "doses")).toBe(false);
    // NOTE: "cano" IS generated as a form (the stem of "canoes"), so a
    // resume naming a colleague "Cano" would match a JD keyword "canoes".
    // That is accepted: "canoes" as a JD keyword is practically
    // impossible, and dropping the stem would break "tomatoes" → "tomato".
    // The true -es plurals still work.
    expect(containsKeyword("tomato soup", "tomatoes")).toBe(true);
    expect(containsKeyword("hero of the story", "heroes")).toBe(true);
    expect(containsKeyword("class project", "classes")).toBe(true);
    expect(containsKeyword("bus stop", "buses")).toBe(true);
  });

  it("pushes nothing for a 2-char o-stem verb (N-11)", () => {
    // "does"/"goes" must not manufacture "doe"/"goe".
    expect(containsKeyword("doe a deer, a female deer", "does")).toBe(false);
    expect(containsKeyword("goe to the store", "goes")).toBe(false);
    // The length guard for "go" still holds.
    expect(containsKeyword("goes above and beyond", "go")).toBe(false);
  });
});

describe("containsKeyword -use words and 2-char o-stem nouns (N-13, N-14)", () => {
  it("matches -use/-ouse plurals by stripping one s, not es (N-13)", () => {
    // "houses" is a regular plural of "house", not an -es plural of "hous".
    expect(containsKeyword("house for sale", "houses")).toBe(true);
    expect(containsKeyword("mouse pad", "mouses")).toBe(true);
    expect(containsKeyword("spouse and kids", "spouses")).toBe(true);
    expect(containsKeyword("reuse the code", "reuses")).toBe(true);
    expect(containsKeyword("excuse the delay", "excuses")).toBe(true);
    // The true -es plurals with ss/us stems still work.
    expect(containsKeyword("bus stop", "buses")).toBe(true);
    expect(containsKeyword("status page", "statuses")).toBe(true);
    expect(containsKeyword("class project", "classes")).toBe(true);
  });

  it("matches 2-char o-stem nouns, only blocking do/go (N-14)", () => {
    // "toes"/"foes"/"woes" are real nouns, not verbs — they must match.
    expect(containsKeyword("toe injury", "toes")).toBe(true);
    expect(containsKeyword("foe of the realm", "foes")).toBe(true);
    expect(containsKeyword("woe is me", "woes")).toBe(true);
    // But the verb block still holds.
    expect(containsKeyword("doe a deer, a female deer", "does")).toBe(false);
    expect(containsKeyword("goe to the store", "goes")).toBe(false);
    expect(containsKeyword("do the work", "does")).toBe(false);
  });
});
