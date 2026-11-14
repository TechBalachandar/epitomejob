"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useQueries } from "@tanstack/react-query";
import {
  fetchMatches,
  useInterviewSessions,
  useMatches,
  usePendingApprovals,
  useProfile,
  useResumes,
  useTracker,
} from "@/lib/api";
import { matchesHref, useSelectedResumeId } from "@/lib/use-selected-resume";
import { topMatchScoreForResume } from "@/lib/resume-selection";
import { ResumeSelector } from "@/components/resume-selector";
import { JobSource } from "@/components/job-source";
import type { Match, ResumeItem } from "@/lib/types";
import { clockStatus, formatDeadline } from "@/lib/h1b-clock";
import {
  activeTrackerCount,
  deriveNextSteps,
  deriveRecentActivity,
  formatActivityDate,
  topMatch,
} from "@/lib/dashboard";
import { CompletenessMeter } from "@/components/completeness-meter";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { ArrowRight, Briefcase, CalendarCheck, FileText, Hourglass, Sparkles } from "lucide-react";

function greetingFor(now: Date): string {
  const hour = now.getHours();
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

/** W6 per-resume strip: each resume's label + its top match score. */
function PerResumeStrip({ resumes }: { resumes: ResumeItem[] }) {
  const ready = resumes.filter((r) => r.status === "READY");
  const queries = useQueries({
    queries: ready.map((r) => ({
      queryKey: ["matches", "me", r.id],
      queryFn: () => fetchMatches(r.id),
    })),
  });
  if (resumes.length === 0) return null;
  return (
    <ul className="flex flex-col gap-2">
      {resumes.map((r) => {
        if (r.status !== "READY") {
          return (
            <li key={r.id} className="flex items-baseline justify-between gap-2 text-sm">
              <span className="min-w-0 truncate font-medium text-slate-900">{r.label}</span>
              <span className="shrink-0 text-xs text-slate-400">Not ready</span>
            </li>
          );
        }
        const q = queries[ready.findIndex((x) => x.id === r.id)];
        const top = topMatchScoreForResume(q?.data);
        return (
          <li key={r.id} className="flex items-baseline justify-between gap-2 text-sm">
            <span className="min-w-0 truncate font-medium text-slate-900">{r.label}</span>
            <span className="shrink-0 text-slate-600">
              {q?.isLoading ? "…" : top !== null ? `Top match ${top}%` : "No matches yet"}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

function DashboardPageContent() {
  const { data: profile, isLoading: profileLoading } = useProfile();
  const { data: resumes, isLoading: resumesLoading } = useResumes();
  const { selectedResumeId, setSelectedResumeId } = useSelectedResumeId(resumes);
  const { data: matches, isLoading: matchesLoading } = useMatches(selectedResumeId);
  const { data: tracker, isLoading: trackerLoading } = useTracker();
  const { data: approvals, isLoading: approvalsLoading } = usePendingApprovals();
  const { data: sessions, isLoading: sessionsLoading } = useInterviewSessions();

  const top = topMatch(matches);
  // W6: the "review your top match" next step keeps the selected resume
  // in its link, like the Top matches card does.
  const nextSteps = deriveNextSteps({
    resumes,
    profile,
    pendingApprovals: approvals,
    matches,
  }).map((step) =>
    step.href === "/matches"
      ? { ...step, href: matchesHref(selectedResumeId) }
      : step,
  );
  const activity = deriveRecentActivity({
    tracker,
    approvals,
    resumes,
  });
  const clock = clockStatus(profile?.h1bClockStartDate ?? null);
  const topMatches = [...(matches ?? [])]
    .sort((a, b) => b.score - a.score)
    .slice(0, 3);

  const stats = [
    {
      label: "Resumes",
      value: resumesLoading ? null : String(resumes?.length ?? 0),
      sub: null as string | null,
      icon: FileText,
      href: "/resumes",
    },
    {
      label: "Matches",
      value: matchesLoading ? null : String(matches?.length ?? 0),
      sub: top ? `Top score ${top.score}%` : null,
      icon: Sparkles,
      href: "/matches",
    },
    {
      label: "Active applications",
      value: trackerLoading ? null : String(activeTrackerCount(tracker)),
      sub: null,
      icon: Briefcase,
      href: "/tracker",
    },
    {
      label: "Pending approvals",
      value: approvalsLoading ? null : String(approvals?.length ?? 0),
      sub: null,
      icon: Hourglass,
      href: "/approvals",
    },
    {
      label: "Interview sessions",
      value: sessionsLoading ? null : String(sessions?.length ?? 0),
      sub: null,
      icon: CalendarCheck,
      href: "/interviews",
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">{greetingFor(new Date())}</h1>
        <p className="mt-1 text-sm text-slate-500">
          Here is where your job search stands today.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        {stats.map(({ label, value, sub, icon: Icon, href }) => (
          <Link key={label} href={href}>
            <Card className="transition-shadow hover:shadow-md">
              <CardContent className="flex items-center gap-3 pt-5">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
                  <Icon className="h-5 w-5" aria-hidden />
                </span>
                <span className="min-w-0">
                  <span className="block text-xl font-bold text-slate-900">
                    {value ?? "—"}
                  </span>
                  <span className="block text-xs text-slate-500">{label}</span>
                  {sub && (
                    <span className="block truncate text-xs text-slate-400">{sub}</span>
                  )}
                </span>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Your profile</CardTitle>
            <CardDescription>
              Complete profiles get matched more accurately.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {profileLoading ? (
              <p className="text-sm text-slate-500">Loading profile…</p>
            ) : profile ? (
              <>
                <CompletenessMeter value={profile.completeness} />
                <p className="text-sm text-slate-600">
                  {profile.headline || "Add a headline so matching knows what you do."}
                </p>
              </>
            ) : (
              <p className="text-sm text-slate-600">
                No profile yet — set one up to start matching.
              </p>
            )}
            <Link
              href="/profile"
              className={cn(buttonVariants({ variant: "outline" }), "w-fit")}
            >
              Edit profile <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          </CardContent>
        </Card>

        <Card
          className={cn(
            clock.state === "urgent" &&
              "border-amber-400 bg-amber-50",
            clock.state === "expired" && "border-rose-400 bg-rose-50",
          )}
        >
          <CardHeader>
            <CardTitle>60-day clock</CardTitle>
            <CardDescription>H-1B grace period tracker</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {clock.state === "not-set" && (
              <>
                <p className="text-3xl font-bold text-slate-900">
                  Not set
                </p>
                <p className="text-sm text-slate-600">
                  Set your H-1B clock start date to start the 60-day
                  countdown.
                </p>
                <Link
                  href="/profile"
                  className={cn(buttonVariants({ variant: "outline" }), "w-fit")}
                >
                  Set your clock start date <ArrowRight className="h-4 w-4" aria-hidden />
                </Link>
              </>
            )}
            {clock.state === "active" && clock.daysLeft !== null && clock.deadline && (
              <>
                <p className="text-3xl font-bold text-slate-900">
                  {clock.daysLeft}{" "}
                  <span className="text-base font-medium text-slate-500">days left</span>
                </p>
                <p className="text-sm text-slate-600">
                  Deadline {formatDeadline(clock.deadline)}
                </p>
                <Badge variant="secondary" className="w-fit">
                  Active
                </Badge>
              </>
            )}
            {clock.state === "urgent" && clock.daysLeft !== null && clock.deadline && (
              <>
                <p className="text-3xl font-bold text-amber-800">
                  {clock.daysLeft}{" "}
                  <span className="text-base font-medium text-amber-700">
                    {clock.daysLeft === 0 ? "day left — last day" : "days left"}
                  </span>
                </p>
                <p className="text-sm text-amber-800">
                  Deadline {formatDeadline(clock.deadline)} — your 60-day
                  grace period is almost up.
                </p>
                <Badge className="w-fit bg-amber-600 hover:bg-amber-600">
                  Urgent
                </Badge>
              </>
            )}
            {clock.state === "expired" && clock.deadline && (
              <>
                <p className="text-3xl font-bold text-rose-700">Expired</p>
                <p className="text-sm text-rose-700">
                  Your 60-day clock ended {formatDeadline(clock.deadline)}.
                  Update the start date on your profile if it changed.
                </p>
                <Link
                  href="/profile"
                  className={cn(buttonVariants({ variant: "outline" }), "w-fit")}
                >
                  Update the date <ArrowRight className="h-4 w-4" aria-hidden />
                </Link>
              </>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Next steps</CardTitle>
          </CardHeader>
          <CardContent>
            {nextSteps.length === 0 ? (
              <p className="text-sm text-slate-600">
                You&apos;re all caught up — nothing needs your attention right now.
              </p>
            ) : (
              <ul className="flex flex-col gap-3">
                {nextSteps.map((step) => (
                  <li key={step.text}>
                    <Link
                      href={step.href}
                      className="flex items-start gap-2 text-sm text-slate-700 hover:text-indigo-700"
                    >
                      <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-indigo-600" aria-hidden />
                      {step.text}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Recent activity</CardTitle>
          </CardHeader>
          <CardContent>
            {activity.length === 0 ? (
              <p className="text-sm text-slate-600">
                No activity yet — upload a resume or save a job to get started.
              </p>
            ) : (
              <ul className="flex flex-col gap-3">
                {activity.map((a) => (
                  <li key={a.id} className="flex flex-col gap-0.5 border-b border-slate-100 pb-3 last:border-0 last:pb-0">
                    <span className="text-sm text-slate-700">{a.text}</span>
                    <span className="text-xs text-slate-400">{formatActivityDate(a.at)}</span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Top matches</CardTitle>
          <CardDescription>
            Your best matches right now, by score, for the selected resume.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {(resumes?.length ?? 0) > 0 && (
            <ResumeSelector
              resumes={resumes ?? []}
              value={selectedResumeId}
              onChange={setSelectedResumeId}
            />
          )}
          <PerResumeStrip resumes={resumes ?? []} />
          {topMatches.length === 0 ? (
            <p className="text-sm text-slate-600">
              No matches yet — complete your profile and upload a resume to
              start matching.
            </p>
          ) : (
            <ul className="flex flex-col gap-4">
              {topMatches.map((match) => (
                <li key={match.job.id} className="flex flex-col gap-1.5">
                  <Link href={matchesHref(selectedResumeId)} className="group flex flex-col gap-1.5">
                    <span className="flex items-baseline justify-between gap-2">
                      <span className="text-sm font-medium text-slate-900 group-hover:text-indigo-700">
                        {match.job.title} · {match.job.company}
                      </span>
                      <span className="shrink-0 text-sm font-semibold text-slate-900">
                        {match.score}%
                      </span>
                    </span>
                    <span
                      className="h-2 w-full overflow-hidden rounded-full bg-slate-200"
                      role="progressbar"
                      aria-valuenow={match.score}
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-label={`Match score for ${match.job.title}`}
                    >
                      <span
                        className="block h-full rounded-full bg-indigo-600"
                        style={{ width: `${match.score}%` }}
                      />
                    </span>
                  </Link>
                  {/* Outside the link: an <a> may not nest inside an <a>. */}
                  <JobSource job={match.job} />
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

// useSearchParams() needs a Suspense boundary so the page still
// prerenders (Next.js CSR bailout rule for search params).
export default function DashboardPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Loading dashboard…</p>}>
      <DashboardPageContent />
    </Suspense>
  );
}
