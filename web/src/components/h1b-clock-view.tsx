// Feature 1: "Your 60 days" — the live countdown plus a day-by-day plan
// computed from the user's real matches, jobs and builder documents
// (see lib/daily-plan.ts). When no clock start date is set, only a setup
// prompt is shown — never a fake countdown.
"use client";

import { useState } from "react";
import Link from "next/link";
import type { Job, Match, Profile, ResumeBuilderDocument } from "@/lib/types";
import { clockStatus, formatDeadline } from "@/lib/h1b-clock";
import { buildDailyPlan, type PlanItem } from "@/lib/daily-plan";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { cn } from "@/lib/utils";

function stateBadge(state: string): { label: string; className: string } {
  if (state === "urgent")
    return { label: "Urgent", className: "bg-amber-600 hover:bg-amber-600" };
  if (state === "expired")
    return { label: "Expired", className: "bg-rose-600 hover:bg-rose-600" };
  return { label: "Active", className: "" };
}

function PlanItemRow({ item }: { item: PlanItem }) {
  const kindLabel =
    item.kind === "apply"
      ? "Apply"
      : item.kind === "tailor"
        ? "Tailor resume"
        : "Mock interview";
  return (
    <li className="flex flex-col gap-0.5 border-b border-slate-100 pb-3 last:border-0 last:pb-0">
      <span className="flex items-center gap-2 text-sm">
        <Badge variant="secondary" className="shrink-0">
          {kindLabel}
        </Badge>
        <Link href={item.href} className="font-medium text-slate-900 hover:text-indigo-700">
          {item.title}
        </Link>
      </span>
      <span className="text-xs text-slate-500">{item.detail}</span>
    </li>
  );
}

