// Cover Letter Generator — editor. Pick a builder resume, paste a job
// description, generate 3–5 full-letter drafts with Co-Pilot (the
// existing copilot endpoint, active_section "cover_letter"), pick one,
// edit it inline, save it on this device, and export PDF / DOCX through
// the existing resume-builder export route (see lib/cover-letter.ts
// for why save is local and what the export renders).
"use client";

import { useMemo, useState } from "react";
import {
  ApiError,
  createBuilderDocument,
  exportBuilderDocument,
  requestCopilot,
  useBuilderDocuments,
} from "@/lib/api";
import { triggerBlobDownload } from "@/lib/resume-builder";
import {
  COVER_LETTER_SECTION,
  buildCoverLetterExportState,
  buildCoverLetterPrompt,
  coverLetterFileName,
  deleteCoverLetter,
  letterFromOption,
  loadCoverLetters,
  newCoverLetterDraft,
  saveCoverLetter,
  validateCoverLetterInputs,
} from "@/lib/cover-letter";
import type { CoverLetterDraft } from "@/lib/cover-letter";
import type { BuilderSection, CopilotOption } from "@/lib/types";
import { toast } from "@/lib/toast-store";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

function loadSaved(): CoverLetterDraft[] {
  if (typeof window === "undefined") return [];
  return loadCoverLetters(window.localStorage);
}

