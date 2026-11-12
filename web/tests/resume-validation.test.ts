import { describe, expect, it } from "vitest";
import { formatFileSize, validateResumeFile, MAX_RESUME_BYTES } from "@/lib/validation";

function pdf(name = "resume.pdf", size = 120_000): File {
  return new File(["x".repeat(Math.min(size, 1000))], name, {
    type: "application/pdf",
  });
}

describe("validateResumeFile", () => {
  it("accepts a PDF under the size limit", () => {
    expect(validateResumeFile(pdf())).toBeNull();
  });

  it("rejects non-PDF files", () => {
    const docx = new File(["x"], "resume.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    });
    expect(validateResumeFile(docx)).toMatch(/PDF only/);
  });

  it("rejects a PDF over 10 MB", () => {
    const big = new File([], "big.pdf", { type: "application/pdf" });
    Object.defineProperty(big, "size", { value: MAX_RESUME_BYTES + 1 });
    expect(validateResumeFile(big)).toMatch(/larger than 10 MB/);
  });

  it("rejects an empty file", () => {
    const empty = new File([], "empty.pdf", { type: "application/pdf" });
    expect(validateResumeFile(empty)).toMatch(/empty/);
  });
});

describe("formatFileSize", () => {
  it("formats bytes, KB, and MB", () => {
    expect(formatFileSize(500)).toBe("500 B");
    expect(formatFileSize(2048)).toBe("2.0 KB");
    expect(formatFileSize(2_621_440)).toBe("2.5 MB");
  });
});
