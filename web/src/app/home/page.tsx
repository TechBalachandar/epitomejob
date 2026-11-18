// Home dashboard — the post-login home (`/home`).
//
// The clock is front and center, then today's plan, this week's
// applications, the pipeline funnel, sponsor jobs posted today, one
// interview question and one resume tip for today, and the daily
// check-in with the streak. Every section is derived from the user's
// real data (profile, tracker, live jobs feed, matches, builder
// documents, interview question bank, analyzer checks) plus the
// calendar date — a returning visitor sees something new each day
// because the date, the feed, and the tracker change, not because
// anything is hardcoded.
"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useQueries } from "@tanstack/react-query";
import {
  fetchMatches,
  useBuilderDocuments,
  useInterviewQuestions,
  useInterviewSessions,
  useJobs,
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
import { buildDailyPlan } from "@/lib/daily-plan";
import {
  activeTrackerCount,
  deriveNextSteps,
  deriveRecentActivity,
  formatActivityDate,
  topMatch,
} from "@/lib/dashboard";
import {
  applicationsThisWeek,
  checkedInToday,
  computeStreak,
  loadCheckinDates,
  newSponsorJobsToday,
  pipelineFunnel,
  questionOfTheDay,
  recordCheckin,
  resumeTipOfTheDay,
  WEEKLY_APPLICATION_GOAL,
} from "@/lib/home-daily";
import { analyzeResume } from "@/lib/resume-builder";
import { loadSeenJobIds, markJobsSeen } from "@/lib/sponsor-alerts";
import { CompletenessMeter } from "@/components/completeness-meter";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import {
  ArrowRight,
  Briefcase,
  CalendarCheck,
  Check,
  FileText,
  Flame,
  Hourglass,
  Sparkles,
} from "lucide-react";

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

function HomePageContent() {
  const { data: profile, isLoading: profileLoading } = useProfile();
  const { data: resumes, isLoading: resumesLoading } = useResumes();
  const { selectedResumeId, setSelectedResumeId } = useSelectedResumeId(resumes);
  const { data: matches, isLoading: matchesLoading } = useMatches(selectedResumeId);
  const { data: tracker, isLoading: trackerLoading } = useTracker();
  const { data: approvals, isLoading: approvalsLoading } = usePendingApprovals();
  const { data: sessions, isLoading: sessionsLoading } = useInterviewSessions();
  const { data: jobPage } = useJobs({ sponsorsH1b: true, size: 100 });
  const { data: documents } = useBuilderDocuments();

  const jobs = jobPage?.content ?? [];
  const clock = clockStatus(profile?.h1bClockStartDate ?? null);
  const plan = useMemo(
    () =>
      buildDailyPlan({
        profile,
        matches: matches ?? [],
        jobs,
        documents: documents ?? [],
      }),
    [profile, matches, jobs, documents],
  );

  // Daily check-in / streak: recorded in localStorage on this device.
  const [checkins, setCheckins] = useState<string[]>([]);
  useEffect(() => {
    setCheckins(loadCheckinDates());
  }, []);
  const streak = computeStreak(checkins);
  const didCheckIn = checkedInToday(checkins);
  function handleCheckin() {
    setCheckins(recordCheckin());
  }

  // New sponsor jobs today: posted today, sponsor H-1B, profile match,
  // not yet seen. Marking them seen keeps tomorrow's list genuinely new.
  const [seenIds, setSeenIds] = useState<Set<string>>(new Set());
  useEffect(() => {
    setSeenIds(loadSeenJobIds());
  }, []);
  const newJobs = useMemo(
    () => newSponsorJobsToday(jobs, matches ?? [], profile, seenIds),
    [jobs, matches, profile, seenIds],
  );
  function handleMarkSeen() {
    const next = markJobsSeen(newJobs.map((n) => n.job.id));
    setSeenIds(next);
  }

  // Question of the day: from the question bank for today's interview job.
  const interviewJobId = plan.interviewJob?.id ?? null;
  const { data: questions } = useInterviewQuestions(interviewJobId);
  const dailyQuestion = questionOfTheDay(questions ?? []);

  // Resume tip of the day: the failing Analyzer check for today, from
  // the first builder document with state.
  const tipDoc = (documents ?? []).find((d) => d.state) ?? null;
  const analysis = useMemo(
    () => (tipDoc?.state ? analyzeResume(tipDoc.state) : null),
    [tipDoc],
  );
  const dailyTip = resumeTipOfTheDay(analysis);

  const week = applicationsThisWeek(tracker);
  const funnel = pipelineFunnel(tracker);

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

      {/* Clock hero — front and center, with today's plan. */}
      <Card
        className={cn(
          clock.state === "urgent" && "border-amber-400 bg-amber-50",
          clock.state === "expired" && "border-rose-400 bg-rose-50",
        )}
      >
        <CardContent className="flex flex-col gap-4 pt-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-sm font-semibold uppercase tracking-wide text-slate-500">
                Your 60-day clock
              </p>
              {clock.state === "not-set" && (
                <>
                  <p className="mt-1 text-3xl font-bold text-slate-900">Not set</p>
                  <p className="mt-1 text-sm text-slate-600">
                    Set your H-1B clock start date to start the 60-day countdown.
                  </p>
                </>
              )}
              {clock.state !== "not-set" && clock.daysLeft !== null && clock.deadline && (
                <>
                  <p className="mt-1 text-3xl font-bold text-slate-900">
                    {clock.daysLeft}{" "}
                    <span className="text-base font-medium text-slate-500">
                      {clock.state === "expired"
                        ? "days past deadline"
                        : clock.daysLeft === 0
                          ? "day left — last day"
                          : "days left"}
                    </span>
                  </p>
                  <p className="mt-1 text-sm text-slate-600">
                    Deadline {formatDeadline(clock.deadline)}
                  </p>
                </>
              )}
            </div>
            <div className="flex flex-col items-start gap-2 sm:items-end">
              <Link
                href="/clock"
                className={cn(buttonVariants({ variant: "outline" }), "w-fit")}
              >
                View your 60-day plan <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
              {clock.state === "not-set" && (
                <Link
                  href="/profile"
                  className={cn(buttonVariants({ variant: "outline" }), "w-fit")}
                >
                  Set your clock start date <ArrowRight className="h-4 w-4" aria-hidden />
                </Link>
              )}
            </div>
          </div>

          {plan.today.length > 0 && (
            <div>
              <p className="text-sm font-semibold text-slate-900">Today&apos;s plan</p>
              <ul className="mt-2 flex flex-col gap-2">
                {plan.today.map((item) => (
                  <li key={`${item.kind}-${item.jobId ?? item.documentId ?? item.title}`}>
                    <Link
                      href={item.href}
                      className="flex items-start gap-2 text-sm text-slate-700 hover:text-indigo-700"
                    >
                      <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-indigo-600" aria-hidden />
                      <span>
                        <span className="font-medium">{item.title}</span>
                        <span className="block text-xs text-slate-500">{item.detail}</span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Daily check-in + streak. */}
      <Card>
        <CardContent className="flex flex-wrap items-center justify-between gap-4 pt-6">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-amber-600">
              <Flame className="h-5 w-5" aria-hidden />
            </span>
            <div>
              <p className="text-sm font-semibold text-slate-900">
                {streak.count > 0
                  ? `${streak.count}-day streak`
                  : "Start your streak"}
              </p>
              <p className="text-xs text-slate-500">
                {didCheckIn
                  ? "Checked in today — come back tomorrow to keep it going."
                  : streak.alive
                    ? "Check in today to keep your streak alive."
                    : "Check in each day you apply — the streak counts consecutive days."}
              </p>
              <p className="text-xs text-slate-400">Streak saved on this device.</p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleCheckin}
            disabled={didCheckIn}
            className={cn(
              buttonVariants({ variant: didCheckIn ? "outline" : "default" }),
              "w-fit",
            )}
          >
            {didCheckIn ? (
              <>
                <Check className="h-4 w-4" aria-hidden /> Checked in today
              </>
            ) : (
              "Did you apply today? Check in"
            )}
          </button>
        </CardContent>
      </Card>

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

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Applications this week + pipeline funnel. */}
        <Card>
          <CardHeader>
            <CardTitle>Job applications</CardTitle>
            <CardDescription>
              This week (Mon–Sun) · goal {WEEKLY_APPLICATION_GOAL} applications ·
              counts applications still in progress (applied, interviewing, or
              offer — rejected ones drop out)
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {trackerLoading ? (
              <p className="text-sm text-slate-500">Loading applications…</p>
            ) : (
              <>
                <div className="flex items-baseline gap-2">
                  <span className="text-3xl font-bold text-slate-900">{week.count}</span>
                  <span className="text-sm text-slate-500">
                    of {week.goal} this week
                    {week.count >= week.goal ? " — goal met" : ` — ${week.goal - week.count} to go`}
                  </span>
                </div>
                <div
                  className="h-2 w-full overflow-hidden rounded-full bg-slate-200"
                  role="progressbar"
                  aria-valuenow={Math.min(week.count, week.goal)}
                  aria-valuemin={0}
                  aria-valuemax={week.goal}
                  aria-label="Applications this week against the weekly goal"
                >
                  <span
                    className="block h-full rounded-full bg-indigo-600"
                    style={{ width: `${Math.min((week.count / week.goal) * 100, 100)}%` }}
                  />
                </div>
                {funnel.total === 0 ? (
                  <p className="text-sm text-slate-600">
                    No applications yet — save a job to start your pipeline.
                  </p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {(
                      [
                        ["Bookmarked", funnel.saved, null],
                        ["Applied", funnel.reachedApplied, funnel.appliedRate],
                        ["Interviewing", funnel.reachedInterview, funnel.interviewRate],
                        ["Offer", funnel.reachedOffer, funnel.offerRate],
                      ] as const
                    ).map(([label, count, rate]) => (
                      <li key={label} className="flex items-center justify-between gap-2 text-sm">
                        <span className="text-slate-700">{label}</span>
                        <span className="flex items-center gap-2">
                          <span className="h-2 w-32 overflow-hidden rounded-full bg-slate-200">
                            <span
                              className="block h-full rounded-full bg-indigo-600"
                              style={{
                                width: `${funnel.total > 0 ? (count / funnel.total) * 100 : 0}%`,
                              }}
                            />
                          </span>
                          <span className="w-6 text-right font-semibold text-slate-900">{count}</span>
                          {rate !== null && (
                            <span className="w-12 text-right text-xs text-slate-500">
                              {Math.round(rate * 100)}%
                            </span>
                          )}
                        </span>
                      </li>
                    ))}
                    {funnel.rejected > 0 && (
                      <li className="flex items-center justify-between gap-2 text-sm">
                        <span className="text-slate-700">Rejected</span>
                        <span className="font-semibold text-slate-900">{funnel.rejected}</span>
                      </li>
                    )}
                  </ul>
                )}
                <Link href="/tracker" className="text-sm font-medium text-indigo-600 hover:underline">
                  Open your tracker <ArrowRight className="inline h-4 w-4" aria-hidden />
                </Link>
              </>
            )}
          </CardContent>
        </Card>

        {/* New sponsor jobs today. */}
        <Card>
          <CardHeader>
            <CardTitle>New sponsor jobs today</CardTitle>
            <CardDescription>
              Posted today, sponsors H-1B, and matches your profile
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {newJobs.length === 0 ? (
              <p className="text-sm text-slate-600">
                No new sponsor jobs today yet — check back tomorrow, or browse
                the full feed.
              </p>
            ) : (
              <>
                <ul className="flex flex-col gap-3">
                  {newJobs.map(({ job, score }) => (
                    <li key={job.id} className="flex flex-col gap-1 border-b border-slate-100 pb-3 last:border-0 last:pb-0">
                      <Link href="/jobs" className="text-sm font-medium text-slate-900 hover:text-indigo-700">
                        {job.title} · {job.company}
                      </Link>
                      <span className="text-xs text-slate-500">
                        {job.location} · {score !== null ? `Match ${score}%` : "Sponsors H-1B"}
                      </span>
                      <JobSource job={job} />
                    </li>
                  ))}
                </ul>
                <button
                  type="button"
                  onClick={handleMarkSeen}
                  className="w-fit text-sm font-medium text-indigo-600 hover:underline"
                >
                  Mark these as seen
                </button>
              </>
            )}
            <Link href="/jobs" className="text-sm font-medium text-indigo-600 hover:underline">
              Browse all sponsor jobs <ArrowRight className="inline h-4 w-4" aria-hidden />
            </Link>
          </CardContent>
        </Card>
      </div>

      {/* Daily learning slot. */}
      <Card>
        <CardHeader>
          <CardTitle>Learn today</CardTitle>
          <CardDescription>
            One interview question and one resume tip, new every day
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <div className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Question of the day
            </p>
            {dailyQuestion ? (
              <>
                <p className="text-sm font-medium text-slate-900">{dailyQuestion.text}</p>
                <p className="text-xs text-slate-500">
                  {dailyQuestion.category}
                  {plan.interviewJob
                    ? ` · for ${plan.interviewJob.title} at ${plan.interviewJob.company}`
                    : ""}
                </p>
                <Link href="/interviews" className="text-sm font-medium text-indigo-600 hover:underline">
                  Practice this question <ArrowRight className="inline h-4 w-4" aria-hidden />
                </Link>
              </>
            ) : (
              <p className="text-sm text-slate-600">
                No question today — pick a job to practice from, or complete
                your profile so we can match one.
              </p>
            )}
          </div>
          <div className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Resume tip of the day
            </p>
            {dailyTip ? (
              <>
                <p className="text-sm font-medium text-slate-900">{dailyTip.label}</p>
                <p className="text-sm text-slate-600">{dailyTip.detail}</p>
                <Link
                  href="/resume-builder"
                  className="text-sm font-medium text-indigo-600 hover:underline"
                >
                  Fix it in the Resume Builder <ArrowRight className="inline h-4 w-4" aria-hidden />
                </Link>
              </>
            ) : (
              <p className="text-sm text-slate-600">
                Your resume passes every check we run — nice work. Add a job
                description to get keyword tips.
              </p>
            )}
          </div>
        </CardContent>
      </Card>

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
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
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
                    <JobSource job={match.job} />
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

// useSearchParams() needs a Suspense boundary so the page still
// prerenders (Next.js CSR bailout rule for search params).
export default function HomePage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Loading home…</p>}>
      <HomePageContent />
    </Suspense>
  );
}
