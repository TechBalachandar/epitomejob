// Feature 2 — One-Click Tailor + Apply: pure logic.
//
// The diff is computed from the actual original and tailored text (never
// staged), and the apply step reuses the existing approval-gated path
// (applyForJob in lib/api.ts). Nothing here invents resume text.
//
// Tracker note: the tracker entry must flip to APPLIED only AFTER the
// user approves the application in the Approvals inbox — flipping it
// when the approval is merely proposed would claim an application that
// has not been sent. The approvals page already performs that flip on
// approval; ensureTrackerApplied below is the reusable form of that
// post-approval step. Call it on approval, never on proposal.
import {
  applyCopilotOption,
  copilotContextFor,
  copilotTargetLabel,
} from "./resume-builder";
import { fetchTracker, saveJob, updateTrackerEntry } from "./api";
import type {
  CopilotOption,
  CopilotTarget,
  Job,
  ResumeBuilderState,
} from "./types";

// --- Real diff -------------------------------------------------------------

export type DiffLineType = "same" | "added" | "removed";

export interface DiffLine {
  type: DiffLineType;
  text: string;
}

/** Split text into diffable units: one per line; a single-line paragraph
 * is split into sentences so a summary rewrite diffs sentence by
 * sentence instead of as one giant changed block. */
export function splitDiffUnits(text: string): string[] {
  const lines = text
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean);
  if (lines.length !== 1) return lines;
  const sentences = lines[0]
    .split(/(?<=[.!?])\s+/)
    .map((s) => s.trim())
    .filter(Boolean);
  return sentences.length > 1 ? sentences : lines;
}

/** Line/sentence LCS diff of the real before/after text. */
export function diffLines(before: string, after: string): DiffLine[] {
  const a = splitDiffUnits(before);
  const b = splitDiffUnits(after);
  const n = a.length;
  const m = b.length;
  // LCS lengths, (n+1) x (m+1).
  const dp: number[][] = Array.from({ length: n + 1 }, () =>
    new Array<number>(m + 1).fill(0),
  );
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] =
        a[i] === b[j]
          ? dp[i + 1][j + 1] + 1
          : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  const out: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      out.push({ type: "same", text: a[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      out.push({ type: "removed", text: a[i] });
      i++;
    } else {
      out.push({ type: "added", text: b[j] });
      j++;
    }
  }
  while (i < n) out.push({ type: "removed", text: a[i++] });
  while (j < m) out.push({ type: "added", text: b[j++] });
  return out;
}

// --- Tailor targets ----------------------------------------------------------

export interface TailorTarget {
  target: CopilotTarget;
  label: string;
  /** The exact current text Co-Pilot is asked to rewrite. */
  originalText: string;
}

/** Sections Co-Pilot can tailor: the summary plus every experience item
 * that has bullets. Empty sections are skipped — no call, no diff. */
export function tailorTargetsFor(state: ResumeBuilderState): TailorTarget[] {
  const targets: TailorTarget[] = [];
  if (state.summary.trim()) {
    const target: CopilotTarget = { section: "summary", label: "Summary" };
    targets.push({
      target,
      label: copilotTargetLabel(target),
      originalText: copilotContextFor(state, target),
    });
  }
  state.experience.forEach((item, index) => {
    if (!item.bullets.some((b) => b.trim())) return;
    const target: CopilotTarget = {
      section: "experience",
      index,
      label: `Experience ${index + 1}`,
    };
    targets.push({
      target,
      label: copilotTargetLabel(target),
      originalText: copilotContextFor(state, target),
    });
  });
  return targets;
}

/** Job description text sent to Co-Pilot as target_jd. */
export function jobDescriptionText(job: Job): string {
  return [
    `${job.title} at ${job.company}`,
    `${job.location} · ${job.workType}`,
    job.summary,
    job.skills.length > 0 ? `Skills: ${job.skills.join(", ")}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

/** The tailored text one chosen Co-Pilot option produces for its block —
 * the same rendering applyCopilotOption uses, so the diff shows exactly
 * what will be saved. */
export function tailoredTextForOption(
  target: CopilotTarget,
  option: CopilotOption,
): string {
  if (target.section === "summary") return option.bullets.join(" ");
  return option.bullets.join("\n");
}

export interface TailorPick {
  target: CopilotTarget;
  option: CopilotOption;
}

/** Apply the chosen options to a copy of the document state. */
export function buildTailoredState(
  state: ResumeBuilderState,
  picks: TailorPick[],
): ResumeBuilderState {
  return picks.reduce(
    (next, pick) => applyCopilotOption(next, pick.target, pick.option),
    state,
  );
}

export interface SectionDiff {
  label: string;
  originalText: string;
  tailoredText: string;
  lines: DiffLine[];
  /** True when Co-Pilot changed nothing in this section. */
  unchanged: boolean;
}

/** Real before/after diffs for every picked section. */
export function sectionDiffs(
  state: ResumeBuilderState,
  picks: TailorPick[],
): SectionDiff[] {
  return picks.map((pick) => {
    const originalText = copilotContextFor(state, pick.target);
    const tailoredText = tailoredTextForOption(pick.target, pick.option);
    return {
      label: copilotTargetLabel(pick.target),
      originalText,
      tailoredText,
      lines: diffLines(originalText, tailoredText),
      unchanged: originalText.trim() === tailoredText.trim(),
    };
  });
}

// --- Post-approval tracker step ----------------------------------------------

/**
 * Move the tracker entry for a job to APPLIED, creating it first when
 * none exists. ONLY call this after the user has approved the
 * APPLY_JOB approval — the approvals page already does this on approval;
 * this is the reusable form of that same step. It must never run when
 * the approval is merely proposed.
 */
export async function ensureTrackerApplied(jobId: string): Promise<void> {
  const existing = (await fetchTracker()).find((t) => t.jobId === jobId);
  if (existing) {
    if (existing.status !== "APPLIED") {
      await updateTrackerEntry(existing.id, { status: "APPLIED" });
    }
    return;
  }
  const entry = await saveJob(jobId);
  await updateTrackerEntry(entry.id, { status: "APPLIED" });
}
