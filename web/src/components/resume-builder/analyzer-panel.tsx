// Resume Builder — Analyzer. A scored dashboard computed deterministically
// from the document itself (see analyzeResume in @/lib/resume-builder):
// a match-score gauge plus countable issue rows grouped by category
// (Structure / Measurable results / Keyword usage). Nothing here is
// invented — every row counts something actually in the document, and
// the keyword row only appears when a target job description is pasted.
// The tip card names the weakest failing check and points at Co-Pilot.
"use client";

import { useMemo } from "react";
import { AlertTriangle, CheckCircle2, Lightbulb } from "lucide-react";
import { analyzeResume } from "@/lib/resume-builder";
import type { ResumeBuilderState } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

function scoreColor(score: number): string {
  if (score >= 80) return "#059669";
  if (score >= 50) return "#d97706";
  return "#e11d48";
}

function ScoreGauge({ score }: { score: number }) {
  const radius = 52;
  const circumference = 2 * Math.PI * radius;
  const filled = (score / 100) * circumference;
  return (
    <div className="relative h-32 w-32 shrink-0" role="img" aria-label={`Resume score ${score} out of 100`}>
      <svg viewBox="0 0 128 128" className="h-32 w-32 -rotate-90">
        <circle cx="64" cy="64" r={radius} fill="none" stroke="#e2e8f0" strokeWidth="10" />
        <circle
          cx="64"
          cy="64"
          r={radius}
          fill="none"
          stroke={scoreColor(score)}
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={`${filled} ${circumference}`}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-3xl font-bold text-slate-900">{score}</span>
        <span className="text-xs font-medium text-slate-500">/ 100</span>
      </div>
    </div>
  );
}

export function AnalyzerPanel({
  state,
  targetJd,
  onTargetJdChange,
}: {
  state: ResumeBuilderState;
  targetJd: string;
  onTargetJdChange: (value: string) => void;
}) {
  const analysis = useMemo(
    () => analyzeResume(state, targetJd.trim() || undefined),
    [state, targetJd],
  );
  const categories = useMemo(() => {
    const groups = new Map<string, typeof analysis.checks>();
    for (const check of analysis.checks) {
      const list = groups.get(check.category) ?? [];
      list.push(check);
      groups.set(check.category, list);
    }
    return [...groups.entries()];
  }, [analysis]);

  return (
    <div className="flex flex-col gap-4">
      <Card className="rounded-2xl shadow-sm">
        <CardContent className="flex items-center gap-5 pt-5">
          <ScoreGauge score={analysis.score} />
          <div className="min-w-0">
            <p className="text-lg font-bold text-slate-900">
              {analysis.score >= 80
                ? "Strong resume"
                : analysis.score >= 50
                  ? "Good start — a few gaps"
                  : "Needs work"}
            </p>
            <p className="mt-1 text-sm text-slate-500">
              {analysis.checks.filter((c) => c.passed).length} of{" "}
              {analysis.checks.length} checks pass. Every row below counts
              something actually in your document.
            </p>
          </div>
        </CardContent>
      </Card>

      {categories.map(([category, checks]) => (
        <Card key={category} className="rounded-2xl shadow-sm">
          <CardHeader>
            <CardTitle>{category}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {checks.map((check) => (
              <div
                key={check.id}
                className="flex items-start gap-3 rounded-lg border border-slate-100 px-3 py-2.5"
              >
                {check.passed ? (
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" aria-hidden />
                ) : (
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" aria-hidden />
                )}
                <div className="min-w-0">
                  <p className="text-sm font-medium text-slate-900">{check.label}</p>
                  <p className={cn("text-sm", check.passed ? "text-slate-500" : "text-amber-700")}>
                    {check.detail}
                  </p>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      ))}

      {targetJd.trim() === "" && (
        <Card className="rounded-2xl shadow-sm">
          <CardContent className="flex flex-col gap-2 pt-5">
            <Label htmlFor="analyzer-target-jd">Target job description</Label>
            <Textarea
              id="analyzer-target-jd"
              rows={3}
              placeholder="Paste the job description you are targeting to check keyword coverage."
              value={targetJd}
              onChange={(e) => onTargetJdChange(e.target.value)}
            />
            <p className="text-xs text-slate-500">
              Keyword usage only appears once a job description is pasted —
              it is counted from that text, never guessed.
            </p>
          </CardContent>
        </Card>
      )}

      {analysis.weakest && (
        <Card className="rounded-2xl border-amber-200 bg-amber-50/60 shadow-sm">
          <CardContent className="flex gap-3 pt-5">
            <Lightbulb className="h-5 w-5 shrink-0 text-amber-600" aria-hidden />
            <div>
              <p className="text-sm font-semibold text-slate-900">Tip — fix this first</p>
              <p className="mt-1 text-sm text-slate-700">
                {analysis.weakest.label}: {analysis.weakest.detail}. Use
                “Improve with AI” on that section in the Content tab and
                Co-Pilot will suggest a stronger version.
              </p>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
