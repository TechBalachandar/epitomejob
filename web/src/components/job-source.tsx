// W6: job source badge + apply link, shared by Matches, Jobs and the
// dashboard. Badge always renders; the link renders only when the job
// carries a sourceUrl (null = badge only, no link).
import { ExternalLink } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { applyLinkLabel, jobSourceLabel } from "@/lib/jobs";
import type { Job } from "@/lib/types";

export function JobSourceBadge({ source }: { source: string | null | undefined }) {
  return (
    <Badge variant="secondary" data-testid="job-source-badge">
      {jobSourceLabel(source)}
    </Badge>
  );
}

export function JobSourceLink({ job }: { job: Job }) {
  if (!job.sourceUrl) return null;
  return (
    <a
      href={job.sourceUrl}
      target="_blank"
      rel="noopener noreferrer"
      data-testid="job-source-link"
      onClick={(e) => e.stopPropagation()}
      className="inline-flex items-center gap-1 text-sm font-medium text-indigo-700 hover:underline"
    >
      {applyLinkLabel(job.source)}
      <ExternalLink className="h-3.5 w-3.5" aria-hidden />
    </a>
  );
}

/** Badge + (when present) apply link, in one row. */
export function JobSource({ job }: { job: Job }) {
  return (
    <span className="flex flex-wrap items-center gap-2">
      <JobSourceBadge source={job.source} />
      <JobSourceLink job={job} />
    </span>
  );
}
