// Resume Builder — Job Matcher. "Compare a Job Description to Your
// Resume": paste a job description (or pick one of your jobs) and the
// match is computed deterministically from the document and that text
// (see matchJobDescription in @/lib/resume-builder) — a keyword-match
// gauge plus a skills table (Hard / Soft, matched / missing). Nothing is
// invented: with no job description there is no score, only the prompt
// to paste one.
"use client";

import { useEffect, useMemo, useState } from "react";
import { CheckCircle2, Search, XCircle } from "lucide-react";
import { matchJobDescription } from "@/lib/resume-builder";
import type { ResumeBuilderState } from "@/lib/types";
import { useJobs } from "@/lib/api";
import type { Job } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

function scoreColor(score: number): string {
  if (score >= 80) return "#059669";
  if (score >= 50) return "#d97706";
  return "#e11d48";
}

/** Semicircular arc gauge, Teal's Job Matcher style. */
function MatchGauge({ score }: { score: number }) {
  const ARC = Math.PI * 80; // semicircle, r = 80
  const filled = (score / 100) * ARC;
  return (
    <div className="flex flex-col items-center" role="img" aria-label={`Match score ${score} percent`}>
      <svg viewBox="0 0 200 115" className="h-28 w-48">
        <path
          d="M 20 100 A 80 80 0 0 1 180 100"
          fill="none"
          stroke="#e2e8f0"
          strokeWidth="12"
          strokeLinecap="round"
        />
        <path
          d="M 20 100 A 80 80 0 0 1 180 100"
          fill="none"
          stroke={scoreColor(score)}
          strokeWidth="12"
          strokeLinecap="round"
          strokeDasharray={`${filled} ${ARC}`}
        />
        <text x="100" y="88" textAnchor="middle" fontSize="30" fontWeight="700" fill="#0f172a">
          {score}%
        </text>
        <text x="100" y="108" textAnchor="middle" fontSize="11" fontWeight="500" fill="#64748b">
          Match Score
        </text>
      </svg>
    </div>
  );
}

/** Job description text for a picked job: title, company, summary, skills. */
export function jobDescriptionForJob(job: Job): string {
  return [
    `${job.title} at ${job.company}`,
    job.summary,
    job.skills.length > 0 ? `Skills: ${job.skills.join(", ")}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

export function JobMatcherPanel({
  state,
  targetJd,
  onTargetJdChange,
}: {
  state: ResumeBuilderState;
  targetJd: string;
  onTargetJdChange: (value: string) => void;
}) {
  const [jobQuery, setJobQuery] = useState("");
  // Debounced search: one request per pause in typing, not one per
  // keystroke. The query is disabled entirely until there is text, so
  // opening Job Matcher does not fire a wasted GET /jobs?size=0.
  const [debouncedQuery, setDebouncedQuery] = useState("");
  useEffect(() => {
    const id = window.setTimeout(() => setDebouncedQuery(jobQuery), 300);
    return () => window.clearTimeout(id);
  }, [jobQuery]);
  const { data: jobsPage } = useJobs(
    { query: debouncedQuery.trim(), size: 5 },
    { enabled: debouncedQuery.trim() !== "" },
  );
  const jobs = jobsPage?.content ?? [];
  const match = useMemo(
    () => matchJobDescription(state, targetJd),
    [state, targetJd],
  );
  const hasJd = targetJd.trim().length > 0;

  return (
    <div className="flex flex-col gap-4">
      <Card className="rounded-2xl shadow-sm">
        <CardHeader>
          <CardTitle>Compare a Job Description to Your Resume</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="job-matcher-search">Search titles or companies</Label>
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                aria-hidden
              />
              <Input
                id="job-matcher-search"
                placeholder="Search titles or companies"
                value={jobQuery}
                onChange={(e) => setJobQuery(e.target.value)}
                className="pl-8"
              />
            </div>
            {debouncedQuery.trim() !== "" && jobs.length > 0 && (
              <ul className="mt-1 flex flex-col gap-1 rounded-lg border border-slate-200 p-1">
                {jobs.map((job) => (
                  <li key={job.id}>
                    <button
                      type="button"
                      onClick={() => {
                        onTargetJdChange(jobDescriptionForJob(job));
                        setJobQuery("");
                      }}
                      className="w-full rounded-md px-2 py-1.5 text-left text-sm hover:bg-slate-100"
                    >
                      <span className="font-medium text-slate-900">{job.title}</span>
                      <span className="text-slate-500"> — {job.company}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {debouncedQuery.trim() !== "" && jobs.length === 0 && (
              <p className="text-xs text-slate-500">No jobs match that search.</p>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="job-matcher-jd">Add a Job Description</Label>
            <Textarea
              id="job-matcher-jd"
              rows={6}
              placeholder="Paste the job description here — the match score and skills table update from it."
              value={targetJd}
              onChange={(e) => onTargetJdChange(e.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      {!hasJd && (
        <Card className="rounded-2xl shadow-sm">
          <CardContent className="pt-5 text-sm text-slate-500">
            Paste a job description above (or pick one of your jobs) to see
            your match score and which of its skills your resume covers.
          </CardContent>
        </Card>
      )}

      {hasJd && match.score === null && (
        <Card className="rounded-2xl shadow-sm">
          <CardContent className="pt-5 text-sm text-slate-500">
            No keywords found in that job description — try a longer one.
          </CardContent>
        </Card>
      )}

      {hasJd && match.score !== null && (
        <>
          <Card className="rounded-2xl shadow-sm">
            <CardContent className="flex flex-col items-center gap-1 pt-5">
              <MatchGauge score={match.score} />
              <p className="text-sm text-slate-500">
                {match.matchedKeywords} of {match.totalKeywords} job keywords
                appear in your resume.
              </p>
            </CardContent>
          </Card>

          <Card className="rounded-2xl shadow-sm">
            <CardHeader>
              <CardTitle>
                {match.totalKeywords > match.skills.length
                  ? `Top ${match.skills.length} skills`
                  : "Skills"}
              </CardTitle>
              {match.totalKeywords > match.skills.length && (
                <CardDescription>
                  Showing the top {match.skills.length} of{" "}
                  {match.totalKeywords} job keywords.
                </CardDescription>
              )}
            </CardHeader>
            <CardContent className="flex flex-col gap-1">
              <div className="flex items-center justify-between border-b border-slate-100 pb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <span>Skill</span>
                <span>Status</span>
              </div>
              {match.skills.map((row) => (
                <div
                  key={row.keyword}
                  className="flex items-center justify-between gap-3 border-b border-slate-50 py-2 text-sm last:border-0"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-slate-900">
                      {row.keyword}
                    </span>
                    <span className="block text-xs text-slate-500">{row.type}</span>
                  </span>
                  <span
                    className={cn(
                      "inline-flex shrink-0 items-center gap-1 text-sm font-medium",
                      row.status === "matched" ? "text-emerald-700" : "text-rose-700",
                    )}
                  >
                    {row.status === "matched" ? (
                      <>
                        <CheckCircle2 className="h-4 w-4" aria-hidden />
                        Matched
                      </>
                    ) : (
                      <>
                        <XCircle className="h-4 w-4" aria-hidden />
                        Missing
                      </>
                    )}
                  </span>
                </div>
              ))}
              {match.missingKeywords.length > 0 && (
                <p className="pt-2 text-xs text-slate-500">
                  Missing keywords to add:{" "}
                  {match.missingKeywords.slice(0, 8).join(", ")}
                  {match.missingKeywords.length > 8 ? "…" : ""}
                </p>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
