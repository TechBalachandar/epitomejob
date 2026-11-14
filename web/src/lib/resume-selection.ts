// W6: per-resume selection — pure helpers, no React / Next imports.
// The shared selection lives in the URL (?resumeId=) so Dashboard and
// Matches show the same resume and the choice survives reload and is
// shareable. Default = latest READY resume (the backend's no-resumeId
// behaviour); an explicit ?resumeId= that names a real resume wins even
// when it is not READY, so the backend's 400 RESUME_NOT_READY surfaces
// instead of silently switching resumes under the user.
import type { ResumeItem } from "./types";

/** Latest READY resume by upload date, or null when there is none. */
export function latestReadyResume(
  resumes: ResumeItem[] | undefined,
): ResumeItem | null {
  const ready = (resumes ?? []).filter((r) => r.status === "READY");
  if (ready.length === 0) return null;
  return [...ready].sort(
    (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
  )[0];
}

/**
 * Resolve the selected resume id from the URL param and the loaded list.
 * Returns null while the list is still loading (caller keeps the raw
 * param) — callers pass resumes only once loaded.
 */
export function resolveSelectedResumeId(
  resumes: ResumeItem[] | undefined,
  resumeIdParam: string | null,
): string | null {
  if (!resumes) return resumeIdParam;
  if (resumeIdParam && resumes.some((r) => r.id === resumeIdParam)) {
    return resumeIdParam;
  }
  return latestReadyResume(resumes)?.id ?? null;
}

/** Top match score for one resume's match list, or null when no matches. */
export function topMatchScoreForResume(
  matches: { score: number }[] | undefined,
): number | null {
  const list = matches ?? [];
  if (list.length === 0) return null;
  return Math.max(...list.map((m) => m.score));
}
