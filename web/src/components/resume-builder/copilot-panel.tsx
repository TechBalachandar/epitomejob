// Resume Builder — Co-Pilot panel. Calls POST
// /resume-builder/documents/{id}/copilot with {active_section,
// user_context, target_jd, resume_state} and shows the returned 3–5
// option cards (angle tag + bullets + ATS keywords). Applying one patches
// the block in the page state; the preview updates on the next render.
// A failed call shows a plain message, never a crash. [placeholder] text
// inside an option is highlighted amber.
"use client";

import { useEffect, useState } from "react";
import { ApiError, requestCopilot } from "@/lib/api";
import {
  copilotTargetLabel,
  splitPlaceholders,
} from "@/lib/resume-builder";
import type {
  CopilotOption,
  CopilotTarget,
  ResumeBuilderState,
} from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { X } from "lucide-react";

function HighlightedText({ text }: { text: string }) {
  return (
    <>
      {splitPlaceholders(text).map((part, i) =>
        part.isPlaceholder ? (
          <mark key={i} className="rounded-sm bg-amber-200 px-0.5 text-amber-900">
            {part.text}
          </mark>
        ) : (
          <span key={i}>{part.text}</span>
        ),
      )}
    </>
  );
}

export function CopilotPanel({
  documentId,
  target,
  contextText,
  targetJd,
  state,
  onApply,
  onClose,
}: {
  documentId: string | null;
  target: CopilotTarget;
  contextText: string;
  targetJd: string;
  state: ResumeBuilderState;
  onApply: (option: CopilotOption) => void;
  onClose: () => void;
}) {
  const [options, setOptions] = useState<CopilotOption[] | null>(null);
  // No document yet (fresh build before first save) means no Co-Pilot
  // call — pick a template first. Loading starts true when a call will
  // happen: the parent re-keys this panel per block, so each mount is a
  // fresh call and no in-effect state reset is needed.
  const [error, setError] = useState<string | null>(
    documentId ? null : "Save the document first — pick a template to create it.",
  );
  const [loading, setLoading] = useState<boolean>(Boolean(documentId));
  const [appliedId, setAppliedId] = useState<string | null>(null);

  // The parent keys this panel by section+index, so a mount = one call
  // for the active block.
  useEffect(() => {
    if (!documentId) return;
    let cancelled = false;
    void requestCopilot(documentId, {
      active_section: target.section,
      user_context: contextText,
      target_jd: targetJd.trim() || undefined,
      resume_state: state,
    })
      .then((res) => {
        if (!cancelled) setOptions(res.options);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(
            err instanceof ApiError
              ? err.message
              : "Co-Pilot is unavailable right now. Try again later.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // Intentionally run once per panel mount (parent re-keys per block).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Card data-testid="copilot-panel" className="border-indigo-200 bg-indigo-50/50">
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <div>
            <CardTitle>Improve {copilotTargetLabel(target)} with AI</CardTitle>
            <CardDescription>
              Pick an option to apply it to this block. [Placeholders] are
              highlighted — fill them in after applying.
            </CardDescription>
          </div>
          <Button variant="ghost" size="icon" aria-label="Close AI suggestions" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {loading && <p className="text-sm text-slate-500">Asking Co-Pilot…</p>}
        {error && (
          <p role="alert" className="text-sm text-rose-600">
            {error}
          </p>
        )}
        {!loading && !error && options?.length === 0 && (
          <p className="text-sm text-slate-500">
            Co-Pilot returned no suggestions for this block.
          </p>
        )}
        {options?.map((option) => (
          <article
            key={option.id ?? option.angle}
            className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-3"
          >
            <div className="flex flex-wrap items-center gap-2">
              <Badge>{option.angle}</Badge>
              {appliedId === option.id && (
                <span className="text-xs font-medium text-emerald-700">Applied</span>
              )}
            </div>
            <ul className="flex flex-col gap-1 pl-5">
              {option.bullets.map((bullet, i) => (
                <li key={i} className="list-disc text-sm text-slate-700">
                  <HighlightedText text={bullet} />
                </li>
              ))}
            </ul>
            {option.atsKeywords.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {option.atsKeywords.map((keyword) => (
                  <Badge key={keyword} variant="secondary">
                    {keyword}
                  </Badge>
                ))}
              </div>
            )}
            <div>
              <Button
                size="sm"
                onClick={() => {
                  onApply(option);
                  setAppliedId(option.id);
                }}
              >
                Apply this option
              </Button>
            </div>
          </article>
        ))}
      </CardContent>
    </Card>
  );
}
