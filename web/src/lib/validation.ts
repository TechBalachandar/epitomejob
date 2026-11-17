// Resume upload validation. Mirrors the backend rules (PDF, DOC, DOCX,
// max 10 MB) so the UI rejects bad files before any upload is attempted.
// The mock API handler enforces the same rules again server-side.
export const MAX_RESUME_BYTES = 10 * 1024 * 1024;

const RESUME_TYPES = new Set([
  "application/pdf",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
]);

export function validateResumeFile(file: File): string | null {
  const lower = file.name.toLowerCase();
  const isResume =
    RESUME_TYPES.has(file.type) ||
    lower.endsWith(".pdf") ||
    lower.endsWith(".doc") ||
    lower.endsWith(".docx");
  if (!isResume) return "Upload a PDF, DOC, or DOCX file.";
  if (file.size === 0) return "That file is empty. Choose a resume with content.";
  if (file.size > MAX_RESUME_BYTES) {
    return "File is larger than 10 MB. Please choose a smaller file.";
  }
  return null;
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
