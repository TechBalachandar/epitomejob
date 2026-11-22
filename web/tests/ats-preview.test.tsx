// ATS parse preview tests: the pure flagging rules (observable evidence
// only — no invented scores) plus the component fetching the real
// get-text response and rendering the raw parsed text verbatim.
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ATS_HAS_CONFIDENCE,
  ATS_NO_CONFIDENCE_NOTE,
  analyzeParsedText,
  normalizeResumeText,
  parsedTextLines,
} from "@/lib/ats-preview";
import { AtsPreview } from "@/components/resumes/ats-preview";
import { useAuthStore } from "@/lib/auth-store";

const FULL_TEXT = `Alex Morgan
Senior Software Engineer
alex.morgan@example.com · (571) 555-0142 · Reston, VA
linkedin.com/in/alexmorgan

SUMMARY
Software engineer with 8 years building Java services.

EXPERIENCE
Senior Software Engineer — Meridian Care Technologies, 2021 – Present
• Led the migration of 12 services to Spring Boot 3.

EDUCATION
B.S. Computer Science — George Mason University, 2013 – 2017

SKILLS
Java, Spring Boot, Kafka, Azure, PostgreSQL`;

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

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
  vi.restoreAllMocks();
  useAuthStore.getState().logout();
});

describe("analyzeParsedText", () => {
  it("flags nothing on a complete parsed text", () => {
    const result = analyzeParsedText(FULL_TEXT);
    expect(result.hasText).toBe(true);
    expect(result.hasEmail).toBe(true);
    expect(result.hasPhone).toBe(true);
    expect(result.hasLinkedin).toBe(true);
    expect(result.sections.map((s) => s.status)).toEqual([
      "present",
      "present",
      "present",
      "present",
    ]);
    expect(result.flags).toEqual([]);
    expect(result.words).toBeGreaterThan(20);
  });

  it("flags a missing email and phone, and only those contact fields", () => {
    const result = analyzeParsedText("Alex Morgan\nSUMMARY\nHello.\n");
    expect(result.hasEmail).toBe(false);
    expect(result.hasPhone).toBe(false);
    expect(result.flags.map((f) => f.id)).toContain("no-email");
    expect(result.flags.map((f) => f.id)).toContain("no-phone");
  });

  it("flags a missing section without claiming the parser dropped it", () => {
    const result = analyzeParsedText(
      "Alex Morgan\nalex@example.com\n(571) 555-0142\nSUMMARY\nHello.\nEXPERIENCE\nDid things.\nEDUCATION\nStudied.\n",
    );
    const skills = result.sections.find((s) => s.name === "Skills");
    expect(skills?.status).toBe("missing");
    const flag = result.flags.find((f) => f.id === "section-missing-skills");
    expect(flag?.message).toMatch(/not found in the parsed text/);
    expect(flag?.message).not.toMatch(/dropped/i);
  });

  it("flags a heading with no content lines as empty", () => {
    const result = analyzeParsedText(
      "Alex Morgan\nalex@example.com\n(571) 555-0142\nSUMMARY\nEXPERIENCE\nDid things.\nEDUCATION\nStudied.\nSKILLS\nJava\n",
    );
    const summary = result.sections.find((s) => s.name === "Summary");
    expect(summary?.status).toBe("empty");
    expect(result.flags.map((f) => f.id)).toContain("section-empty-summary");
  });

  it("recognises the parser's heading variants (Work Experience, Technical Skills)", () => {
    const result = analyzeParsedText(
      "alex@example.com\n(571) 555-0142\nWORK EXPERIENCE\nDid things.\nTECHNICAL SKILLS\nJava\n",
    );
    expect(result.sections.find((s) => s.name === "Experience")?.status).toBe("present");
    expect(result.sections.find((s) => s.name === "Skills")?.status).toBe("present");
  });

  it("flags undecodable characters and counts the affected lines", () => {
    const result = analyzeParsedText(
      "alex@example.com\n(571) 555-0142\nSUMMARY\nCaf� manager.\nEXPERIENCE\nDid things.\nEDUCATION\nStudied.\nSKILLS\nJava\n",
    );
    expect(result.undecodableLines).toBe(1);
    expect(result.flags.map((f) => f.id)).toContain("undecodable");
  });

  it("reports no parsed text as a single flag, with no invented detail", () => {
    for (const text of [null, undefined, "", "   "]) {
      const result = analyzeParsedText(text);
      expect(result.hasText).toBe(false);
      expect(result.flags.map((f) => f.id)).toEqual(["no-text"]);
      expect(result.sections.every((s) => s.status === "missing")).toBe(true);
    }
  });

  it("exposes no confidence scores", () => {
    expect(ATS_HAS_CONFIDENCE).toBe(false);
  });
});

