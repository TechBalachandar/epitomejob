// Feature 2 — Tailor + Apply modal (jobs page).
// Flow, all on real paths:
//   1. Pick one of the user's Resume Builder documents (ResumeSelector,
//      fed with the builder documents — it only renders id + label).
//   2. Co-Pilot tailors the summary + each experience block to this job
//      via the existing copilot route (requestCopilot, one call per
//      section). The user picks one option per section.
//   3. A real diff view shows the actual original vs tailored text.
//   4. On confirm: the tailored state is saved to the document, saved
//      to the profile (returns a real resume id), then the existing
//      approval-gated apply runs (useApplyJob). Nothing is sent until
//      the user approves it in the Approvals inbox — approving there
//      moves the tracker entry to APPLIED (see lib/tailor-apply.ts).
"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ApiError,
  requestCopilot,
  saveBuilderDocumentToProfile,
  updateBuilderDocument,
  useApplyJob,
  useBuilderDocuments,
} from "@/lib/api";
import {
  buildTailoredState,
  diffLines,
  jobDescriptionText,
  sectionDiffs,
  tailorTargetsFor,
  type TailorTarget,
} from "@/lib/tailor-apply";
import type {
  CopilotOption,
  Job,
  ResumeBuilderDocument,
  ResumeBuilderState,
  ResumeItem,
} from "@/lib/types";
import { ResumeSelector } from "@/components/resume-selector";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { X } from "lucide-react";

type Step = "pick" | "tailoring" | "review" | "applying" | "done";

interface SectionResult {
  target: TailorTarget;
  options: CopilotOption[];
  selectedId: string | null;
  skipped: boolean;
}

/** ResumeSelector renders ResumeItem rows (id + label + status); map a
 * builder document onto that shape — id and title are the real values. */
function builderDocToResumeItem(doc: ResumeBuilderDocument): ResumeItem {
  return {
    id: doc.id ?? "",
    label: doc.title ?? "Untitled resume",
    fileName: doc.title ?? "Untitled resume",
    fileType: "application/pdf",
    fileSize: 0,
    status: "READY",
    createdAt: doc.createdAt ?? "",
  };
}

function DiffView({ before, after }: { before: string; after: string }) {
  const lines = useMemo(() => diffLines(before, after), [before, after]);
  return (
    <div
      data-testid="tailor-diff"
      className="flex flex-col gap-0.5 rounded-lg border border-slate-200 bg-white p-3 font-mono text-xs leading-relaxed"
    >
      {lines.map((line, i) => (
        <p
          key={i}
          className={
            line.type === "added"
              ? "bg-emerald-50 text-emerald-900"
              : line.type === "removed"
                ? "bg-rose-50 text-rose-900 line-through"
                : "text-slate-600"
          }
        >
          <span aria-hidden className="mr-2 inline-block w-3">
            {line.type === "added" ? "+" : line.type === "removed" ? "−" : " "}
          </span>
          {line.text}
        </p>
      ))}
      {lines.length === 0 && (
        <p className="text-slate-500">No text to compare.</p>
      )}
    </div>
  );
}

