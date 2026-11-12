"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { MOCK_JOBS } from "@/lib/mock-data";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Building2, MapPin, Search } from "lucide-react";
import type { Job } from "@/lib/types";

function formatSalary(min: number, max: number): string {
  const k = (n: number) => `$${Math.round(n / 1000)}k`;
  return `${k(min)} – ${k(max)}`;
}

export default function JobsPage() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [mode, setMode] = useState("ALL");
  const [sponsorsOnly, setSponsorsOnly] = useState(false);
  const [selected, setSelected] = useState<Job | null>(MOCK_JOBS[0] ?? null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return MOCK_JOBS.filter((job) => {
      if (mode !== "ALL" && job.workMode.toUpperCase() !== mode) return false;
      if (sponsorsOnly && !job.sponsorsVisa) return false;
      if (!q) return true;
      const haystack = `${job.title} ${job.company} ${job.location} ${job.skills.join(" ")}`.toLowerCase();
      return haystack.includes(q);
    });
  }, [query, mode, sponsorsOnly]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Jobs</h1>
        <p className="mt-1 text-sm text-slate-500">
          Mock job list for W0 — search, filter, and open a job to see the
          detail. Real Job Discovery lands in W2.
        </p>
      </div>

      <Card>
        <CardContent className="flex flex-col gap-3 pt-5 sm:flex-row sm:items-center">
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
          <Select value={mode} onChange={(e) => setMode(e.target.value)} aria-label="Work mode" className="sm:w-40">
            <option value="ALL">All modes</option>
            <option value="REMOTE">Remote</option>
            <option value="HYBRID">Hybrid</option>
            <option value="ONSITE">Onsite</option>
          </Select>
          <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={sponsorsOnly}
              onChange={(e) => setSponsorsOnly(e.target.checked)}
              className="h-4 w-4 accent-indigo-600"
            />
            Sponsors visa
          </label>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="flex flex-col gap-3 lg:col-span-3">
          {filtered.length === 0 && (
            <p className="text-sm text-slate-500">
              No jobs match that search. Try a different keyword.
            </p>
          )}
          {filtered.map((job) => (
            <Card
              key={job.id}
              className={`cursor-pointer transition-shadow hover:shadow-md ${selected?.id === job.id ? "ring-2 ring-indigo-600" : ""}`}
              onClick={() => setSelected(job)}
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
                  <Badge variant="secondary" className="shrink-0">{job.workMode}</Badge>
                </div>
              </CardHeader>
              <CardContent className="flex flex-wrap items-center gap-2 pt-0">
                <span className="text-sm font-medium text-slate-900">
                  {formatSalary(job.salaryMin, job.salaryMax)}
                </span>
                {job.sponsorsVisa && <Badge variant="success">Sponsors visa</Badge>}
                <span className="text-xs text-slate-400">
                  Posted {job.postedDaysAgo}d ago
                </span>
              </CardContent>
            </Card>
          ))}
        </div>

        <Card className="h-fit lg:col-span-2">
          {selected ? (
            <>
              <CardHeader>
                <CardTitle>{selected.title}</CardTitle>
                <CardDescription>
                  {selected.company} · {selected.location} · {selected.workMode}
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <p className="text-sm font-medium text-slate-900">
                  {formatSalary(selected.salaryMin, selected.salaryMax)}
                </p>
                <p className="text-sm text-slate-600">{selected.summary}</p>
                <div className="flex flex-wrap gap-2">
                  {selected.skills.map((s) => (
                    <Badge key={s} variant="outline">{s}</Badge>
                  ))}
                </div>
                <div className="flex gap-2">
                  <Button onClick={() => router.push("/matches")}>
                    See my match
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => setSelected(null)}
                  >
                    Close
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