describe("normalizeResumeText", () => {
  it("accepts {resumeId, text} and null text", () => {
    expect(normalizeResumeText({ resumeId: "r-1", text: "hello" })).toEqual({
      resumeId: "r-1",
      text: "hello",
    });
    expect(normalizeResumeText({ resumeId: "r-1", text: null })).toEqual({
      resumeId: "r-1",
      text: null,
    });
    expect(normalizeResumeText({ text: "hello" })).toBeNull();
    expect(normalizeResumeText(null)).toBeNull();
  });
});

describe("AtsPreview component", () => {
  it("renders the raw parsed text verbatim with its flags", async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(jsonResponse({ resumeId: "resume-1", text: FULL_TEXT })),
    );
    vi.stubGlobal("fetch", fetchMock);
    render(<AtsPreview resumeId="resume-1" label="Android Developer" />);

    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/resumes/resume-1/text",
        expect.objectContaining({ credentials: "same-origin" }),
      ),
    );
    // Raw text shown exactly as returned (name line, bullet, email).
    expect(await screen.findByText(/Led the migration of 12 services/)).toBeInTheDocument();
    expect(screen.getByText(/alex\.morgan@example\.com/)).toBeInTheDocument();
    expect(screen.getByText("Email found")).toBeInTheDocument();
    expect(screen.getByText(/No confidence scores/)).toBeInTheDocument();
    expect(screen.queryByText(/confidence score:/i)).not.toBeInTheDocument();
  });

  it("flags what is missing in the parsed text", async () => {
    const text = "Alex Morgan\nSUMMARY\nHello.\n";
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(jsonResponse({ resumeId: "r-1", text }))));
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(await screen.findByText("No email address found in the parsed text.")).toBeInTheDocument();
    expect(screen.getByText(/Experience not found in the parsed text/)).toBeInTheDocument();
  });

  it("shows the backend error when the text is not ready", async () => {
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve(jsonResponse({ message: "Resume text is not ready yet." }, 400)),
    ));
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(await screen.findByText("Resume text is not ready yet.")).toBeInTheDocument();
  });
});

describe("parsedTextLines", () => {
  it("splits CRLF, trims, and drops blank lines", () => {
    expect(parsedTextLines("  Alex Morgan  \r\n\r\n\nSUMMARY\r\n  Hello.  ")).toEqual([
      "Alex Morgan",
      "SUMMARY",
      "Hello.",
    ]);
    expect(parsedTextLines("")).toEqual([]);
    expect(parsedTextLines("   \n\t\n  ")).toEqual([]);
  });
});