export function TailorApplyModal({
  job,
  open,
  onClose,
  onApplied,
}: {
  job: Job;
  open: boolean;
  onClose: () => void;
  onApplied?: () => void;
}) {
  const router = useRouter();
  const { data: documents, isLoading: docsLoading } = useBuilderDocuments({
    enabled: open,
  });
  const applyJob = useApplyJob();
  const [step, setStep] = useState<Step>("pick");
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null);
  const [results, setResults] = useState<SectionResult[]>([]);
  const [skippedNotes, setSkippedNotes] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);

  const docs = (documents ?? []).filter((d) => d.id);
  const selectedDoc = docs.find((d) => d.id === selectedDocId) ?? null;

  function resetAndClose() {
    setStep("pick");
    setSelectedDocId(null);
    setResults([]);
    setSkippedNotes([]);
    setError(null);
    onClose();
  }

  async function handleTailor() {
    if (!selectedDoc?.id) return;
    const state: ResumeBuilderState = selectedDoc.state;
    const targets = tailorTargetsFor(state);
    if (targets.length === 0) {
      setError(
        "This document has no summary or experience bullets to tailor. Add some in the Resume Builder first.",
      );
      return;
    }
    setError(null);
    setStep("tailoring");
    const jd = jobDescriptionText(job);
    const next: SectionResult[] = [];
    const skipped: string[] = [];
    for (const target of targets) {
      try {
        const res = await requestCopilot(selectedDoc.id, {
          active_section: target.target.section,
          user_context: target.originalText,
          target_jd: jd,
          resume_state: state,
        });
        if (res.options.length === 0) {
          skipped.push(`${target.label}: Co-Pilot returned no options.`);
          next.push({ target, options: [], selectedId: null, skipped: true });
        } else {
          next.push({
            target,
            options: res.options,
            selectedId: res.options[0]?.id ?? null,
            skipped: false,
          });
        }
      } catch (err) {
        skipped.push(
          `${target.label}: ${err instanceof ApiError ? err.message : "Co-Pilot is unavailable."}`,
        );
        next.push({ target, options: [], selectedId: null, skipped: true });
      }
    }
    setResults(next);
    setSkippedNotes(skipped);
    if (next.every((r) => r.skipped)) {
      setError(
        "Co-Pilot could not tailor any section. Check the connection and try again — nothing was changed.",
      );
      setStep("pick");
      return;
    }
    setStep("review");
  }

  function pickOption(sectionKey: string, optionId: string) {
    setResults((rs) =>
      rs.map((r) =>
        `${r.target.target.section}-${r.target.target.index ?? ""}` === sectionKey
          ? { ...r, selectedId: optionId }
          : r,
      ),
    );
  }

  const picks = results
    .filter((r) => !r.skipped && r.selectedId)
    .map((r) => ({
      target: r.target.target,
      option: r.options.find((o) => o.id === r.selectedId) ?? r.options[0],
    }))
    .filter((p): p is { target: typeof p.target; option: CopilotOption } =>
      Boolean(p.option),
    );

  const diffs = useMemo(() => {
    if (!selectedDoc || step !== "review") return [];
    return sectionDiffs(selectedDoc.state, picks);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedDoc, step, results]);

  async function handleConfirmApply() {
    if (!selectedDoc?.id || picks.length === 0) return;
    setError(null);
    setStep("applying");
    try {
      const tailored = buildTailoredState(selectedDoc.state, picks);
      await updateBuilderDocument(selectedDoc.id, { state: tailored });
      const label = `Tailored — ${job.title} at ${job.company}`.slice(0, 80);
      const saved = await saveBuilderDocumentToProfile(selectedDoc.id, label);
      if (!saved.resumeId) {
        throw new Error("The tailored resume could not be saved to your profile.");
      }
      await applyJob.mutateAsync({ jobId: job.id, resumeId: saved.resumeId });
      setStep("done");
      onApplied?.();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : "Could not apply. Nothing was sent.",
      );
      setStep("review");
    }
  }

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`Tailor and apply to ${job.title}`}
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-900/50 p-4"
      onClick={resetAndClose}
    >
      <Card
        className="my-8 w-full max-w-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <CardHeader>
          <div className="flex items-start justify-between gap-2">
            <div>
              <CardTitle>
                Tailor + Apply — {job.title} at {job.company}
              </CardTitle>
              <p className="mt-1 text-sm text-slate-500">
                Co-Pilot tailors your resume to this job, you review the
                exact changes, then the application is proposed for your
                approval. Nothing is sent until you approve it.
              </p>
            </div>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Close"
              onClick={resetAndClose}
            >
              <X className="h-4 w-4" aria-hidden />
            </Button>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {error && (
            <p role="alert" className="text-sm text-rose-600">
              {error}
            </p>
          )}

          {step === "pick" && (
            <>
              {docsLoading && (
                <p className="text-sm text-slate-500">Loading your resumes…</p>
              )}
              {!docsLoading && docs.length === 0 && (
                <p className="text-sm text-slate-600">
                  No Resume Builder documents yet. Create one in the Resume
                  Builder first, then tailor it here.
                </p>
              )}
              {docs.length > 0 && (
                <ResumeSelector
                  resumes={docs.map(builderDocToResumeItem)}
                  value={selectedDocId}
                  onChange={setSelectedDocId}
                  id="tailor-resume-selector"
                />
              )}
              <div>
                <Button
                  disabled={!selectedDocId}
                  onClick={() => void handleTailor()}
                >
                  Tailor with Co-Pilot
                </Button>
              </div>
            </>
          )}

          {step === "tailoring" && (
            <p className="text-sm text-slate-500">
              Asking Co-Pilot to tailor your resume to this job…
            </p>
          )}

          {step === "review" && (
            <>
              {skippedNotes.length > 0 && (
                <p className="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  {skippedNotes.join(" ")} Those sections keep their
                  original text.
                </p>
              )}
              {results
                .filter((r) => !r.skipped)
                .map((result) => {
                  const key = `${result.target.target.section}-${result.target.target.index ?? ""}`;
                  const diff = diffs.find(
                    (d) => d.label === result.target.label,
                  );
                  const selected =
                    result.options.find((o) => o.id === result.selectedId) ??
                    result.options[0];
                  return (
                    <section key={key} className="flex flex-col gap-2">
                      <h3 className="text-sm font-semibold text-slate-900">
                        {result.target.label}
                      </h3>
                      <div className="flex flex-wrap gap-2" role="group" aria-label={`Co-Pilot options for ${result.target.label}`}>
                        {result.options.map((option) => (
                          <button
                            key={option.id ?? option.angle}
                            type="button"
                            aria-pressed={result.selectedId === option.id}
                            onClick={() => pickOption(key, option.id ?? "")}
                            className={`rounded-full border px-3 py-1 text-xs font-medium ${
                              result.selectedId === option.id
                                ? "border-indigo-600 bg-indigo-600 text-white"
                                : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                            }`}
                          >
                            {option.angle}
                          </button>
                        ))}
                      </div>
                      {selected?.atsKeywords.length ? (
                        <div className="flex flex-wrap gap-1.5">
                          {selected.atsKeywords.map((keyword) => (
                            <Badge key={keyword} variant="secondary">
                              {keyword}
                            </Badge>
                          ))}
                        </div>
                      ) : null}
                      {diff && (
                        <DiffView
                          before={diff.originalText}
                          after={diff.tailoredText}
                        />
                      )}
                      {diff?.unchanged && (
                        <p className="text-xs text-slate-500">
                          Co-Pilot returned this section unchanged.
                        </p>
                      )}
                    </section>
                  );
                })}
              <div className="flex flex-wrap gap-2">
                <Button
                  disabled={picks.length === 0}
                  onClick={() => void handleConfirmApply()}
                >
                  Save tailored resume &amp; request apply
                </Button>
                <Button variant="outline" onClick={() => setStep("pick")}>
                  Pick a different resume
                </Button>
              </div>
            </>
          )}

          {step === "applying" && (
            <p className="text-sm text-slate-500">
              Saving the tailored resume and requesting approval…
            </p>
          )}

          {step === "done" && (
            <>
              <p className="text-sm text-slate-700">
                Approval requested for {job.title} at {job.company}. The
                tailored resume is saved to your profile. When you approve
                the request in the Approvals inbox, the tracker moves this
                job to Applied automatically.
              </p>
              <div className="flex flex-wrap gap-2">
                <Button onClick={() => router.push("/approvals")}>
                  Go to Approvals
                </Button>
                <Button variant="outline" onClick={resetAndClose}>
                  Done
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