export function CoverLetterEditor() {
  const { data: documents, isLoading: docsLoading } = useBuilderDocuments();
  const [resumeId, setResumeId] = useState("");
  const [jobDescription, setJobDescription] = useState("");
  const [company, setCompany] = useState("");
  const [role, setRole] = useState("");
  const [hiringManager, setHiringManager] = useState("");
  const [extraNotes, setExtraNotes] = useState("");
  const [options, setOptions] = useState<CopilotOption[]>([]);
  const [body, setBody] = useState("");
  const [generating, setGenerating] = useState(false);
  const [exporting, setExporting] = useState<"pdf" | "docx" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<CoverLetterDraft[]>(() => loadSaved());
  const [currentId, setCurrentId] = useState<string | null>(null);
  const [title, setTitle] = useState("");

  const selectedDoc = useMemo(
    () => documents?.find((d) => d.id === resumeId) ?? null,
    [documents, resumeId],
  );

  async function handleGenerate() {
    const problem = validateCoverLetterInputs({ resumeId: resumeId || null, jobDescription });
    if (problem) {
      setError(problem);
      return;
    }
    if (!selectedDoc?.id) {
      setError("Choose a resume to base the cover letter on.");
      return;
    }
    setError(null);
    setGenerating(true);
    setOptions([]);
    try {
      const res = await requestCopilot(selectedDoc.id, {
        active_section: COVER_LETTER_SECTION as BuilderSection,
        user_context: buildCoverLetterPrompt({
          resumeLabel: selectedDoc.title ?? "this resume",
          jobDescription,
          company: company || undefined,
          role: role || undefined,
          hiringManager: hiringManager || undefined,
          extraNotes: extraNotes || undefined,
        }),
        target_jd: jobDescription.trim(),
        resume_state: selectedDoc.state,
      });
      if (res.options.length === 0) {
        setError(
          "Co-Pilot asked for more detail before drafting — add a number, outcome, or detail from the role (for example team size or users reached) and try again.",
        );
        return;
      }
      setOptions(res.options);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not generate the cover letter.");
    } finally {
      setGenerating(false);
    }
  }

  function handlePickOption(option: CopilotOption) {
    const letter = letterFromOption(option);
    setBody(letter);
    setCurrentId(null);
    setTitle("");
    setError(null);
  }

  function handleSave() {
    if (!selectedDoc?.id && !currentId) {
      setError("Choose a resume first — saved letters remember which resume they were based on.");
      return;
    }
    if (body.trim().length === 0) {
      setError("Nothing to save — generate a letter or write one first.");
      return;
    }
    const draft = newCoverLetterDraft({
      resumeId: selectedDoc?.id ?? saved.find((s) => s.id === currentId)?.resumeId ?? "",
      resumeLabel: selectedDoc?.title ?? saved.find((s) => s.id === currentId)?.resumeLabel ?? "Resume",
      jobDescription,
      company: company || undefined,
      role: role || undefined,
      body: body.trim(),
    });
    // Keep the title the user edited, if any.
    if (title.trim()) draft.title = title.trim();
    if (currentId) draft.id = currentId;
    setSaved(saveCoverLetter(window.localStorage, draft));
    setCurrentId(draft.id);
    setTitle(draft.title);
    toast({ title: "Cover letter saved", description: "Saved on this device.", variant: "success" });
  }

  function handleOpenSaved(letter: CoverLetterDraft) {
    setBody(letter.body);
    setJobDescription(letter.jobDescription);
    setCompany(letter.company);
    setRole(letter.role);
    setResumeId(letter.resumeId);
    setCurrentId(letter.id);
    setTitle(letter.title);
    setOptions([]);
    setError(null);
  }

  function handleDeleteSaved(id: string) {
    setSaved(deleteCoverLetter(window.localStorage, id));
    if (currentId === id) setCurrentId(null);
  }

  async function handleExport(format: "pdf" | "docx") {
    if (!selectedDoc) {
      setError("Choose a resume first — the export carries its contact details.");
      return;
    }
    if (body.trim().length === 0) {
      setError("Nothing to export — generate a letter or write one first.");
      return;
    }
    setError(null);
    setExporting(format);
    try {
      // The export route renders a builder document, so the letter is
      // written into one first (contact from the resume, letter as the
      // body). The document stays in Resume Builder titled as below.
      const docTitle =
        title.trim() ||
        `Cover Letter — ${[role.trim(), company.trim()].filter(Boolean).join(" at ") || selectedDoc.title || "Resume"}`;
      const created = await createBuilderDocument({
        state: buildCoverLetterExportState(selectedDoc.state.contact, body.trim()),
        templateId: "modern",
        title: docTitle,
      });
      if (!created.id) throw new Error("Export failed — the letter document could not be created.");
      const { blob, filename } = await exportBuilderDocument(created.id, format);
      triggerBlobDownload(blob, filename || coverLetterFileName(docTitle, format));
      toast({ title: `Cover letter exported (${format.toUpperCase()})`, variant: "success" });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not export the cover letter.");
    } finally {
      setExporting(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Generate a cover letter</CardTitle>
          <CardDescription>
            Pick one of your resumes and paste the job description. Co-Pilot
            drafts 3–5 full letters from your resume — pick one and edit it
            inline. Only facts from your resume are used; anything missing
            becomes an editable [placeholder].
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="cover-letter-resume">Resume</Label>
            <select
              id="cover-letter-resume"
              aria-label="Resume"
              value={resumeId}
              onChange={(e) => setResumeId(e.target.value)}
              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm sm:w-96"
            >
              <option value="">
                {docsLoading ? "Loading resumes…" : "Choose a resume"}
              </option>
              {documents?.map((doc) => (
                <option key={doc.id ?? doc.title} value={doc.id ?? ""}>
                  {doc.title?.trim() || "Untitled resume"}
                </option>
              ))}
            </select>
            {!docsLoading && (!documents || documents.length === 0) && (
              <p className="text-xs text-slate-500">
                No resumes yet — create one in Resume Builder first.
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="cover-letter-jd">Job description</Label>
            <Textarea
              id="cover-letter-jd"
              placeholder="Paste the full job description here…"
              value={jobDescription}
              onChange={(e) => setJobDescription(e.target.value)}
              rows={8}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cover-letter-company">Company (optional)</Label>
              <Input
                id="cover-letter-company"
                placeholder="Navy Federal"
                value={company}
                onChange={(e) => setCompany(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cover-letter-role">Role (optional)</Label>
              <Input
                id="cover-letter-role"
                placeholder="Senior Software Engineer"
                value={role}
                onChange={(e) => setRole(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cover-letter-manager">Hiring manager (optional)</Label>
              <Input
                id="cover-letter-manager"
                placeholder="Leave blank for “Hiring Manager”"
                value={hiringManager}
                onChange={(e) => setHiringManager(e.target.value)}
              />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="cover-letter-notes">Extra instructions (optional)</Label>
            <Input
              id="cover-letter-notes"
              placeholder="e.g. Emphasise my API platform work"
              value={extraNotes}
              onChange={(e) => setExtraNotes(e.target.value)}
            />
          </div>

          <div>
            <Button onClick={() => void handleGenerate()} disabled={generating}>
              {generating ? "Generating…" : "Generate cover letter"}
            </Button>
          </div>
          {error && (
            <p role="alert" className="text-sm text-rose-600">
              {error}
            </p>
          )}
        </CardContent>
      </Card>

      {options.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Drafts from Co-Pilot</CardTitle>
            <CardDescription>
              Pick one to edit below — each draft is a full letter.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {options.map((option, i) => (
              <article
                key={option.id ?? `option-${i}`}
                className="rounded-lg border border-slate-200 p-3"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="secondary">{option.angle}</Badge>
                  <Button size="sm" variant="outline" onClick={() => handlePickOption(option)}>
                    Use this draft
                  </Button>
                </div>
                <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">
                  {letterFromOption(option)}
                </p>
                {option.atsKeywords.length > 0 && (
                  <p className="mt-2 text-xs text-slate-500">
                    Keywords from the job description: {option.atsKeywords.join(", ")}
                  </p>
                )}
              </article>
            ))}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Your cover letter</CardTitle>
          <CardDescription>
            Edit inline — every change is yours. Save keeps the letter on
            this device; export downloads it through Resume Builder (the
            letter is exported as the document body under your contact
            details, and a “Cover Letter — …” document is added to Resume
            Builder).
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="cover-letter-title">Title</Label>
            <Input
              id="cover-letter-title"
              placeholder="Cover Letter — …"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>
          <Textarea
            aria-label="Cover letter text"
            placeholder="Your cover letter — generate a draft above, open a saved one, or write it here."
            value={body}
            onChange={(e) => setBody(e.target.value)}
            rows={16}
          />
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={handleSave} disabled={body.trim().length === 0}>
              Save on this device
            </Button>
            <Button
              variant="outline"
              onClick={() => void handleExport("pdf")}
              disabled={exporting !== null || body.trim().length === 0}
            >
              {exporting === "pdf" ? "Exporting…" : "Export PDF"}
            </Button>
            <Button
              variant="outline"
              onClick={() => void handleExport("docx")}
              disabled={exporting !== null || body.trim().length === 0}
            >
              {exporting === "docx" ? "Exporting…" : "Download DOCX"}
            </Button>
          </div>
        </CardContent>
      </Card>

      {saved.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Saved on this device</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col divide-y divide-slate-100">
              {saved.map((letter) => (
                <li key={letter.id} className="flex flex-wrap items-center gap-2 py-2">
                  <button
                    type="button"
                    onClick={() => handleOpenSaved(letter)}
                    className="min-w-0 flex-1 truncate text-left text-sm font-medium text-slate-900 hover:text-indigo-700"
                  >
                    {letter.title}
                  </button>
                  <span className="text-xs text-slate-500">
                    {new Date(letter.updatedAt).toLocaleDateString()}
                  </span>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => handleDeleteSaved(letter.id)}
                  >
                    Delete
                  </Button>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