describe("analyzeParsedText — headings (360°)", () => {
  const CONTACT = "alex@example.com\n(571) 555-0142\n";

  it("accepts a trailing colon and any case on headings", () => {
    const result = analyzeParsedText(
      `${CONTACT}summary:\nHello.\nExperience:\nDid things.\neducation:\nStudied.\nskills:\nJava\n`,
    );
    expect(result.sections.map((s) => s.status)).toEqual([
      "present",
      "present",
      "present",
      "present",
    ]);
  });

  it("recognises every documented heading variant", () => {
    const result = analyzeParsedText(
      `${CONTACT}PROFESSIONAL SUMMARY\nHello.\nEMPLOYMENT HISTORY\nDid things.\nEDUCATION & CERTIFICATIONS\nStudied.\nSKILLS & TECHNOLOGIES\nJava\n`,
    );
    expect(result.sections.map((s) => s.status)).toEqual([
      "present",
      "present",
      "present",
      "present",
    ]);
    const alt = analyzeParsedText(
      `${CONTACT}PROFILE\nHello.\nWORK HISTORY\nDid things.\nEDUCATION AND CERTIFICATIONS\nStudied.\nCORE SKILLS\nJava\n`,
    );
    expect(alt.sections.map((s) => s.status)).toEqual([
      "present",
      "present",
      "present",
      "present",
    ]);
    const alt2 = analyzeParsedText(
      `${CONTACT}OBJECTIVE\nHello.\nABOUT\nMore.\nEXECUTIVE SUMMARY\nEven more.\n`,
    );
    // First Summary variant wins; content runs only to the next
    // recognised heading, so the later Summary variants are not
    // re-counted as content.
    expect(alt2.sections.find((s) => s.name === "Summary")?.status).toBe("present");
    expect(alt2.sections.find((s) => s.name === "Summary")?.contentLines).toBe(1);
  });

  it("uses the first occurrence of a repeated heading; content stops at the next heading", () => {
    const result = analyzeParsedText(
      `${CONTACT}EXPERIENCE\nFirst role.\nEXPERIENCE\nSecond role.\nSKILLS\nJava\n`,
    );
    const exp = result.sections.find((s) => s.name === "Experience");
    expect(exp?.status).toBe("present");
    expect(exp?.contentLines).toBe(1);
    expect(exp?.detail).toBe("1 line");
  });

  it("marks a heading followed only by another heading as empty", () => {
    const result = analyzeParsedText(
      `${CONTACT}SUMMARY\nEXPERIENCE\nDid things.\nEDUCATION\nStudied.\nSKILLS\nJava\n`,
    );
    expect(result.sections.find((s) => s.name === "Summary")?.status).toBe("empty");
    const tail = analyzeParsedText(`${CONTACT}SUMMARY\nHello.\nSKILLS\n`);
    expect(tail.sections.find((s) => s.name === "Skills")?.status).toBe("empty");
    expect(tail.flags.map((f) => f.id)).toContain("section-empty-skills");
  });

  it("does not treat a heading-like word inside a sentence as a heading", () => {
    const result = analyzeParsedText(
      `${CONTACT}I have experience with Java.\nMy skills include Kafka.\n`,
    );
    expect(result.sections.every((s) => s.status === "missing")).toBe(true);
  });
});

describe("analyzeParsedText — contact detection (360°)", () => {
  it("accepts common phone formats", () => {
    for (const phone of [
      "+1 (571) 555-0142",
      "(571) 555-0142",
      "571.555.0142",
      "571-555-0142",
      "571 555 0142",
      "+1 571-555-0142",
    ]) {
      expect(analyzeParsedText(`Alex\n${phone}\nSUMMARY\nHi.\n`).hasPhone).toBe(true);
    }
  });

  it("rejects a 7-digit number and a bare digit string as a phone", () => {
    expect(analyzeParsedText("Alex\n555-0142\nSUMMARY\nHi.\n").hasPhone).toBe(false);
    expect(analyzeParsedText("Alex\n12345\nSUMMARY\nHi.\n").hasPhone).toBe(false);
  });

  it("rejects malformed emails and accepts a minimal valid one", () => {
    expect(analyzeParsedText("Alex\nnot-an-email@\nSUMMARY\nHi.\n").hasEmail).toBe(false);
    expect(analyzeParsedText("Alex\n@nodomain.com\nSUMMARY\nHi.\n").hasEmail).toBe(false);
    expect(analyzeParsedText("Alex\na@b.co\nSUMMARY\nHi.\n").hasEmail).toBe(true);
    expect(analyzeParsedText("Reach me at alex.morgan+work@example.co.uk any time.\n").hasEmail).toBe(true);
  });

  it("detects LinkedIn case-insensitively, but not a company page", () => {
    expect(analyzeParsedText("LinkedIn.com/in/alexmorgan\n").hasLinkedin).toBe(true);
    expect(analyzeParsedText("linkedin.com/company/acme\n").hasLinkedin).toBe(false);
    expect(analyzeParsedText("linkedin.com/in/\n").hasLinkedin).toBe(false);
  });

  it("does not flag LinkedIn as missing — it is informational only", () => {
    const result = analyzeParsedText("Alex\nalex@example.com\n(571) 555-0142\nSUMMARY\nHi.\nEXPERIENCE\nDid things.\nEDUCATION\nStudied.\nSKILLS\nJava\n");
    expect(result.hasLinkedin).toBe(false);
    expect(result.flags.map((f) => f.id)).not.toContain("no-linkedin");
    expect(result.flags).toEqual([]);
  });
});

