// Resume Builder — Designer panel. Teal-style design controls for the
// live preview: Presentation (font, line-height, accent, date format,
// header + date alignment), Sections (show/hide each section), Settings
// (page margins). Every control writes to the page's design state, and
// the preview applies it instantly via one override <style> — nothing
// here touches the document state or the backend.
"use client";

import { useState } from "react";
import {
  ACCENT_OPTIONS,
  DATE_SEPARATOR_OPTIONS,
  FONT_OPTIONS,
  SKILLS_LAYOUT_OPTIONS,
} from "@/lib/resume-builder";
import type {
  DesignSettings,
  HeaderAlign,
  LocationAlign,
  SkillsLayout,
} from "@/lib/resume-builder";
import type { BuilderSection } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { cn } from "@/lib/utils";

type DesignerTab = "presentation" | "sections" | "settings";

const SECTION_LABELS: { id: BuilderSection; label: string; hint: string }[] = [
  { id: "contact", label: "Contact", hint: "Name, title, email, phone" },
  { id: "summary", label: "Summary", hint: "The opening paragraph" },
  { id: "experience", label: "Experience", hint: "Roles and bullets" },
  { id: "education", label: "Education", hint: "Schools and degrees" },
  { id: "skills", label: "Skills", hint: "The skills list" },
];

