// Feature 1: "Your 60 days" — the H-1B clock page. Data comes from the
// existing hooks (profile, sponsor jobs feed, matches, builder documents);
// the plan is computed in lib/daily-plan.ts from that real data.
"use client";

import { useBuilderDocuments, useJobs, useMatches, useProfile, useUpdateProfile } from "@/lib/api";
import { H1bClockView } from "@/components/h1b-clock-view";
import { SponsorAlerts } from "@/components/sponsor-alerts";
import { toast } from "@/lib/toast-store";

export default function ClockPage() {
  const { data: profile, isLoading: profileLoading, isError: profileError } = useProfile();
  const { data: jobPage } = useJobs({ sponsorsH1b: true, size: 100 });
  const { data: matches } = useMatches();
  const { data: documents } = useBuilderDocuments();
  const update = useUpdateProfile();

  const jobs = jobPage?.content ?? [];

  async function saveDate(date: string) {
    try {
      await update.mutateAsync({ h1bClockStartDate: date });
      toast({ title: "60-day clock started", variant: "success" });
    } catch {
      toast({
        title: "Could not save the start date",
        description: "Try again.",
        variant: "error",
      });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Your 60 days</h1>
        <p className="mt-1 text-sm text-slate-500">
          Your H-1B grace-period countdown and the day-by-day plan built
          from your real jobs and resumes.
        </p>
      </div>

      {profileError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load your profile. Make sure you are signed in.
        </p>
      )}

      <H1bClockView
        profile={profile}
        matches={matches ?? []}
        jobs={jobs}
        documents={documents ?? []}
        isLoading={profileLoading}
        saving={update.isPending}
        onSaveDate={(date) => void saveDate(date)}
      />

      <SponsorAlerts jobs={jobs} matches={matches ?? []} profile={profile} />
    </div>
  );
}
