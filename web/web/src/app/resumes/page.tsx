"use client";

import { useEffect, useRef, useState } from "react";
import { useResumes, useUploadResume, ApiError } from "@/lib/api";
import { formatFileSize, validateResumeFile } from "@/lib/validation";
import { toast } from "@/lib/toast-store";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { UploadCloud } from "lucide-react";

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

export default function ResumesPage() {
  const { data: resumes, isLoading, refetch } = useResumes();
  const upload = useUploadResume();
  const [error, setError] = useState<string | null>(null);
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
    setError(null);
    try {
      await upload.mutateAsync(file);
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
          READY shortly after upload.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Upload a resume</CardTitle>
          <CardDescription>Choose a PDF file from your device.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
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
              <li key={r.id} className="flex flex-wrap items-center gap-3 py-3">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-slate-900">
                    {r.fileName}
                  </span>
                  <span className="block text-xs text-slate-500">
                    {formatFileSize(r.fileSize)} ·{" "}
                    {new Date(r.createdAt).toLocaleString()}
                  </span>
                </span>
                {statusBadge(r.status)}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