export function H1bClockView({
  profile,
  matches = [],
  jobs = [],
  documents = [],
  isLoading = false,
  saving = false,
  onSaveDate,
}: {
  profile: Profile | null | undefined;
  matches?: Match[];
  jobs?: Job[];
  documents?: ResumeBuilderDocument[];
  isLoading?: boolean;
  saving?: boolean;
  onSaveDate: (date: string) => void;
}) {
  const [date, setDate] = useState("");
  const [dateError, setDateError] = useState<string | null>(null);

  const clock = clockStatus(profile?.h1bClockStartDate ?? null);
  const plan = buildDailyPlan({ profile, matches, jobs, documents });

  function saveDate() {
    const todayISO = new Date().toISOString().slice(0, 10);
    if (!date) {
      setDateError("Pick the date your layoff (last working day) was.");
      return;
    }
    if (date > todayISO) {
      setDateError("The start date can't be in the future.");
      return;
    }
    setDateError(null);
    onSaveDate(date);
  }

  if (isLoading) {
    return <p className="text-sm text-slate-500">Loading your clock…</p>;
  }

  // No start date: setup prompt only. No countdown is shown.
  if (clock.state === "not-set") {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Set your 60-day clock</CardTitle>
          <CardDescription>
            Your H-1B grace period is 60 days from your last working day.
            Set that date and we will count down from it — and build your
            day-by-day plan from your real jobs and resumes.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="clock-start-date">Layoff date (last working day)</Label>
            <Input
              id="clock-start-date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
            {dateError && (
              <p role="alert" className="text-xs text-rose-600">
                {dateError}
              </p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button onClick={saveDate} disabled={saving}>
              {saving ? "Saving…" : "Start my 60-day clock"}
            </Button>
            <Link href="/profile" className="text-sm text-indigo-700 hover:underline">
              Or set it on your profile
            </Link>
          </div>
        </CardContent>
      </Card>
    );
  }

  const badge = stateBadge(clock.state);
  return (
    <div className="flex flex-col gap-4">
      <Card
        className={cn(
          clock.state === "urgent" && "border-amber-400 bg-amber-50",
          clock.state === "expired" && "border-rose-400 bg-rose-50",
        )}
      >
        <CardHeader>
          <CardTitle>Your 60 days</CardTitle>
          <CardDescription>
            H-1B grace period — started {profile?.h1bClockStartDate}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {clock.daysLeft !== null && clock.daysLeft >= 0 && (
            <p className="text-3xl font-bold text-slate-900">
              {clock.daysLeft}{" "}
              <span className="text-base font-medium text-slate-500">
                {clock.daysLeft === 0
                  ? "day left — last day"
                  : clock.daysLeft === 1
                    ? "day left"
                    : "days left"}
              </span>
            </p>
          )}
          {clock.state === "expired" && (
            <p className="text-3xl font-bold text-rose-700">Expired</p>
          )}
          {clock.deadline && (
            <p className="text-sm text-slate-600">
              Deadline {formatDeadline(clock.deadline)}
              {clock.state === "expired" && " — your grace period has ended."}
              {clock.state === "urgent" &&
                " — your grace period is almost up."}
            </p>
          )}
          <Badge className={cn("w-fit", badge.className)}>{badge.label}</Badge>
          <p className="text-sm text-slate-600">
            <Link href="/profile" className="text-indigo-700 hover:underline">
              Update the start date on your profile
            </Link>{" "}
            if it changed.
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Today&apos;s plan</CardTitle>
          <CardDescription>
            Built from your live sponsor matches, your jobs feed and your
            resumes — not a fixed schedule.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {plan.today.length === 0 ? (
            <p className="text-sm text-slate-600">
              Nothing to plan yet — no sponsor matches or resumes to work
              from.{" "}
              <Link href="/jobs" className="text-indigo-700 hover:underline">
                Browse sponsor jobs
              </Link>{" "}
              or{" "}
              <Link href="/resume-builder" className="text-indigo-700 hover:underline">
                build a resume
              </Link>
              .
            </p>
          ) : (
            <ul className="flex flex-col gap-3">
              {plan.today.map((item) => (
                <PlanItemRow key={`${item.kind}-${item.jobId ?? item.documentId ?? item.title}`} item={item} />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {plan.sponsorJobs.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Sponsor jobs to apply to</CardTitle>
            <CardDescription>
              Top sponsor matches first, then sponsor jobs from the live feed
              that overlap your profile.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-3">
              {plan.sponsorJobs.map(({ job, score }) => (
                <li key={job.id} className="flex flex-col gap-0.5 border-b border-slate-100 pb-3 last:border-0 last:pb-0">
                  <Link
                    href={job.sourceUrl ?? "/jobs"}
                    target={job.sourceUrl ? "_blank" : undefined}
                    rel={job.sourceUrl ? "noopener noreferrer" : undefined}
                    className="text-sm font-medium text-slate-900 hover:text-indigo-700"
                  >
                    {job.title} · {job.company}
                  </Link>
                  <span className="text-xs text-slate-500">
                    {job.location}
                    {score !== null ? ` · Backend match ${score}%` : " · Sponsors H-1B"}
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {plan.resumesToTailor.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Resumes to tailor</CardTitle>
            <CardDescription>
              Skills each resume is missing for your best sponsor job.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-3">
              {plan.resumesToTailor.map((t) => (
                <li key={t.document.id ?? t.document.title ?? "doc"} className="flex flex-col gap-0.5 border-b border-slate-100 pb-3 last:border-0 last:pb-0">
                  <Link
                    href={
                      t.document.id
                        ? `/resume-builder?documentId=${encodeURIComponent(t.document.id)}`
                        : "/resume-builder"
                    }
                    className="text-sm font-medium text-slate-900 hover:text-indigo-700"
                  >
                    {t.document.title ?? "Untitled resume"}
                  </Link>
                  <span className="text-xs text-slate-500">
                    For {t.job.title} at {t.job.company} — missing:{" "}
                    {t.missingSkills.join(", ")}
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {plan.interviewJob && (
        <Card>
          <CardHeader>
            <CardTitle>Mock interview</CardTitle>
            <CardDescription>
              Practice for your top sponsor job with the Coach.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-slate-700">
              {plan.interviewJob.title} at {plan.interviewJob.company}
            </p>
            <Link
              href="/interviews"
              className="mt-2 inline-block text-sm font-medium text-indigo-700 hover:underline"
            >
              Open mock interviews →
            </Link>
          </CardContent>
        </Card>
      )}

      {plan.week.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Next 7 days</CardTitle>
            <CardDescription>
              One sponsor application a day, resume tailoring every other
              day, mock interviews twice a week — all from the jobs and
              resumes above.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-4">
              {plan.week.map((day) => (
                <li key={day.dateISO} className="flex flex-col gap-2">
                  <p className="text-sm font-semibold text-slate-900">
                    {day.label}
                  </p>
                  {day.items.length === 0 ? (
                    <p className="text-xs text-slate-500">
                      No sponsor jobs or resumes to plan from yet.
                    </p>
                  ) : (
                    <ul className="flex flex-col gap-3 pl-1">
                      {day.items.map((item) => (
                        <PlanItemRow
                          key={`${day.dateISO}-${item.kind}-${item.jobId ?? item.documentId ?? item.title}`}
                          item={item}
                        />
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
