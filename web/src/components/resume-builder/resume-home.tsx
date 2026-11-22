// Resume Builder — home / resume list. Large action
// cards (New Resume, Start from job description, Start from template,
// New Cover Letter) with soft pastel icon chips, plus a Recent Resumes
// section with search and sort. Every card maps to a real action.
// Opening a resume loads it in the editor; "Match a job" opens it
// straight into the Job Matcher tab; "New Cover Letter" opens the
// cover-letter generator, which drafts from a builder resume + job
// description via Co-Pilot.
"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ArrowUpDown,
  Briefcase,
  FilePlus2,
  LayoutTemplate,
  ListFilter,
  Mail,
  Search,
  Sparkles,
  UploadCloud,
} from "lucide-react";
import type { ResumeBuilderDocument } from "@/lib/types";
import {
  filterBuilderDocuments,
  sortBuilderDocuments,
} from "@/lib/resume-builder";
import type { BuilderDocumentsSort } from "@/lib/resume-builder";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

function formatEditedAt(value: string | null): string {
  if (!value) return "Not edited yet";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Not edited yet";
  return `Edited: ${date.toLocaleDateString(undefined, {
    month: "2-digit",
    day: "2-digit",
    year: "numeric",
  })}`;
}

const ACTION_CARDS: {
  id: "new" | "job-description" | "template" | "cover-letter";
  title: string;
  description: string;
  chip: string;
  icon: React.ReactNode;
}[] = [
  {
    id: "new",
    title: "New Resume",
    description: "Start from a blank resume and pick a template.",
    chip: "bg-teal-100 text-teal-700",
    icon: <FilePlus2 className="h-6 w-6" aria-hidden />,
  },
  {
    id: "job-description",
    title: "Start from job description",
    description: "Paste a job description — the resume is tailored to it.",
    chip: "bg-violet-100 text-violet-700",
    icon: <Briefcase className="h-6 w-6" aria-hidden />,
  },
  {
    id: "template",
    title: "Start from template",
    description: "Browse the template library and start from a design.",
    chip: "bg-rose-100 text-rose-700",
    icon: <LayoutTemplate className="h-6 w-6" aria-hidden />,
  },
  {
    id: "cover-letter",
    title: "New Cover Letter",
    description: "Draft a tailored cover letter from a resume and a job description.",
    chip: "bg-amber-100 text-amber-700",
    icon: <Mail className="h-6 w-6" aria-hidden />,
  },
];

export function ResumeHome({
  documents,
  loading,
  error,
  onNew,
  onFromJobDescription,
  onFromTemplate,
  onImport,
  onOpen,
  onMatchJob,
}: {
  documents: ResumeBuilderDocument[];
  loading: boolean;
  error: string | null;
  onNew: () => void;
  onFromJobDescription: () => void;
  onFromTemplate: () => void;
  onImport: () => void;
  onOpen: (doc: ResumeBuilderDocument) => void;
  onMatchJob: (doc: ResumeBuilderDocument) => void;
}) {
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<BuilderDocumentsSort>("recent");
  const router = useRouter();
  const visible = useMemo(
    () => sortBuilderDocuments(filterBuilderDocuments(documents, query), sort),
    [documents, query, sort],
  );

  const handleAction = (id: (typeof ACTION_CARDS)[number]["id"]) => {
    if (id === "new") onNew();
    else if (id === "job-description") onFromJobDescription();
    else if (id === "cover-letter") router.push("/cover-letter");
    else onFromTemplate();
  };

  return (
    <div className="flex min-w-0 flex-col gap-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">
          Resume Builder
        </h1>
        <p className="mt-2 max-w-2xl text-base text-slate-500">
          Create, update, and tailor unlimited resumes in one place — then
          check each one against the job you want.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {ACTION_CARDS.map((card) => (
          <button
            key={card.id}
            type="button"
            onClick={() => handleAction(card.id)}
            className="group flex flex-col items-center gap-3 rounded-2xl border border-slate-200 bg-white px-6 py-8 text-center shadow-sm transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
          >
            <span
              className={cn(
                "flex h-16 w-16 items-center justify-center rounded-full",
                card.chip,
              )}
            >
              {card.icon}
            </span>
            <span className="text-base font-semibold text-slate-900">
              {card.title}
            </span>
            <span className="text-sm text-slate-500">{card.description}</span>
          </button>
        ))}
      </div>

      <button
        type="button"
        onClick={onImport}
        className="inline-flex items-center gap-2 self-start text-sm font-medium text-indigo-600 hover:text-indigo-800"
      >
        <UploadCloud className="h-4 w-4" aria-hidden />
        Have a resume already? Import it
      </button>

      <div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-bold text-slate-900">Recent Resumes</h2>
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                aria-hidden
              />
              <Input
                aria-label="Search resumes"
                placeholder="Search Resumes"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="w-56 pl-8"
              />
            </div>
            <label className="inline-flex items-center gap-1.5 text-sm text-slate-600">
              <ListFilter className="h-4 w-4" aria-hidden />
              <span className="sr-only">Sort resumes</span>
              <select
                aria-label="Sort resumes"
                value={sort}
                onChange={(e) =>
                  setSort(e.target.value as BuilderDocumentsSort)
                }
                className="rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm"
              >
                <option value="recent">Most recent</option>
                <option value="title">Title A–Z</option>
              </select>
            </label>
          </div>
        </div>

        {error && (
          <p role="alert" className="mt-3 text-sm text-rose-600">
            {error}
          </p>
        )}
        {loading && <p className="mt-3 text-sm text-slate-500">Loading resumes…</p>}

        {!loading && visible.length === 0 && (
          <Card className="mt-4 rounded-2xl shadow-sm">
            <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
              <Sparkles className="h-8 w-8 text-slate-300" aria-hidden />
              <p className="text-sm font-medium text-slate-900">
                {documents.length === 0
                  ? "No resumes yet"
                  : "No resumes match that search"}
              </p>
              <p className="max-w-sm text-sm text-slate-500">
                {documents.length === 0
                  ? "Create your first resume from a template, a job description, or a blank page."
                  : "Try a different search term."}
              </p>
              {documents.length === 0 && (
                <Button onClick={onNew}>
                  <FilePlus2 className="h-4 w-4" aria-hidden />
                  New Resume
                </Button>
              )}
            </CardContent>
          </Card>
        )}

        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {visible.map((doc, index) => (
            <Card
              key={`${doc.id ?? "noid"}-${index}`}
              className="rounded-2xl shadow-sm transition hover:shadow-md"
            >
              <CardContent className="flex flex-col gap-3 pt-5">
                <button
                  type="button"
                  data-testid="builder-doc-title"
                  onClick={() => onOpen(doc)}
                  className="text-left focus:outline-none"
                >
                  <span className="block truncate text-base font-semibold text-slate-900 hover:text-indigo-700">
                    {doc.title?.trim() || "Untitled resume"}
                  </span>
                </button>
                <span className="text-xs text-slate-500">
                  {formatEditedAt(doc.updatedAt)}
                </span>
                <div className="flex items-center gap-2 border-t border-slate-100 pt-3">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onMatchJob(doc)}
                  >
                    <ArrowUpDown className="h-3.5 w-3.5" aria-hidden />
                    Match a job
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => onOpen(doc)}>
                    Open
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
