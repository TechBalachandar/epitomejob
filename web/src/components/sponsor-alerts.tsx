// Feature 6: sponsor-job alerts, rendered in-app.
// The rule (shown in the UI) is in lib/sponsor-alerts.ts: a job alerts
// when it sponsors H-1B AND matches the profile (backend match score, or
// skills / location / headline overlap) AND has not been seen before.
// There is no notifications create endpoint (the API only lists, counts
// unread, and marks read), so these alerts live here in the app with
// their own unread count — nothing is added to the notification inbox.
"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import type { Job, Match, Profile } from "@/lib/types";
import {
  SPONSOR_ALERT_RULE,
  findNewSponsorJobs,
  loadSeenJobIds,
  markJobsSeen,
  type SponsorAlert,
} from "@/lib/sponsor-alerts";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";

function alertJobHref(job: Job): string {
  return job.sourceUrl ?? "/jobs";
}

export function SponsorAlerts({
  jobs,
  matches = [],
  profile,
}: {
  jobs: Job[];
  matches?: Match[];
  profile: Profile | null | undefined;
}) {
  // Alerts found on the last scan that have not been dismissed. The
  // seen-set in localStorage decides what counts as "new"; scanning marks
  // everything currently matching as seen, so a later scan only surfaces
  // jobs that appeared since.
  const [alerts, setAlerts] = useState<SponsorAlert[]>(() =>
    findNewSponsorJobs(jobs, profile, matches, loadSeenJobIds()),
  );
  const [lastChecked, setLastChecked] = useState<Date | null>(null);
  const [scanCount, setScanCount] = useState(0);

  const unread = alerts.length;

  // Re-scan against the current feed. `scanCount` is in the deps so the
  // button re-runs the scan even when the feed props are unchanged.
  const scanned = useMemo(
    () => findNewSponsorJobs(jobs, profile, matches, loadSeenJobIds()),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [jobs, matches, profile, scanCount],
  );

  // Derive alerts when the feed/profile arrive: the component mounts
  // while jobs/matches/profile are still loading (empty props), so the
  // one-time useState initializer above scans an empty feed. Without
  // this effect, alerts would stay empty until the user clicks "Check
  // for new sponsor jobs". Only newly surfaced alerts are merged in
  // (dismissed ones are in the seen-set, so the scan never returns
  // them) and marked seen, exactly like a manual check.
  useEffect(() => {
    const prevIds = new Set(alerts.map((a) => a.job.id));
    const fresh = scanned.filter((a) => !prevIds.has(a.job.id));
    if (fresh.length === 0) return;
    const next = [...alerts, ...fresh];
    setAlerts(next);
    markJobsSeen(next.map((a) => a.job.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scanned]);

  function checkForNew() {
    // Scan fresh at click time: the `scanned` memo is stale across
    // dismissals (it only recomputes when the feed props or scanCount
    // change), so reusing it here would resurface a job dismissed
    // since the last scan — the seen-set is only consulted by a new
    // call to findNewSponsorJobs.
    const freshScan = findNewSponsorJobs(jobs, profile, matches, loadSeenJobIds());
    // New since the last scan = what the fresh scan finds. Only jobs
    // actually surfaced as alerts are marked seen — a sponsor job that
    // does not match the profile today may match after the profile
    // changes, so it must stay unseen until it alerts.
    const next = (() => {
      const prevIds = new Set(alerts.map((a) => a.job.id));
      const fresh = freshScan.filter((a) => !prevIds.has(a.job.id));
      return [...alerts, ...fresh];
    })();
    setAlerts(next);
    markJobsSeen(next.map((a) => a.job.id));
    setLastChecked(new Date());
    setScanCount((c) => c + 1);
  }

  function dismiss(id: string) {
    // Dismissing marks the job seen, so the next scan does not surface
    // it again — removing it from local state alone would let it
    // resurface on the next check.
    markJobsSeen([id]);
    setAlerts((prev) => prev.filter((a) => a.job.id !== id));
  }

  function markAllRead() {
    markJobsSeen(alerts.map((a) => a.job.id));
    setAlerts([]);
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          Sponsor-job alerts
          {unread > 0 && (
            <Badge className="bg-rose-600 hover:bg-rose-600" aria-label={`${unread} unread sponsor alerts`}>
              {unread} new
            </Badge>
          )}
        </CardTitle>
        <CardDescription>{SPONSOR_ALERT_RULE}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-2">
          <Button onClick={checkForNew} size="sm">
            Check for new sponsor jobs
          </Button>
          {unread > 0 && (
            <Button onClick={markAllRead} variant="outline" size="sm">
              Mark all read
            </Button>
          )}
          {lastChecked && (
            <span className="text-xs text-slate-500">
              Last checked{" "}
              {lastChecked.toLocaleTimeString("en-US", {
                hour: "numeric",
                minute: "2-digit",
              })}
            </span>
          )}
        </div>

        {alerts.length === 0 ? (
          <p className="text-sm text-slate-600">
            No new sponsor jobs right now. New ones appear here when a job
            in the live feed sponsors H-1B, matches your profile, and you
            have not seen it before.
          </p>
        ) : (
          <ul className="flex flex-col gap-3">
            {alerts.map(({ job, via, score, reason }) => (
              <li
                key={job.id}
                className="flex flex-col gap-1 rounded-lg border border-slate-200 p-3"
              >
                <span className="flex items-baseline justify-between gap-2">
                  <Link
                    href={alertJobHref(job)}
                    target={job.sourceUrl ? "_blank" : undefined}
                    rel={job.sourceUrl ? "noopener noreferrer" : undefined}
                    className="text-sm font-medium text-slate-900 hover:text-indigo-700"
                  >
                    {job.title} · {job.company}
                  </Link>
                  <button
                    type="button"
                    onClick={() => dismiss(job.id)}
                    className="shrink-0 text-xs text-slate-400 hover:text-slate-700"
                    aria-label={`Dismiss alert for ${job.title}`}
                  >
                    Dismiss
                  </button>
                </span>
                <span className="text-xs text-slate-500">
                  {job.location} · {reason}
                  {via === "match" && score !== null ? ` (${score}%)` : ""}
                </span>
                {job.sourceUrl && (
                  <span className="text-xs">
                    <a
                      href={job.sourceUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-indigo-700 hover:underline"
                    >
                      View the real posting
                    </a>
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}

        <p className="text-xs text-slate-400">
          In-app alerts only: the notifications API has no create endpoint,
          so these are not added to your notification inbox.
        </p>
      </CardContent>
    </Card>
  );
}