describe("analyzeParsedText — counts and undecodable text (360°)", () => {
  it("counts characters (raw, incl. newlines), words and non-empty lines", () => {
    const text = "Alex Morgan\nalex@example.com\n\nSUMMARY\nHello world.\n";
    const result = analyzeParsedText(text);
    expect(result.characters).toBe(text.length);
    expect(result.words).toBe(6);
    expect(result.lines).toBe(4);
  });

  it("counts several undecodable lines and uses the plural message", () => {
    const result = analyzeParsedText(
      "alex@example.com\n(571) 555-0142\nSUMMARY\nCaf� manager.\nEXPERIENCE\nDid � things.\nEDUCATION\nStudied.\nSKILLS\nJava\n",
    );
    expect(result.undecodableLines).toBe(2);
    const flag = result.flags.find((f) => f.id === "undecodable");
    expect(flag?.severity).toBe("warning");
    expect(flag?.message).toMatch(/2 lines contain/);
  });

  it("uses the singular message for one undecodable line", () => {
    const result = analyzeParsedText("SUMMARY\nCaf� manager.\n");
    expect(result.flags.find((f) => f.id === "undecodable")?.message).toMatch(
      /1 line contains/,
    );
  });

  it("a line of only replacement characters still counts as undecodable", () => {
    const result = analyzeParsedText("SUMMARY\n���\n");
    expect(result.undecodableLines).toBe(1);
  });

  it("empty text reports zero counts and no invented contact detail", () => {
    const result = analyzeParsedText("");
    expect(result).toMatchObject({
      characters: 0,
      words: 0,
      lines: 0,
      undecodableLines: 0,
      hasEmail: false,
      hasPhone: false,
      hasLinkedin: false,
    });
  });

  it("a very long text is analysed without truncation", () => {
    const text = `alex@example.com\n(571) 555-0142\nSUMMARY\n${"word ".repeat(5000)}\nEXPERIENCE\nDid things.\nEDUCATION\nStudied.\nSKILLS\nJava\n`;
    const result = analyzeParsedText(text);
    expect(result.words).toBeGreaterThan(5000);
    expect(result.flags).toEqual([]);
  });

  it("exposes the no-confidence note text", () => {
    expect(ATS_NO_CONFIDENCE_NOTE).toMatch(/No confidence scores/);
    expect(ATS_NO_CONFIDENCE_NOTE).toMatch(/only the parsed text/);
  });
});

describe("normalizeResumeText — malformed shapes (360°)", () => {
  it("rejects non-objects", () => {
    expect(normalizeResumeText([])).toBeNull();
    expect(normalizeResumeText("text")).toBeNull();
    expect(normalizeResumeText(42)).toBeNull();
    expect(normalizeResumeText(undefined)).toBeNull();
  });

  it("rejects a missing or empty resumeId", () => {
    expect(normalizeResumeText({ resumeId: "", text: "x" })).toBeNull();
    expect(normalizeResumeText({ resumeId: 123, text: "x" })).toBeNull();
  });

  it("coerces a non-string text to null and ignores extra fields", () => {
    expect(normalizeResumeText({ resumeId: "r-1", text: 123, extra: true })).toEqual({
      resumeId: "r-1",
      text: null,
    });
    expect(normalizeResumeText({ resumeId: "r-1" })).toEqual({
      resumeId: "r-1",
      text: null,
    });
  });
});

