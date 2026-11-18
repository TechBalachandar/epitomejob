// Resume Builder — Template Library modal. A Teal-style library: a left
// filter column (search, Style, Layout), a thumbnail grid, and a live
// preview of the selected template side-by-side. Selecting a thumbnail
// only stages it — the single "Use Template" commit applies it. Filters
// are derived from the template itself (see templateStyleOf /
// templateLayoutOf in @/lib/resume-builder), never invented.
"use client";

import { useMemo, useState } from "react";
import { X } from "lucide-react";
import {
  sampleBuilderState,
  templateLayoutOf,
  templateStyleOf,
} from "@/lib/resume-builder";
import type { BuilderTemplate, ResumeBuilderState } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { TemplateGallery } from "@/components/resume-builder/template-gallery";
import { ResumePreview } from "@/components/resume-builder/resume-preview";
import { cn } from "@/lib/utils";

function FilterGroup({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: string[];
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
        {label}
      </span>
      <div className="flex flex-col gap-1">
        {options.map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={value === option}
            onClick={() => onChange(option)}
            className={cn(
              "rounded-md px-2.5 py-1.5 text-left text-sm transition-colors",
              value === option
                ? "bg-indigo-600 font-medium text-white"
                : "text-slate-700 hover:bg-slate-100",
            )}
          >
            {option}
          </button>
        ))}
      </div>
    </div>
  );
}

export function TemplateLibraryModal({
  templates,
  state,
  selectedId,
  onSelect,
  onUse,
  onClose,
  loading,
}: {
  templates: BuilderTemplate[];
  state: ResumeBuilderState;
  selectedId: string | null;
  onSelect: (template: BuilderTemplate) => void;
  onUse: () => void;
  onClose: () => void;
  loading?: boolean;
}) {
  const [query, setQuery] = useState("");
  const [style, setStyle] = useState("All");
  const [layout, setLayout] = useState("All");

  const styles = useMemo(() => {
    const seen = new Set(templates.map((t) => templateStyleOf(t)));
    return ["All", ...[...seen].sort()];
  }, [templates]);
  const layouts = useMemo(() => {
    const seen = new Set(templates.map((t) => templateLayoutOf(t)));
    return ["All", ...[...seen].sort()];
  }, [templates]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return templates.filter((t) => {
      if (style !== "All" && templateStyleOf(t) !== style) return false;
      if (layout !== "All" && templateLayoutOf(t) !== layout) return false;
      if (q === "") return true;
      return `${t.name} ${t.description}`.toLowerCase().includes(q);
    });
  }, [templates, query, style, layout]);

  const selected =
    templates.find((t) => t.id === selectedId) ?? filtered[0] ?? templates[0] ?? null;
  const previewState = state ?? sampleBuilderState();

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Choose a template"
        className="flex max-h-[92vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-4">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Choose a template</h2>
            <p className="mt-0.5 text-sm text-slate-500">
              Each thumbnail is a live preview of the template with your
              content — pick one, preview it, then use it.
            </p>
          </div>
          <Button variant="ghost" size="icon" aria-label="Close template library" onClick={onClose}>
            <X className="h-5 w-5" aria-hidden />
          </Button>
        </div>

        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-6 lg:flex-row">
          <div className="flex w-full shrink-0 flex-col gap-5 lg:w-48">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="template-library-search">Search templates</Label>
              <Input
                id="template-library-search"
                placeholder="Search by name…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <FilterGroup label="Style" options={styles} value={style} onChange={setStyle} />
            <FilterGroup label="Layout" options={layouts} value={layout} onChange={setLayout} />
          </div>

          <div className="min-w-0 flex-1">
            {loading && <p className="text-sm text-slate-500">Loading templates…</p>}
            {!loading && filtered.length === 0 && (
              <p className="text-sm text-slate-500">
                No templates match those filters. Clear the search or pick a
                different style.
              </p>
            )}
            <TemplateGallery
              templates={filtered}
              state={previewState}
              selectedId={selectedId}
              onSelect={onSelect}
            />
          </div>

          <div className="w-full shrink-0 lg:w-[340px]">
            <h3 className="mb-2 text-sm font-semibold text-slate-900">
              Preview{selected ? ` — ${selected.name}` : ""}
            </h3>
            {selected ? (
              <ResumePreview template={selected} state={previewState} />
            ) : (
              <p className="text-sm text-slate-500">
                Select a template to see its live preview.
              </p>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-100 px-6 py-4">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={!selectedId || loading} onClick={onUse}>
            {selectedId && selected ? `Use ${selected.name}` : "Use Template"}
          </Button>
        </div>
      </div>
    </div>
  );
}
