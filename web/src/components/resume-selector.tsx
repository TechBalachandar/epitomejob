// W6: resume selector — one dropdown shared by the Matches page and the
// Dashboard. Options are the user's resumes by label; non-READY resumes
// are listed (label + status) but matching against one returns the
// backend's RESUME_NOT_READY error instead of silently switching.
"use client";

import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import type { ResumeItem } from "@/lib/types";

export function ResumeSelector({
  resumes,
  value,
  onChange,
  id = "resume-selector",
}: {
  resumes: ResumeItem[];
  value: string | null;
  onChange: (id: string) => void;
  id?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>Resume</Label>
      <Select
        id={id}
        aria-label="Resume"
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        className="sm:w-72"
      >
        {resumes.length === 0 && <option value="">No resumes yet</option>}
        {resumes.map((r) => (
          <option key={r.id} value={r.id}>
            {r.label}
            {r.status !== "READY" ? ` (${r.status})` : ""}
          </option>
        ))}
      </Select>
    </div>
  );
}
