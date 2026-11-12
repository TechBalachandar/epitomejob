// Resume upload validation. Mirrors the backend rules (Phase 0: PDF only,
// max 10 MB) so the UI rejects bad files before any upload is attempted.
// The mock API handler enforces the same rules again server-side.
export const MAX_RESUME_BYTES = 10 * 1024 * 1024;

export function validateResumeFile(file: File): string | null {
  const isPdf =
    file.type === "application/pdf" ||
    file.name.toLowerCase().endsWith(".pdf");
  if (!isPdf) return "Phase 0 accepts PDF only. Please choose a .pdf file.";
  if (file.size === 0) return "That file is empty. Choose a PDF with content.";
  if (file.size > MAX_RESUME_BYTES) {
    return "File is larger than 10 MB. Please choose a smaller PDF.";
  }
  return null;
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
