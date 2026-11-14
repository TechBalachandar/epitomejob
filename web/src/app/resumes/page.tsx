"use client";

import { useEffect, useRef, useState } from "react";
import { useRenameResume, useResumes, useUploadResume, ApiError } from "@/lib/api";
import { formatFileSize, validateResumeFile } from "@/lib/validation";
import { toast } from "@/lib/toast-store";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Pencil, UploadCloud } from "lucide-react";
import type { ResumeItem } from "@/lib/types";

function statusBadge(status: string) {
  switch (status) {
    case "READY":
      return <Badge variant="success">READY</Badge>;
    case "PROCESSING":
      return <Badge variant="warning">PROCESSING</Badge>;
    case "FAILED":
      return <Badge variant="destructive">FAILED</Badge>;
    default:
      return <Badge variant="secondary">{status}</Badge>;
  }
}

/** One resume row: label (with inline rename), file name, status, date. */
function ResumeRow({ resume }: { resume: ResumeItem }) {
  const rename = useRenameResume();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(resume.label);
  const [renameError, setRenameError] = useState<string | null>(null);

  async function handleSave() {
    const label = draft.trim();
    if (label.length < 1 || label.length > 80) {
      setRenameError("Label must be 1–80 characters.");
      return;
    }
    setRenameError(null);
    try {
      await rename.mutateAsync({ id: resume.id, label });
      setEditing(false);
      toast({ title: "Resume renamed", description: label, variant: "success" });
    } catch (err) {
      setRenameError(err instanceof ApiError ? err.message : "Could not rename the resume.");
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-3 py-3">
      <span className="min-w-0 flex-1">
        {editing ? (
          <span className="flex flex-wrap items-center gap-2">
            <Input
              aria-label={`Label for ${resume.fileName}`}
              value={draft}
              maxLength={80}
              onChange={(e) => setDraft(e.target.value)}
              className="w-56 max-w-full"
            />
            <Button size="sm" disabled={rename.isPending} onClick={() => void handleSave()}>
              {rename.isPending ? "Saving…" : "Save"}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                setDraft(resume.label);
                setRenameError(null);
                setEditing(false);
              }}
            >
              Cancel
            </Button>
          </span>
        ) : (
          <span className="flex flex-wrap items-center gap-2">
            <span className="block truncate text-sm font-medium text-slate-900">
              {resume.label}
            </span>
            <Button
              size="sm"
              variant="ghost"
              aria-label={`Rename ${resume.label}`}
              onClick={() => {
                setDraft(resume.label);
                setEditing(true);
              }}
            >
              <Pencil className="h-3.5 w-3.5" aria-hidden />
              Rename
            </Button>
          </span>
        )}
        <span className="block truncate text-xs text-slate-500">{resume.fileName}</span>
        <span className="block text-xs text-slate-500">
          {formatFileSize(resume.fileSize)} ·{" "}
          {new Date(resume.createdAt).toLocaleString()}
        </span>
        {renameError && (
          <span role="alert" className="block text-xs text-rose-600">{renameError}</span>
        )}
      </span>
      {statusBadge(resume.status)}
    </li>
  );
}

export default function ResumesPage() {
  const { data: resumes, isLoading, refetch } = useResumes();
  const upload = useUploadResume();
  const [error, setError] = useState<string | null>(null);
  const [label, setLabel] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const hasProcessing = resumes?.some((r) => r.status === "PROCESSING");
  // Poll while the server parse is running so PROCESSING flips to READY.
  useEffect(() => {
    if (!hasProcessing) return;
    const timer = window.setInterval(() => void refetch(), 1000);
    return () => window.clearInterval(timer);
  }, [hasProcessing, refetch]);

  async function handleFile(file: File | undefined) {
    if (!file) return;
    const problem = validateResumeFile(file);
    if (problem) {
      setError(problem);
      return;
    }
    if (label.trim().length > 80) {
      setError("Label must be 80 characters or fewer.");
      return;
    }
    setError(null);
    try {
      await upload.mutateAsync({ file, label: label.trim() || undefined });
      setLabel("");
      toast({ title: "Resume uploaded", description: "Parsing on the server — status will turn READY shortly.", variant: "success" });
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Upload failed.";
      setError(message);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Resumes</h1>
        <p className="mt-1 text-sm text-slate-500">
          PDF only, up to 10 MB. The server parses the file — status turns
          READY shortly after upload. Give each resume a label (e.g. Android
          Developer) so matching can tell them apart.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Upload a resume</CardTitle>
          <CardDescription>Choose a PDF file from your device.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="resume-label">Label (optional)</Label>
            <Input
              id="resume-label"
              placeholder="Android Developer"
              value={label}
              maxLength={80}
              onChange={(e) => setLabel(e.target.value)}
              className="sm:w-72"
            />
            <p className="text-xs text-slate-500">
              Without a label, the file name is used.
            </p>
          </div>
          <input
            ref={inputRef}
            type="file"
            accept="application/pdf,.pdf"
            className="hidden"
            onChange={(e) => void handleFile(e.target.files?.[0])}
          />
          <Button
            variant="outline"
            className="w-fit"
            disabled={upload.isPending}
            onClick={() => inputRef.current?.click()}
          >
            <UploadCloud className="h-4 w-4" aria-hidden />
            {upload.isPending ? "Uploading…" : "Choose PDF"}
          </Button>
          {error && (
            <p role="alert" className="text-sm text-rose-600">{error}</p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Your resumes</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading && <p className="text-sm text-slate-500">Loading…</p>}
          {!isLoading && (!resumes || resumes.length === 0) && (
            <p className="text-sm text-slate-500">
              No resumes yet. Upload your first PDF above.
            </p>
          )}
          <ul className="flex flex-col divide-y divide-slate-100">
            {resumes?.map((r) => (
              <ResumeRow key={r.id} resume={r} />
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
