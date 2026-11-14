"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, useApplyJob, useJobs, useResumes, useSaveJob } from "@/lib/api";
import { formatPostedAt, formatSalary } from "@/lib/jobs";
import { JobSource } from "@/components/job-source";
import { toast } from "@/lib/toast-store";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Building2, MapPin, Search } from "lucide-react";
import type { Job } from "@/lib/types";

export default function JobsPage() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [location, setLocation] = useState("");
  const [workType, setWorkType] = useState("ALL");
  const [source, setSource] = useState("ALL");
  const [sponsorsOnly, setSponsorsOnly] = useState(false);
  const { data: page, isLoading, isError } = useJobs({
    query,
    location,
    workType,
    source,
    sponsorsH1b: sponsorsOnly,
    size: 20,
  });
  const jobs = page?.content ?? [];
  const [pickedId, setPickedId] = useState<string | null>(null);
  // Default the detail pane to the first job; the user's pick wins.
  const selectedId = pickedId ?? jobs[0]?.id ?? null;
  const selected: Job | null = jobs.find((j) => j.id === selectedId) ?? jobs[0] ?? null;

  const { data: resumes } = useResumes();
  const saveJob = useSaveJob();
  const applyJob = useApplyJob();
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());
  const [appliedIds, setAppliedIds] = useState<Set<string>>(new Set());

  async function handleSave(job: Job) {
    const resumeId = resumes?.find((r) => r.status === "READY")?.id ?? null;
    try {
      await saveJob.mutateAsync({ jobId: job.id, resumeId });
      setSavedIds((s) => new Set(s).add(job.id));
      toast({ title: "Saved", description: `${job.title} added to your tracker.`, variant: "success" });
    } catch (err) {
      toast({ title: "Could not save", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  async function handleApply(job: Job) {
    try {
      await applyJob.mutateAsync(job.id);
      setAppliedIds((s) => new Set(s).add(job.id));
      toast({ title: "Approval requested", description: "Your application is waiting in the Approvals inbox.", variant: "success" });
    } catch (err) {
      toast({ title: "Could not request approval", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Jobs</h1>
        <p className="mt-1 text-sm text-slate-500">
          Search, filter, and open a job to see the detail. Saving adds it to
          your tracker; applying always asks for your approval first.
        </p>
      </div>

      <Card>
        <CardContent className="flex flex-col gap-3 pt-5 sm:flex-row sm:flex-wrap sm:items-center">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <Input
              className="pl-9"
              placeholder="Search title, company, or skill…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Search jobs"
            />
          </div>
          <Input
            placeholder="Location, e.g. Reston, VA"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            aria-label="Filter by location"
            className="sm:w-48"
          />
          <Select value={workType} onChange={(e) => setWorkType(e.target.value)} aria-label="Work type" className="sm:w-40">
            <option value="ALL">All types</option>
            <option value="Remote">Remote</option>
            <option value="Hybrid">Hybrid</option>
            <option value="Onsite">Onsite</option>
          </Select>
          <Select value={source} onChange={(e) => setSource(e.target.value)} aria-label="Job source" className="sm:w-40">
            <option value="ALL">All sources</option>
            <option value="LINKEDIN">LinkedIn</option>
            <option value="INDEED">Indeed</option>
            <option value="MONSTER">Monster</option>
            <option value="NAUKRI">Naukri</option>
            <option value="COMPANY">Company site</option>
            <option value="SEED">Seed</option>
          </Select>
          <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={sponsorsOnly}
              onChange={(e) => setSponsorsOnly(e.target.checked)}
              className="h-4 w-4 accent-indigo-600"
            />
            Sponsors H-1B
          </label>
        </CardContent>
      </Card>

      {isLoading && <p className="text-sm text-slate-500">Loading jobs…</p>}
      {isError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load jobs. Make sure you are signed in and the backend is running.
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="flex flex-col gap-3 lg:col-span-3">
          {!isLoading && jobs.length === 0 && (
            <p className="text-sm text-slate-500">
              No jobs match that search. Try a different keyword.
            </p>
          )}
          {jobs.map((job) => (
            <Card
              key={job.id}
              className={`cursor-pointer transition-shadow hover:shadow-md ${selected?.id === job.id ? "ring-2 ring-indigo-600" : ""}`}
              onClick={() => setPickedId(job.id)}
            >
              <CardHeader className="pb-2">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle>{job.title}</CardTitle>
                    <CardDescription className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
                      <span className="inline-flex items-center gap-1">
                        <Building2 className="h-3.5 w-3.5" aria-hidden /> {job.company}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <MapPin className="h-3.5 w-3.5" aria-hidden /> {job.location}
                      </span>
                    </CardDescription>
                  </div>
                  <Badge variant="secondary" className="shrink-0">{job.workType}</Badge>
                </div>
              </CardHeader>
              <CardContent className="flex flex-wrap items-center gap-2 pt-0">
                <span className="text-sm font-medium text-slate-900">
                  {formatSalary(job.salaryMin, job.salaryMax)}
                </span>
                {job.sponsorsH1b && <Badge variant="success">Sponsors H-1B</Badge>}
                <JobSource job={job} />
                <span className="text-xs text-slate-400">
                  {formatPostedAt(job.postedAt)}
                </span>
              </CardContent>
            </Card>
          ))}
          {page && page.totalElements > jobs.length && (
            <p className="text-xs text-slate-400">
              Showing {jobs.length} of {page.totalElements} jobs.
            </p>
          )}
        </div>

        <Card className="h-fit lg:col-span-2">
          {selected ? (
            <>
              <CardHeader>
                <CardTitle>{selected.title}</CardTitle>
                <CardDescription>
                  {selected.company} · {selected.location} · {selected.workType}
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <p className="text-sm font-medium text-slate-900">
                  {formatSalary(selected.salaryMin, selected.salaryMax)} {selected.currency}
                </p>
                <p className="text-sm text-slate-600">{selected.summary}</p>
                <div className="flex flex-wrap gap-2">
                  {selected.skills.map((s) => (
                    <Badge key={s} variant="outline">{s}</Badge>
                  ))}
                </div>
                <p className="text-xs text-slate-400">
                  {selected.role} · {formatPostedAt(selected.postedAt)}
                </p>
                <JobSource job={selected} />
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    disabled={savedIds.has(selected.id) || saveJob.isPending}
                    onClick={() => void handleSave(selected)}
                  >
                    {savedIds.has(selected.id) ? "Saved" : "Save"}
                  </Button>
                  <Button
                    disabled={appliedIds.has(selected.id) || applyJob.isPending}
                    onClick={() => void handleApply(selected)}
                  >
                    {appliedIds.has(selected.id) ? "Approval pending" : "Apply"}
                  </Button>
                  <Button variant="outline" onClick={() => router.push("/matches")}>
                    See my match
                  </Button>
                </div>
              </CardContent>
            </>
          ) : (
            <CardContent className="pt-5">
              <p className="text-sm text-slate-500">Select a job to see the detail.</p>
            </CardContent>
          )}
        </Card>
      </div>
    </div>
  );
}