function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { id: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-medium text-slate-900">{label}</span>
      <div
        role="group"
        aria-label={label}
        className="grid auto-cols-fr grid-flow-col gap-1 rounded-lg bg-slate-100 p-1"
      >
        {options.map((option) => (
          <button
            key={option.id}
            type="button"
            aria-pressed={value === option.id}
            onClick={() => onChange(option.id)}
            className={cn(
              "rounded-md px-2 py-1.5 text-sm transition-colors",
              value === option.id
                ? "bg-white font-medium text-slate-900 shadow-sm"
                : "text-slate-600 hover:text-slate-900",
            )}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function MarginStepper({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
}) {
  const clamp = (v: number) => Math.min(96, Math.max(0, v));
  return (
    <div className="flex flex-col gap-1.5">
      <Label>{label}</Label>
      <div className="flex items-center gap-1">
        <Button
          type="button"
          variant="outline"
          size="sm"
          aria-label={`Decrease ${label} margin`}
          onClick={() => onChange(clamp(value - 4))}
        >
          −
        </Button>
        <Input
          type="number"
          min={0}
          max={96}
          step={4}
          aria-label={`${label} margin in pixels`}
          value={value}
          onChange={(e) => {
            const next = Number(e.target.value);
            if (!Number.isNaN(next)) onChange(clamp(next));
          }}
          className="w-20 text-center"
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          aria-label={`Increase ${label} margin`}
          onClick={() => onChange(clamp(value + 4))}
        >
          +
        </Button>
        <span className="text-xs text-slate-500">px</span>
      </div>
    </div>
  );
}

export function DesignerPanel({
  design,
  onDesignChange,
  visibleSections,
  onToggleSection,
  onResetDesign,
}: {
  design: DesignSettings;
  onDesignChange: (next: DesignSettings) => void;
  visibleSections: Record<BuilderSection, boolean>;
  onToggleSection: (section: BuilderSection) => void;
  onResetDesign: () => void;
}) {
  const [tab, setTab] = useState<DesignerTab>("presentation");
  const set = (patch: Partial<DesignSettings>) => onDesignChange({ ...design, ...patch });
  const setMargin = (key: keyof DesignSettings["margins"], value: number) =>
    set({ margins: { ...design.margins, [key]: value } });

  return (
    <Card className="rounded-2xl shadow-sm">
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>Designer</CardTitle>
          <Button variant="ghost" size="sm" onClick={onResetDesign}>
            Reset to defaults
          </Button>
        </div>
        <div role="tablist" aria-label="Designer settings" className="mt-2 flex gap-1 rounded-lg bg-slate-100 p-1">
          {(
            [
              ["presentation", "Presentation"],
              ["sections", "Sections"],
              ["settings", "Settings"],
            ] as [DesignerTab, string][]
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={tab === id}
              onClick={() => setTab(id)}
              className={cn(
                "flex-1 rounded-md px-2 py-1.5 text-sm transition-colors",
                tab === id
                  ? "bg-white font-medium text-slate-900 shadow-sm"
                  : "text-slate-600 hover:text-slate-900",
              )}
            >
              {label}
            </button>
          ))}
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <p className="text-xs text-slate-500">
          Preview only — exports use the template&apos;s own styling and
          include all sections. (The backend does not store design or
          visibility yet.)
        </p>
        {tab === "presentation" && (
          <>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="designer-font">Font</Label>
              <Select
                id="designer-font"
                value={design.font}
                onChange={(e) => set({ font: e.target.value })}
              >
                {FONT_OPTIONS.map((font) => (
                  <option key={font.id} value={font.id}>
                    {font.label}
                  </option>
                ))}
              </Select>
            </div>
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <Label htmlFor="designer-line-height">Line height</Label>
                <span className="text-sm font-medium text-slate-700">
                  {Math.round(design.lineHeight * 100)}%
                </span>
              </div>
              <input
                id="designer-line-height"
                type="range"
                min={100}
                max={200}
                step={5}
                value={Math.round(design.lineHeight * 100)}
                onChange={(e) => set({ lineHeight: Number(e.target.value) / 100 })}
                className="w-full accent-indigo-600"
              />
              <span className="text-xs text-slate-500">
                Tighter (100%) to airier (200%) — updates the preview instantly.
              </span>
            </div>
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-slate-900">Accent color</span>
              <div className="flex flex-wrap gap-2" role="group" aria-label="Accent color">
                {ACCENT_OPTIONS.map((accent) => (
                  <button
                    key={accent.id}
                    type="button"
                    aria-label={`Accent color ${accent.label}`}
                    aria-pressed={design.accent === accent.value}
                    onClick={() => set({ accent: accent.value })}
                    className={cn(
                      "h-8 w-8 rounded-full ring-2 ring-offset-2 transition-all",
                      design.accent === accent.value
                        ? "ring-indigo-600 scale-110"
                        : "ring-transparent hover:scale-110",
                    )}
                    style={{ backgroundColor: accent.value }}
                  />
                ))}
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="designer-date-format">Date format</Label>
              <Select
                id="designer-date-format"
                value={design.dateSeparator}
                onChange={(e) => set({ dateSeparator: e.target.value })}
              >
                {DATE_SEPARATOR_OPTIONS.map((option) => (
                  <option key={option.id} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </div>
            <SegmentedControl<HeaderAlign>
              label="Header alignment"
              options={[
                { id: "left", label: "Left" },
                { id: "center", label: "Center" },
                { id: "right", label: "Right" },
              ]}
              value={design.headerAlign}
              onChange={(headerAlign) => set({ headerAlign })}
            />
            <SegmentedControl<LocationAlign>
              label="Location alignment"
              options={[
                { id: "left", label: "Left" },
                { id: "right", label: "Right" },
              ]}
              value={design.locationAlign}
              onChange={(locationAlign) => set({ locationAlign })}
            />
            <SegmentedControl<SkillsLayout>
              label="Skills layout"
              options={SKILLS_LAYOUT_OPTIONS.map((o) => ({ id: o.id, label: o.label }))}
              value={design.skillsLayout}
              onChange={(skillsLayout) => set({ skillsLayout })}
            />
          </>
        )}
        {tab === "sections" && (
          <>
            <p className="text-sm text-slate-500">
              Show or hide sections in the preview only. Hidden sections
              stay in your document and still appear in exports.
            </p>
            {SECTION_LABELS.map((section) => (
              <label
                key={section.id}
                className="flex cursor-pointer items-center justify-between gap-3 rounded-lg border border-slate-200 px-3 py-2.5"
              >
                <span>
                  <span className="block text-sm font-medium text-slate-900">
                    {section.label}
                  </span>
                  <span className="block text-xs text-slate-500">{section.hint}</span>
                </span>
                <input
                  type="checkbox"
                  checked={visibleSections[section.id]}
                  onChange={() => onToggleSection(section.id)}
                  aria-label={`Show ${section.label} section`}
                  className="h-4 w-4 accent-indigo-600"
                />
              </label>
            ))}
          </>
        )}
        {tab === "settings" && (
          <>
            <p className="text-sm text-slate-500">
              Page margins — the padding of the Letter page, in pixels.
            </p>
            <div className="grid grid-cols-2 gap-3">
              <MarginStepper
                label="Top"
                value={design.margins.top}
                onChange={(v) => setMargin("top", v)}
              />
              <MarginStepper
                label="Right"
                value={design.margins.right}
                onChange={(v) => setMargin("right", v)}
              />
              <MarginStepper
                label="Bottom"
                value={design.margins.bottom}
                onChange={(v) => setMargin("bottom", v)}
              />
              <MarginStepper
                label="Left"
                value={design.margins.left}
                onChange={(v) => setMargin("left", v)}
              />
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
