// ATS parse preview — "How an ATS reads this" for one uploaded resume.
// Fetches the REAL parsed text from GET /api/resumes/{id}/text (the
// backend's PDFBox / POI extraction) and renders it exactly as the
// parser produced it, with flags for what the parser could not read
// cleanly (see lib/ats-preview.ts). No ATS is simulated and no scores
// are shown: the backend exposes no per-field confidence.
"use client";

import { useState } from "react";
import { useAuthStore } from "@/lib/auth-store";
import { refreshAccessToken } from "@/lib/api";
import {
  ATS_NO_CONFIDENCE_NOTE,
  analyzeParsedText,
  normalizeResumeText,
} from "@/lib/ats-preview";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

/**
 * Fetch the parsed text through the BFF. lib/api.ts owns the shared
 * apiFetch, but it exposes no resume-text call and is outside this
 * feature's file ownership, so the call lives here with the same
 * behaviour: Bearer token + one refresh-and-retry on 401.
 */
async function fetchResumeText(id: string): Promise<string | null> {
  const doFetch = async (): Promise<Response> => {
    const token = useAuthStore.getState().accessToken;
    const headers = new Headers();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`/api/resumes/${encodeURIComponent(id)}/text`, {
      headers,
      credentials: "same-origin",
    });
  };
  let res = await doFetch();
  if (res.status === 401) {
    const ok = await refreshAccessToken();
    if (ok) res = await doFetch();
    else useAuthStore.getState().logout();
  }
  if (!res.ok) {
    let message = `Could not load the parsed text (${res.status})`;
    try {
      const body = (await res.json()) as { message?: string; error?: string };
      message = body.message ?? body.error ?? message;
    } catch {
      // keep the generic message when the body is not JSON
    }
    throw new Error(message);
  }
  const parsed = normalizeResumeText(await res.json());
  return parsed?.text ?? null;
}

export function AtsPreview({ resumeId, label }: { resumeId: string; label: string }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState<string | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleToggle() {
    if (open) {
      setOpen(false);
      return;
    }
    setOpen(true);
    if (text !== undefined) return;
    setLoading(true);
    setError(null);
    try {
      setText(await fetchResumeText(resumeId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the parsed text.");
    } finally {
      setLoading(false);
    }
  }

  // `undefined` = not fetched yet (no analysis). `null` = the backend
  // returned no parsed text — analyse it as such (zero counts, the
  // single no-text flag) instead of rendering an empty card: empty
  // string already renders that way, and null must match it.
  const analysis = text === undefined ? null : analyzeParsedText(text);

  return (
    <div className="mt-2 w-full">
      <Button
        size="sm"
        variant="outline"
        aria-expanded={open}
        onClick={() => void handleToggle()}
      >
        {open ? "Hide ATS view" : "How an ATS reads this"}
      </Button>

      {open && (
        <Card className="mt-3">
          <CardHeader>
            <CardTitle className="text-base">
              How an ATS reads “{label}”
            </CardTitle>
            <p className="text-xs text-slate-500">
              The raw text below is exactly what the parser extracted from
              your file — this is what an applicant tracking system has to
              work with. {ATS_NO_CONFIDENCE_NOTE}
            </p>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {loading && <p className="text-sm text-slate-500">Loading parsed text…</p>}
            {error && (
              <p role="alert" className="text-sm text-rose-600">
                {error}
              </p>
            )}
            {analysis && (
              <>
                <div className="flex flex-wrap gap-2 text-xs text-slate-600">
                  <span>{analysis.characters.toLocaleString()} characters</span>
                  <span>·</span>
                  <span>{analysis.words.toLocaleString()} words</span>
                  <span>·</span>
                  <span>{analysis.lines.toLocaleString()} lines</span>
                </div>

                <div className="flex flex-col gap-1.5">
                  <h3 className="text-sm font-semibold text-slate-900">
                    Contact fields found in the parsed text
                  </h3>
                  <ul className="flex flex-wrap gap-2">
                    <li>
                      <Badge variant={analysis.hasEmail ? "success" : "destructive"}>
                        Email {analysis.hasEmail ? "found" : "not found"}
                      </Badge>
                    </li>
                    <li>
                      <Badge variant={analysis.hasPhone ? "success" : "destructive"}>
                        Phone {analysis.hasPhone ? "found" : "not found"}
                      </Badge>
                    </li>
                    <li>
                      <Badge variant={analysis.hasLinkedin ? "success" : "secondary"}>
                        LinkedIn {analysis.hasLinkedin ? "found" : "not found"}
                      </Badge>
                    </li>
                  </ul>
                </div>

                <div className="flex flex-col gap-1.5">
                  <h3 className="text-sm font-semibold text-slate-900">Sections</h3>
                  <ul className="flex flex-col gap-1 text-sm">
                    {analysis.sections.map((section) => (
                      <li key={section.name} className="flex flex-wrap items-center gap-2">
                        <Badge
                          variant={
                            section.status === "present"
                              ? "success"
                              : section.status === "empty"
                                ? "warning"
                                : "destructive"
                          }
                        >
                          {section.status}
                        </Badge>
                        <span className="font-medium text-slate-900">{section.name}</span>
                        <span className="text-slate-500">{section.detail}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                {analysis.flags.length > 0 ? (
                  <div className="flex flex-col gap-1.5">
                    <h3 className="text-sm font-semibold text-slate-900">
                      What the parser could not read cleanly
                    </h3>
                    <ul className="flex list-disc flex-col gap-1 pl-5 text-sm text-slate-700">
                      {analysis.flags.map((flag) => (
                        <li key={flag.id}>{flag.message}</li>
                      ))}
                    </ul>
                  </div>
                ) : (
                  <p className="text-sm text-slate-600">
                    Nothing flagged: an email and phone number were found and
                    every section has content in the parsed text.
                  </p>
                )}

                <div className="flex flex-col gap-1.5">
                  <h3 className="text-sm font-semibold text-slate-900">
                    Raw parsed text
                  </h3>
                  {analysis.hasText ? (
                    <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-xs text-slate-800">
                      {text}
                    </pre>
                  ) : (
                    <p className="text-sm text-slate-500">
                      The parser returned no text for this file.
                    </p>
                  )}
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