describe("AtsPreview component — errors, states, ownership (360°)", () => {
  it("shows the backend message on a 404 (missing or another user's resume)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          jsonResponse({ message: "Resume not found" }, 404),
        ),
      ),
    );
    render(<AtsPreview resumeId="someone-elses-resume" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(await screen.findByText("Resume not found")).toBeInTheDocument();
    // Nothing from the other resume is ever rendered.
    expect(screen.queryByText(/Led the migration/)).not.toBeInTheDocument();
  });

  it("shows the backend error field when there is no message", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(jsonResponse({ error: "Parse failed" }, 500))),
    );
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(await screen.findByText("Parse failed")).toBeInTheDocument();
  });

  it("falls back to a generic message on a non-JSON error body", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(new Response("<html>error</html>", { status: 500 }))),
    );
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(
      await screen.findByText("Could not load the parsed text (500)"),
    ).toBeInTheDocument();
  });

  it("shows the fetch failure message when the network drops", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new Error("Network down"))),
    );
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(await screen.findByText("Network down")).toBeInTheDocument();
  });

  it("shows a loading state while fetching, then the result", async () => {
    let resolveFetch!: (res: Response) => void;
    const fetchMock = vi.fn(
      () => new Promise<Response>((resolve) => { resolveFetch = resolve; }),
    );
    vi.stubGlobal("fetch", fetchMock);
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(await screen.findByText("Loading parsed text…")).toBeInTheDocument();
    resolveFetch(jsonResponse({ resumeId: "r-1", text: FULL_TEXT }));
    expect(await screen.findByText(/Led the migration of 12 services/)).toBeInTheDocument();
    expect(screen.queryByText("Loading parsed text…")).not.toBeInTheDocument();
  });

  it("reports a null parsed text as no text, with the single no-text flag", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(jsonResponse({ resumeId: "r-1", text: null }))),
    );
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(
      await screen.findByText("The parser returned no text for this file."),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/No parsed text — the parser returned nothing/),
    ).toBeInTheDocument();
  });

  it("does not refetch when the view is closed and reopened", async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(jsonResponse({ resumeId: "r-1", text: FULL_TEXT })),
    );
    vi.stubGlobal("fetch", fetchMock);
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole("button", { name: "Hide ATS view" }));
    expect(screen.queryByText(/Led the migration/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(await screen.findByText(/Led the migration/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("says 'Nothing flagged' on a complete text and shows the undecodable flag in the UI", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(jsonResponse({ resumeId: "r-1", text: FULL_TEXT }))),
    );
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(await screen.findByText(/Nothing flagged/)).toBeInTheDocument();
    expect(screen.getByText("LinkedIn found")).toBeInTheDocument();

    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          jsonResponse({
            resumeId: "r-2",
            text: "alex@example.com\n(571) 555-0142\nSUMMARY\nCaf� manager.\nEXPERIENCE\nDid things.\nEDUCATION\nStudied.\nSKILLS\nJava\n",
          }),
        ),
      ),
    );
    render(<AtsPreview resumeId="r-2" label="Other" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    expect(
      await screen.findByText(/characters the parser could not decode/),
    ).toBeInTheDocument();
    expect(screen.getByText("LinkedIn not found")).toBeInTheDocument();
  });

  it("shows 0 characters / 0 words for an empty parsed text", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(jsonResponse({ resumeId: "r-1", text: "" }))),
    );
    render(<AtsPreview resumeId="r-1" label="Plain" />);
    fireEvent.click(screen.getByRole("button", { name: "How an ATS reads this" }));
    await waitFor(() => expect(screen.getByText(/0 characters/)).toBeInTheDocument());
    expect(screen.getByText(/0 words/)).toBeInTheDocument();
  });
});
