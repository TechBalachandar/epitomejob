"use client";

import { useState } from "react";
import { ApiError, useApplyJob, useMatches, useSaveJob } from "@/lib/api";
import { matchSourceLabel } from "@/lib/jobs";
import { toast } from "@/lib/toast-store";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { CheckCircle2, CircleAlert, Save } from "lucide-react";

export default function MatchesPage() {
  const { data: matches, isLoading, isError } = useMatches();
  const sorted = [...(matches ?? [])].sort((a, b) => b.score - a.score);
  const applyJob = useApplyJob();
  const saveJob = useSaveJob();
  const [saved, setSaved] = useState<Set<string>>(new Set());
  const [applied, setApplied] = useState<Set<string>>(new Set());

  async function handleSave(jobId: string, title: string) {
    try {
      await saveJob.mutateAsync({ jobId });
      setSaved((s) => new Set(s).add(jobId));
      toast({ title: "Saved", description: `${title} added to your tracker.`, variant: "success" });
    } catch (err) {
      toast({ title: "Could not save", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  async function handleApply(jobId: string) {
    try {
      await applyJob.mutateAsync(jobId);
      setApplied((s) => new Set(s).add(jobId));
      toast({
        title: "Approval requested",
        description: "Your application is waiting in the Approvals inbox.",
        variant: "success",
      });
    } catch (err) {
      toast({
        title: "Could not request approval",
        description: err instanceof ApiError ? err.message : "Try again.",
        variant: "error",
      });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Matches</h1>
        <p className="mt-1 text-sm text-slate-500">
          Applying never sends anything directly — it creates a pending
          approval for you to confirm.
        </p>
      </div>

      {isLoading && <p className="text-sm text-slate-500">Loading matches…</p>}
      {isError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load matches. Make sure you are signed in and the backend is running.
        </p>
      )}
      {!isLoading && sorted.length === 0 && (
        <p className="text-sm text-slate-500">
          No matches yet. Upload a resume or complete your profile to get matched.
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {sorted.map((match) => {
          const job = match.job;
          const isSaved = saved.has(job.id);
          const isApplied = applied.has(job.id);
          return (
            <Card key={job.id}>
              <CardHeader>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle>{job.title}</CardTitle>
                    <CardDescription className="mt-1">
                      {job.company} · {job.location} · {job.workType}
                    </CardDescription>
                  </div>
                  <span className="flex shrink-0 flex-col items-center rounded-lg bg-indigo-50 px-3 py-2">
                    <span className="text-xl font-bold text-indigo-700">{match.score}%</span>
                    <span className="text-[11px] font-medium text-indigo-600">match</span>
                  </span>
                </div>
                <Badge variant="secondary" className="mt-2 w-fit">
                  {matchSourceLabel(match.source)}
                </Badge>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <div>
                  <p className="mb-1.5 flex items-center gap-1.5 text-sm font-medium text-slate-900">
                    <CheckCircle2 className="h-4 w-4 text-emerald-600" aria-hidden />
                    Why this matches
                  </p>
                  <ul className="flex flex-col gap-1 pl-5">
                    {match.reasons.map((r) => (
                      <li key={r.label} className="list-disc text-sm text-slate-600">
                        {r.label} <span className="font-medium text-slate-900">(+{r.points})</span>
                      </li>
                    ))}
                  </ul>
                </div>
                {match.gaps.length > 0 && (
                  <div>
                    <p className="mb-1.5 flex items-center gap-1.5 text-sm font-medium text-slate-900">
                      <CircleAlert className="h-4 w-4 text-amber-600" aria-hidden />
                      Gaps to close
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {match.gaps.map((g) => (
                        <Badge key={g} variant="warning">{g}</Badge>
                      ))}
                    </div>
                  </div>
                )}
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    disabled={isSaved || saveJob.isPending}
                    onClick={() => void handleSave(job.id, job.title)}
                  >
                    <Save className="h-4 w-4" aria-hidden />
                    {isSaved ? "Saved" : "Save"}
                  </Button>
                  <Button
                    disabled={isApplied || applyJob.isPending}
                    onClick={() => void handleApply(job.id)}
                  >
                    {isApplied ? "Approval pending" : "Apply"}
                  </Button>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
