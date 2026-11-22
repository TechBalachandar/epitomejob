// Resume Builder — /resume-builder. Teal-style editor shell:
//   entry → template library (modal) → editor.
// Entry: "I have a resume" (upload PDF/DOC/DOCX → parse → template
// library with the parsed state) or "Build fresh" (empty state → library).
// Editor: a top bar (back link, inline-editable title, template chip,
// Save, Export PDF), mode tabs (Content / Designer / Analyzer), a left
// control panel and a right live Letter-page preview on a grey canvas
// that updates instantly from every control. Co-Pilot per section/block;
// PDF/DOCX export and Save to profile. The document id lives in the URL
// (?documentId=) after creation — the W6 ?resumeId= pattern — so refresh
// keeps working.
"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  ChevronDown,
  Download,
  FilePlus2,
  LayoutTemplate,
  Plus,
  Save,
  Sparkles,
  Trash2,
  UploadCloud,
} from "lucide-react";
import {
  ApiError,
  createBuilderDocument,
  exportBuilderDocument,
  fetchBuilderDocument,
  parseBuilderResume,
  saveBuilderDocumentToProfile,
  updateBuilderDocument,
  useBuilderDocuments,
  useBuilderTemplates,
} from "@/lib/api";
import type { ResumeBuilderDocument } from "@/lib/types";
import {
  BUILDER_ACCEPT,
  DEFAULT_DESIGN,
  addEducationItem,
  addExperienceItem,
  applyCopilotOption,
  builtinTemplateById,
  copilotContextFor,
  emptyBuilderState,
  patchContact,
  removeEducationItem,
  removeExperienceItem,
  triggerBlobDownload,
  updateEducationItem,
  updateExperienceItem,
  validateBuilderFile,
} from "@/lib/resume-builder";
import type { DesignSettings } from "@/lib/resume-builder";
import type {
  BuilderSection,
  BuilderTemplate,
  CopilotOption,
  CopilotTarget,
  ResumeBuilderState,
} from "@/lib/types";
import { toast } from "@/lib/toast-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ResumePreview } from "@/components/resume-builder/resume-preview";
import { CopilotPanel } from "@/components/resume-builder/copilot-panel";
import { DesignerPanel } from "@/components/resume-builder/designer-panel";
import { AnalyzerPanel } from "@/components/resume-builder/analyzer-panel";
import { JobMatcherPanel } from "@/components/resume-builder/job-matcher-panel";
import { TemplateLibraryModal } from "@/components/resume-builder/template-library-modal";
import { ResumeHome } from "@/components/resume-builder/resume-home";
import {
  BUILDER_TOUR_SEEN_KEY,
  QuickTourModal,
} from "@/components/resume-builder/quick-tour";
import { BuilderMenu } from "@/components/resume-builder/builder-menu";
import { cn } from "@/lib/utils";

type Step = "home" | "entry" | "editor";
type EditorMode = "content" | "designer" | "analyzer" | "job-matcher";

function ImproveButton({
  label,
  onClick,
}: {
  label: string;
  onClick: () => void;
}) {
  return (
    <Button variant="outline" size="sm" aria-label={label} onClick={onClick}>
      <Sparkles className="h-3.5 w-3.5" aria-hidden />
      Improve with AI
    </Button>
  );
}

/**
 * One Content-mode section, Teal-style: a row with an include checkbox
 * (excluded sections are muted and hidden from the preview), a count,
 * an expand/collapse chevron, and a + to add an entry where that applies
 * (experience / education). The form only renders when expanded.
 */
function SectionCard({
  title,
  count,
  included,
  onToggleInclude,
  expanded,
  onToggleExpand,
  onAdd,
  addLabel,
  onImprove,
  improveLabel,
  copilotPanel,
  children,
}: {
  title: string;
  count?: string;
  included: boolean;
  onToggleInclude: () => void;
  expanded: boolean;
  onToggleExpand: () => void;
  onAdd?: () => void;
  addLabel?: string;
  onImprove?: () => void;
  improveLabel?: string;
  copilotPanel?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Card className={cn("rounded-2xl shadow-sm", !included && "opacity-60")}>
      <CardHeader>
        <div className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={included}
            onChange={onToggleInclude}
            aria-label={`Include ${title} section`}
            className="h-4 w-4 shrink-0 accent-indigo-600"
          />
          <button
            type="button"
            onClick={onToggleExpand}
            aria-expanded={expanded}
            className="flex min-w-0 flex-1 items-center gap-2 text-left"
          >
            <CardTitle className="truncate">{title}</CardTitle>
            {count && (
              <span className="shrink-0 text-xs font-normal text-slate-500">{count}</span>
            )}
            <ChevronDown
              className={cn(
                "ml-auto h-4 w-4 shrink-0 text-slate-500 transition-transform",
                expanded && "rotate-180",
              )}
              aria-hidden
            />
          </button>
          {onAdd && addLabel && (
            <Button
              variant="ghost"
              size="sm"
              aria-label={addLabel}
              onClick={onAdd}
            >
              <Plus className="h-3.5 w-3.5" aria-hidden />
            </Button>
          )}
          {onImprove && improveLabel && (
            <ImproveButton label={improveLabel} onClick={onImprove} />
          )}
        </div>
      </CardHeader>
      {expanded && (
        <CardContent className="flex flex-col gap-3">
          {children}
          {copilotPanel}
        </CardContent>
      )}
    </Card>
  );
}

type SectionAccordionProps = {
  included: boolean;
  onToggleInclude: () => void;
  expanded: boolean;
  onToggleExpand: () => void;
};

function ContactSection({
  state,
  onChange,
  onImprove,
  copilotPanel,
  ...accordion
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
} & SectionAccordionProps) {
  const c = state.contact;
  const set = (patch: Partial<typeof c>) => onChange(patchContact(state, patch));
  return (
    <SectionCard
      title="Contact"
      count={c.fullName || undefined}
      onImprove={() => onImprove({ section: "contact", label: "Contact" })}
      improveLabel="Improve contact with AI"
      copilotPanel={copilotPanel}
      {...accordion}
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="builder-full-name">Full name</Label>
        <Input
          id="builder-full-name"
          value={c.fullName}
          onChange={(e) => set({ fullName: e.target.value })}
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="builder-title">Professional title</Label>
        <Input
          id="builder-title"
          placeholder="Senior Software Engineer"
          value={c.title}
          onChange={(e) => set({ title: e.target.value })}
        />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="builder-email">Email</Label>
          <Input
            id="builder-email"
            type="email"
            value={c.email}
            onChange={(e) => set({ email: e.target.value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="builder-phone">Phone</Label>
          <Input
            id="builder-phone"
            value={c.phone}
            onChange={(e) => set({ phone: e.target.value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="builder-location">Location</Label>
          <Input
            id="builder-location"
            placeholder="Reston, VA"
            value={c.location}
            onChange={(e) => set({ location: e.target.value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="builder-linkedin">LinkedIn</Label>
          <Input
            id="builder-linkedin"
            placeholder="linkedin.com/in/you"
            value={c.linkedin}
            onChange={(e) => set({ linkedin: e.target.value })}
          />
        </div>
      </div>
    </SectionCard>
  );
}

function SummarySection({
  state,
  onChange,
  onImprove,
  copilotPanel,
  ...accordion
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
} & SectionAccordionProps) {
  return (
    <SectionCard
      title="Summary"
      count={state.summary.trim() ? `${state.summary.trim().length} chars` : undefined}
      onImprove={() => onImprove({ section: "summary", label: "Summary" })}
      improveLabel="Improve summary with AI"
      copilotPanel={copilotPanel}
      {...accordion}
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="builder-summary">Summary</Label>
        <Textarea
          id="builder-summary"
          rows={4}
          value={state.summary}
          onChange={(e) => onChange({ ...state, summary: e.target.value })}
        />
      </div>
    </SectionCard>
  );
}

function ExperienceSection({
  state,
  onChange,
  onImprove,
  copilotPanel,
  activeIndex,
  ...accordion
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
  activeIndex?: number;
} & SectionAccordionProps) {
  return (
    <SectionCard
      title="Experience"
      count={
        state.experience.length > 0
          ? `${state.experience.length} role${state.experience.length === 1 ? "" : "s"}`
          : undefined
      }
      onAdd={() => {
        onChange(addExperienceItem(state));
        // The new item's form lives inside the section, so open it.
        if (!accordion.expanded) accordion.onToggleExpand();
      }}
      addLabel="Add experience"
      {...accordion}
    >
      {state.experience.length === 0 && (
        <p className="text-sm text-slate-500">No experience yet — add your first role.</p>
      )}
      {state.experience.map((item, i) => (
        <div key={item.id} className="flex flex-col gap-3 rounded-xl border border-slate-200 p-3">
          <div className="flex items-center justify-between gap-2">
            <span className="text-sm font-medium text-slate-900">Experience {i + 1}</span>
            <span className="flex items-center gap-2">
              <ImproveButton
                label={`Improve experience ${i + 1} with AI`}
                onClick={() =>
                  onImprove({ section: "experience", index: i, label: `Experience ${i + 1}` })
                }
              />
              <Button
                variant="ghost"
                size="sm"
                aria-label={`Remove experience ${i + 1}`}
                onClick={() => onChange(removeExperienceItem(state, i))}
              >
                <Trash2 className="h-3.5 w-3.5" aria-hidden />
                Remove
              </Button>
            </span>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`builder-exp-${i}-title`}>Job title</Label>
              <Input
                id={`builder-exp-${i}-title`}
                value={item.title}
                onChange={(e) =>
                  onChange(updateExperienceItem(state, i, { title: e.target.value }))
                }
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`builder-exp-${i}-company`}>Company</Label>
              <Input
                id={`builder-exp-${i}-company`}
                value={item.company}
                onChange={(e) =>
                  onChange(updateExperienceItem(state, i, { company: e.target.value }))
                }
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`builder-exp-${i}-start`}>Start date</Label>
              <Input
                id={`builder-exp-${i}-start`}
                placeholder="2021"
                value={item.startDate}
                onChange={(e) =>
                  onChange(updateExperienceItem(state, i, { startDate: e.target.value }))
                }
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`builder-exp-${i}-end`}>End date</Label>
              <Input
                id={`builder-exp-${i}-end`}
                placeholder="Present"
                value={item.endDate}
                onChange={(e) =>
                  onChange(updateExperienceItem(state, i, { endDate: e.target.value }))
                }
              />
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`builder-exp-${i}-bullets`}>Bullets — one per line</Label>
            <Textarea
              id={`builder-exp-${i}-bullets`}
              rows={4}
              value={item.bullets.join("\n")}
              onChange={(e) =>
                onChange(
                  updateExperienceItem(state, i, { bullets: e.target.value.split("\n") }),
                )
              }
            />
          </div>
          {copilotPanel && activeIndex === i && copilotPanel}
        </div>
      ))}
    </SectionCard>
  );
}

function EducationSection({
  state,
  onChange,
  onImprove,
  copilotPanel,
  activeIndex,
  ...accordion
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
  activeIndex?: number;
} & SectionAccordionProps) {
  return (
    <SectionCard
      title="Education"
      count={
        state.education.length > 0
          ? `${state.education.length} entr${state.education.length === 1 ? "y" : "ies"}`
          : undefined
      }
      onAdd={() => {
        onChange(addEducationItem(state));
        // The new item's form lives inside the section, so open it.
        if (!accordion.expanded) accordion.onToggleExpand();
      }}
      addLabel="Add education"
      {...accordion}
    >
      {state.education.length === 0 && (
        <p className="text-sm text-slate-500">No education yet — add your first entry.</p>
      )}
      {state.education.map((item, i) => (
        <div key={item.id} className="flex flex-col gap-3 rounded-xl border border-slate-200 p-3">
          <div className="flex items-center justify-between gap-2">
            <span className="text-sm font-medium text-slate-900">Education {i + 1}</span>
            <span className="flex items-center gap-2">
              <ImproveButton
                label={`Improve education ${i + 1} with AI`}
                onClick={() =>
                  onImprove({ section: "education", index: i, label: `Education ${i + 1}` })
                }
              />
              <Button
                variant="ghost"
                size="sm"
                aria-label={`Remove education ${i + 1}`}
                onClick={() => onChange(removeEducationItem(state, i))}
              >
                <Trash2 className="h-3.5 w-3.5" aria-hidden />
                Remove
              </Button>
            </span>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`builder-edu-${i}-school`}>School</Label>
              <Input
                id={`builder-edu-${i}-school`}
                value={item.school}
                onChange={(e) =>
                  onChange(updateEducationItem(state, i, { school: e.target.value }))
                }
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`builder-edu-${i}-degree`}>Degree</Label>
              <Input
                id={`builder-edu-${i}-degree`}
                value={item.degree}
                onChange={(e) =>
                  onChange(updateEducationItem(state, i, { degree: e.target.value }))
                }
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`builder-edu-${i}-start`}>Start date</Label>
              <Input
                id={`builder-edu-${i}-start`}
                placeholder="2013"
                value={item.startDate}
                onChange={(e) =>
                  onChange(updateEducationItem(state, i, { startDate: e.target.value }))
                }
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`builder-edu-${i}-end`}>End date</Label>
              <Input
                id={`builder-edu-${i}-end`}
                placeholder="2017"
                value={item.endDate}
                onChange={(e) =>
                  onChange(updateEducationItem(state, i, { endDate: e.target.value }))
                }
              />
            </div>
          </div>
          {copilotPanel && activeIndex === i && copilotPanel}
        </div>
      ))}
    </SectionCard>
  );
}

function SkillsSection({
  state,
  onChange,
  onImprove,
  copilotPanel,
  ...accordion
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
} & SectionAccordionProps) {
  // Draft text so a trailing comma survives while typing: the input
  // value is derived from the parsed skills, so typing "Java," would
  // otherwise erase the comma the moment it is typed (split + filter).
  // The draft resets on blur, when the parsed list is shown again.
  const [draft, setDraft] = useState<string | null>(null);
  const inputValue = draft ?? state.skills.join(", ");
  return (
    <SectionCard
      title="Skills"
      count={
        state.skills.length > 0
          ? `${state.skills.length} skill${state.skills.length === 1 ? "" : "s"}`
          : undefined
      }
      onImprove={() => onImprove({ section: "skills", label: "Skills" })}
      improveLabel="Improve skills with AI"
      copilotPanel={copilotPanel}
      {...accordion}
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="builder-skills">Skills (comma separated)</Label>
        <Input
          id="builder-skills"
          placeholder="Java, Spring Boot, Kafka"
          value={inputValue}
          onChange={(e) => {
            setDraft(e.target.value);
            onChange({
              ...state,
              skills: e.target.value
                .split(",")
                .map((s) => s.trim())
                .filter(Boolean),
            });
          }}
          onBlur={() => setDraft(null)}
        />
      </div>
    </SectionCard>
  );
}

const MODE_TABS: { id: EditorMode; label: string }[] = [
  { id: "content", label: "Content" },
  { id: "designer", label: "Designer" },
  { id: "analyzer", label: "Analyzer" },
  { id: "job-matcher", label: "Job Matcher" },
];

function ResumeBuilderPageContent() {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const documentIdParam = searchParams.get("documentId");
  const modeParam = searchParams.get("mode");

  const [step, setStep] = useState<Step>(documentIdParam ? "entry" : "home");
  const [showUpload, setShowUpload] = useState(false);
  const [builderState, setBuilderState] = useState<ResumeBuilderState>(emptyBuilderState());
  const [documentId, setDocumentId] = useState<string | null>(documentIdParam);
  // Id of the document whose state is already in the editor (parsed,
  // created, or loaded). The ?documentId= effect below only fetches when
  // the URL names a different document — re-fetching one we just
  // parsed/created would waste a call and, on failure, kick the user
  // back to entry and discard the state we already hold.
  const [loadedDocId, setLoadedDocId] = useState<string | null>(null);
  const [templateId, setTemplateId] = useState<string | null>(null);
  const [docTitle, setDocTitle] = useState("");
  const [targetJd, setTargetJd] = useState("");
  const [copilotTarget, setCopilotTarget] = useState<CopilotTarget | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Param whose document load failed (derived loading flag below: a
  // failed load is not "loading" any more, so the editor stays usable).
  const [loadErrorParam, setLoadErrorParam] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState<"pdf" | "docx" | null>(null);
  const [saveLabel, setSaveLabel] = useState("");
  const [savingToProfile, setSavingToProfile] = useState(false);
  const [savedResumeId, setSavedResumeId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const menuImportInputRef = useRef<HTMLInputElement>(null);

  // Editor shell state: mode tab, Designer design, section visibility,
  // and the Template Library modal.
  const [mode, setMode] = useState<EditorMode>(() => {
    if (
      modeParam === "designer" ||
      modeParam === "analyzer" ||
      modeParam === "job-matcher"
    ) {
      return modeParam;
    }
    return "content";
  });
  const [design, setDesign] = useState<DesignSettings>(DEFAULT_DESIGN);
  const [visibleSections, setVisibleSections] = useState<Record<BuilderSection, boolean>>({
    contact: true,
    summary: true,
    experience: true,
    education: true,
    skills: true,
  });
  /** Design + section visibility are preview-only page state (never
   * persisted — see renderTemplateHtml). Reset them whenever a different
   * document is opened so one document's choices never leak into the
   * next, and a refresh never resurrects a stale preview. */
  function resetPreviewSettings() {
    setDesign(DEFAULT_DESIGN);
    setVisibleSections({
      contact: true,
      summary: true,
      experience: true,
      education: true,
      skills: true,
    });
  }
  const [showTemplateLibrary, setShowTemplateLibrary] = useState(false);
  // Content accordion: which section's form is open (one at a time, null = all closed).
  const [expandedSection, setExpandedSection] = useState<BuilderSection | null>("contact");
  // Quick Tour: shown once on first visit (localStorage "seen" flag).
  const [showTour, setShowTour] = useState(false);
  // "Start from job description": the JD paste modal + its draft.
  const [showJdModal, setShowJdModal] = useState(false);
  const [jdDraft, setJdDraft] = useState("");

  const {
    data: documents,
    isLoading: documentsLoading,
    isError: documentsError,
    // The list is only shown on the home step — a ?documentId= deep link
    // starts on "entry" and never shows it, so skip the GET there.
  } = useBuilderDocuments({ enabled: step === "home" });
  const documentList = documents ?? [];

  useEffect(() => {
    // First-visit tour: read the "seen" flag after mount so the server
    // render and the first client render agree (no modal), then show it.
    // The setState runs in a microtask, not synchronously in the effect.
    let cancelled = false;
    queueMicrotask(() => {
      if (cancelled) return;
      try {
        if (window.localStorage.getItem(BUILDER_TOUR_SEEN_KEY) !== "1") {
          setShowTour(true);
        }
      } catch {
        // Private mode / blocked storage: the tour just never shows.
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  function closeTour() {
    try {
      window.localStorage.setItem(BUILDER_TOUR_SEEN_KEY, "1");
    } catch {
      // Ignore storage failures.
    }
    setShowTour(false);
  }

  const { data: templates, isLoading: templatesLoading } = useBuilderTemplates();
  const templateList = templates ?? [];
  const selectedTemplate: BuilderTemplate =
    templateList.find((t) => t.id === templateId) ??
    builtinTemplateById(templateId);
  const hideSections = (Object.keys(visibleSections) as BuilderSection[]).filter(
    (section) => !visibleSections[section],
  );

  // ?documentId= on load: fetch the document so refresh keeps working —
  // but only when the URL names a document we do not already hold (see
  // loadedDocId above). Loading is derived: the param names a document
  // that is neither loaded nor failed. A failed load only reports the
  // error; it never moves the user away from the step they are on.
  const loadingDoc =
    documentIdParam !== null &&
    documentIdParam !== loadedDocId &&
    loadErrorParam !== documentIdParam;
  useEffect(() => {
    if (!documentIdParam || documentIdParam === loadedDocId) return;
    void fetchBuilderDocument(documentIdParam)
      .then((doc) => {
        setError(null);
        setBuilderState(doc.state);
        setDocumentId(doc.id);
        setTemplateId(doc.templateId);
        setDocTitle(doc.title ?? "");
        setSaveLabel(doc.title ?? "");
        setLoadedDocId(documentIdParam);
        resetPreviewSettings();
        // Always the editor — the template falls back to Modern when the
        // document has none, exactly like opening from the home list.
        setStep("editor");
      })
      .catch((err) => {
        setError(
          err instanceof ApiError
            ? err.message
            : "Could not load that document. Start a new one below.",
        );
        setLoadErrorParam(documentIdParam);
      });
  }, [documentIdParam, loadedDocId]);

  function setDocumentIdInUrl(id: string | null, nextMode?: EditorMode) {
    setDocumentId(id);
    const params = new URLSearchParams(searchParams.toString());
    if (id) params.set("documentId", id);
    else params.delete("documentId");
    if (nextMode && nextMode !== "content") params.set("mode", nextMode);
    else params.delete("mode");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  /** Mode tab click: switch the tab and write it back to ?mode= so a
   * refresh lands on the same tab instead of the deep-linked default. */
  function handleModeChange(nextMode: EditorMode) {
    setMode(nextMode);
    const params = new URLSearchParams(searchParams.toString());
    if (nextMode !== "content") params.set("mode", nextMode);
    else params.delete("mode");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  async function handleFile(file: File | undefined) {
    if (!file) return;
    const problem = validateBuilderFile(file);
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    setUploading(true);
    try {
      const doc = await parseBuilderResume(file);
      setBuilderState(doc.state);
      resetPreviewSettings();
      if (doc.id) {
        setDocumentIdInUrl(doc.id);
        setLoadedDocId(doc.id);
      }
      if (doc.templateId) setTemplateId(doc.templateId);
      if (doc.title) {
        setDocTitle(doc.title);
        setSaveLabel(doc.title);
      }
      setShowTemplateLibrary(true);
      toast({
        title: "Resume parsed",
        description: "Pick a template to continue editing.",
        variant: "success",
      });
    } catch (err) {
      // Server error passthrough: show the backend's message verbatim.
      setError(err instanceof ApiError ? err.message : "Upload failed. Try again.");
    } finally {
      setUploading(false);
    }
  }

  function handleBuildFresh() {
    setBuilderState(emptyBuilderState());
    setDocumentIdInUrl(null);
    setTemplateId(null);
    setDocTitle("");
    setSaveLabel("");
    setSavedResumeId(null);
    setError(null);
    resetPreviewSettings();
    setShowTemplateLibrary(true);
  }

  /** Home → open a saved document in the editor (state already in hand). */
  function handleOpenDocument(doc: ResumeBuilderDocument, nextMode?: EditorMode) {
    setBuilderState(doc.state);
    setDocumentIdInUrl(doc.id, nextMode);
    setLoadedDocId(doc.id);
    setTemplateId(doc.templateId);
    setDocTitle(doc.title ?? "");
    setSaveLabel(doc.title ?? "");
    setSavedResumeId(null);
    setError(null);
    resetPreviewSettings();
    if (nextMode) setMode(nextMode);
    setStep("editor");
  }

  /** Home → New Resume: blank document with the default template, straight
   * to the editor. The document is NOT created on the backend here — an
   * abandoned New Resume would otherwise leave an "Untitled resume" in
   * the Recent list. It is created on first Save (ensureDocumentId). */
  function handleNewResume() {
    setBuilderState(emptyBuilderState());
    setDocumentIdInUrl(null);
    setTemplateId("modern");
    setDocTitle("Untitled resume");
    setSaveLabel("Untitled resume");
    setSavedResumeId(null);
    setError(null);
    resetPreviewSettings();
    setStep("editor");
  }

  /** Home → Start from job description: paste the JD, then build fresh with it set. */
  function handleStartFromJd() {
    setJdDraft("");
    setShowJdModal(true);
  }

  function handleConfirmJd() {
    const jd = jdDraft.trim();
    if (!jd) return;
    setTargetJd(jd);
    setShowJdModal(false);
    handleBuildFresh();
    toast({
      title: "Job description set",
      description: "Co-Pilot and the Analyzer will tailor to it.",
      variant: "success",
    });
  }

  /** Menu → Duplicate this resume: a real copy via the create endpoint. */
  async function handleDuplicate() {
    const title = `${docTitle.trim() || selectedTemplate.name + " resume"} (copy)`;
    try {
      const doc = await createBuilderDocument({
        state: builderState,
        templateId: templateId ?? selectedTemplate.id,
        title,
      });
      if (doc.id) {
        setDocumentIdInUrl(doc.id);
        setLoadedDocId(doc.id);
      }
      setDocTitle(title);
      setSaveLabel(title);
      void queryClient.invalidateQueries({ queryKey: ["resume-builder", "documents"] });
      toast({ title: "Duplicated", description: `Created “${title}”.`, variant: "success" });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not duplicate the resume.");
    }
  }

  /** Menu / top bar → back to the resume list. */
  function handleBackToHome() {
    setDocumentIdInUrl(null);
    setLoadedDocId(null);
    setError(null);
    void queryClient.invalidateQueries({ queryKey: ["resume-builder", "documents"] });
    setStep("home");
  }

  const hasContent =
    builderState.contact.fullName.trim() !== "" ||
    builderState.contact.title.trim() !== "" ||
    builderState.contact.email.trim() !== "" ||
    builderState.contact.phone.trim() !== "" ||
    builderState.contact.location.trim() !== "" ||
    builderState.contact.linkedin.trim() !== "" ||
    builderState.summary.trim() !== "" ||
    builderState.experience.length > 0 ||
    builderState.education.length > 0 ||
    builderState.skills.length > 0;

  function sectionAccordionProps(section: BuilderSection): SectionAccordionProps {
    return {
      included: visibleSections[section],
      onToggleInclude: () =>
        setVisibleSections((prev) => ({ ...prev, [section]: !prev[section] })),
      expanded: expandedSection === section,
      onToggleExpand: () =>
        setExpandedSection((prev) => (prev === section ? null : section)),
    };
  }

  /** Pick a template from the Template Library. Do NOT reset the preview
   * settings here: when called from the editor to switch templates on an
   * existing document, the user's design customisations must survive.
   * The new-document path is covered because every route that opens the
   * library for a new document goes through handleBuildFresh / handleFile
   * first, and those reset. A future entry path that opens the library
   * without them must reset there, not here. */
  async function handleSelectTemplate(template: BuilderTemplate) {
    setTemplateId(template.id);
    setError(null);
    const title = docTitle.trim() || `${template.name} resume`;
    setDocTitle(title);
    if (!saveLabel.trim()) setSaveLabel(title);
    try {
      if (documentId) {
        await updateBuilderDocument(documentId, { templateId: template.id, title });
      } else {
        const doc = await createBuilderDocument({
          state: builderState,
          templateId: template.id,
          title,
        });
        if (doc.id) {
          setDocumentIdInUrl(doc.id);
          setLoadedDocId(doc.id);
        }
      }
      setShowTemplateLibrary(false);
      setStep("editor");
    } catch (err) {
      // The document still works locally even if the first save fails —
      // the editor's Save button retries it.
      setError(
        err instanceof ApiError
          ? err.message
          : "Could not save the document yet — you can keep editing and save from the editor.",
      );
      setShowTemplateLibrary(false);
      setStep("editor");
    }
  }

  /** Persist the document first when export / save-to-profile needs an id. */
  async function ensureDocumentId(): Promise<string | null> {
    if (documentId) return documentId;
    try {
      const doc = await createBuilderDocument({
        state: builderState,
        templateId: templateId ?? selectedTemplate.id,
        title: docTitle.trim() || `${selectedTemplate.name} resume`,
      });
      if (doc.id) {
        setDocumentIdInUrl(doc.id);
        setLoadedDocId(doc.id);
        return doc.id;
      }
      return null;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the document.");
      return null;
    }
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const id = await ensureDocumentId();
      if (!id) return;
      await updateBuilderDocument(id, {
        state: builderState,
        templateId: templateId ?? selectedTemplate.id,
        title: docTitle.trim() || undefined,
      });
      toast({ title: "Document saved", variant: "success" });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the document.");
    } finally {
      setSaving(false);
    }
  }

  async function handleExport(format: "pdf" | "docx") {
    setExporting(format);
    setError(null);
    try {
      const id = await ensureDocumentId();
      if (!id) return;
      const { blob, filename } = await exportBuilderDocument(id, format);
      triggerBlobDownload(blob, filename);
      toast({ title: `Downloaded ${filename}`, variant: "success" });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : `Could not export ${format.toUpperCase()}.`);
    } finally {
      setExporting(null);
    }
  }

  async function handleSaveToProfile() {
    const label = saveLabel.trim() || docTitle.trim() || `${selectedTemplate.name} resume`;
    setSavingToProfile(true);
    setError(null);
    try {
      const id = await ensureDocumentId();
      if (!id) return;
      const result = await saveBuilderDocumentToProfile(id, label);
      setSavedResumeId(result.resumeId);
      toast({
        title: "Saved to profile",
        description: `Saved as resume ${result.resumeId}.`,
        variant: "success",
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save to profile.");
    } finally {
      setSavingToProfile(false);
    }
  }

  function handleApplyCopilot(option: CopilotOption) {
    if (!copilotTarget) return;
    setBuilderState((prev) => applyCopilotOption(prev, copilotTarget, option));
  }

  const copilotPanel = copilotTarget ? (
    <CopilotPanel
      key={`${copilotTarget.section}-${copilotTarget.index ?? ""}`}
      documentId={documentId}
      target={copilotTarget}
      contextText={copilotContextFor(builderState, copilotTarget)}
      targetJd={targetJd}
      state={builderState}
      onApply={handleApplyCopilot}
      onClose={() => setCopilotTarget(null)}
    />
  ) : null;

  const onChange = (next: ResumeBuilderState) => setBuilderState(next);
  const onImprove = (target: CopilotTarget) => {
    // The Co-Pilot panel renders inside the section's form, so expand the
    // section or the panel would be invisible inside a collapsed card.
    setExpandedSection(target.section);
    setCopilotTarget(target);
  };

  return (
    <div className="flex min-w-0 flex-col gap-6">
      {step === "home" && (
        <ResumeHome
          documents={documentList}
          loading={documentsLoading}
          error={
            documentsError
              ? "Could not load your resumes. Make sure you are signed in."
              : null
          }
          onNew={() => void handleNewResume()}
          onFromJobDescription={handleStartFromJd}
          onFromTemplate={handleBuildFresh}
          onImport={() => {
            setShowUpload(true);
            setStep("entry");
          }}
          onOpen={(doc) => handleOpenDocument(doc)}
          onMatchJob={(doc) => handleOpenDocument(doc, "job-matcher")}
        />
      )}

      {step === "entry" && (
        <>
          <div>
            <button
              type="button"
              onClick={handleBackToHome}
              className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900"
            >
              <ArrowLeft className="h-4 w-4" aria-hidden />
              Back to Resumes
            </button>
            <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-900">Resume Builder</h1>
            <p className="mt-2 max-w-2xl text-base text-slate-500">
              Upload your resume to start from what you have, or build fresh
              from a template — then edit, design, and check it against the
              job you want, and download it as PDF or DOCX.
            </p>
          </div>

          {error && (
            <p role="alert" className="text-sm text-rose-600">
              {error}
            </p>
          )}
          {loadingDoc && <p className="text-sm text-slate-500">Loading document…</p>}

          <div className="grid gap-4 sm:grid-cols-2">
            <Card className="rounded-2xl shadow-sm">
              <CardHeader>
                <CardTitle className="text-lg">I have a resume</CardTitle>
                <CardDescription>
                  Upload a PDF, DOC or DOCX. We parse it into editable
                  sections — nothing is overwritten until you save.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Button
                  variant="outline"
                  onClick={() => setShowUpload((v) => !v)}
                  aria-expanded={showUpload}
                >
                  <UploadCloud className="h-4 w-4" aria-hidden />
                  {showUpload ? "Hide upload" : "Upload a resume"}
                </Button>
              </CardContent>
            </Card>
            <Card className="rounded-2xl shadow-sm">
              <CardHeader>
                <CardTitle className="text-lg">Build fresh</CardTitle>
                <CardDescription>
                  Start from a blank resume and pick a template. Fill in
                  each section — Co-Pilot can help rewrite them.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Button onClick={handleBuildFresh}>
                  <FilePlus2 className="h-4 w-4" aria-hidden />
                  Build fresh
                </Button>
              </CardContent>
            </Card>
          </div>

          {showUpload && (
            <Card className="rounded-2xl shadow-sm">
              <CardHeader>
                <CardTitle>Upload your resume</CardTitle>
                <CardDescription>
                  PDF, DOC or DOCX, up to 10 MB.
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                <div
                  data-testid="builder-dropzone"
                  className={`flex min-h-32 flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed p-6 text-center ${
                    dragOver ? "border-indigo-500 bg-indigo-50" : "border-slate-300"
                  }`}
                  onDragOver={(e) => {
                    e.preventDefault();
                    setDragOver(true);
                  }}
                  onDragLeave={() => setDragOver(false)}
                  onDrop={(e) => {
                    e.preventDefault();
                    setDragOver(false);
                    void handleFile(e.dataTransfer.files?.[0]);
                  }}
                >
                  <UploadCloud className="h-6 w-6 text-slate-400" aria-hidden />
                  <p className="text-sm text-slate-600">
                    Drag your resume here, or choose a file.
                  </p>
                  <input
                    ref={fileInputRef}
                    data-testid="builder-file-input"
                    type="file"
                    accept={BUILDER_ACCEPT}
                    className="hidden"
                    onChange={(e) => void handleFile(e.target.files?.[0])}
                  />
                  <Button
                    variant="outline"
                    disabled={uploading}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    {uploading ? "Parsing…" : "Choose file"}
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}
        </>
      )}

      {step === "editor" && (
        <div className="flex min-w-0 flex-col gap-4">
          {/* Top bar: back, inline title, template, save + export, menu. */}
          <div className="sticky top-0 z-20 -mx-4 border-b border-slate-200 bg-white/95 px-4 py-3 backdrop-blur sm:-mx-6 sm:px-6">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
              <button
                type="button"
                onClick={handleBackToHome}
                className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900"
              >
                <ArrowLeft className="h-4 w-4" aria-hidden />
                Back to Resumes
              </button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowTemplateLibrary(true)}
              >
                <LayoutTemplate className="h-4 w-4" aria-hidden />
                Change template
              </Button>
              <input
                aria-label="Document title"
                value={docTitle}
                placeholder={`${selectedTemplate.name} resume`}
                onChange={(e) => setDocTitle(e.target.value)}
                className="min-w-40 flex-1 rounded-md border border-transparent bg-transparent px-2 py-1 text-lg font-bold tracking-tight text-slate-900 hover:border-slate-200 focus:border-indigo-500 focus:outline-none"
              />
              <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
                Template: {selectedTemplate.name}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={saving}
                onClick={() => void handleSave()}
              >
                <Save className="h-4 w-4" aria-hidden />
                {saving ? "Saving…" : "Save"}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={exporting !== null}
                onClick={() => void handleExport("docx")}
              >
                <Download className="h-4 w-4" aria-hidden />
                {exporting === "docx" ? "Exporting…" : "Download DOCX"}
              </Button>
              <Button
                size="sm"
                disabled={exporting !== null}
                onClick={() => void handleExport("pdf")}
              >
                <Download className="h-4 w-4" aria-hidden />
                {exporting === "pdf" ? "Exporting…" : "Export PDF"}
              </Button>
              <BuilderMenu
                duplicateDisabled={!hasContent}
                onImport={() => menuImportInputRef.current?.click()}
                onDuplicate={() => void handleDuplicate()}
                onQuickTour={() => setShowTour(true)}
                onBackToResumes={handleBackToHome}
              />
              <input
                ref={menuImportInputRef}
                id="builder-menu-import-input"
                data-testid="builder-menu-import-input"
                type="file"
                accept={BUILDER_ACCEPT}
                className="hidden"
                onChange={(e) => void handleFile(e.target.files?.[0])}
              />
            </div>
            {/* Mode tabs: Content / Designer / Analyzer. */}
            <div
              role="tablist"
              aria-label="Resume builder mode"
              className="mt-3 flex gap-1 rounded-lg bg-slate-100 p-1 sm:max-w-md"
            >
              {MODE_TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  role="tab"
                  aria-selected={mode === tab.id}
                  onClick={() => handleModeChange(tab.id)}
                  className={cn(
                    "flex-1 rounded-md px-3 py-1.5 text-sm transition-colors",
                    mode === tab.id
                      ? "bg-white font-semibold text-slate-900 shadow-sm"
                      : "text-slate-600 hover:text-slate-900",
                  )}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          {error && (
            <p role="alert" className="text-sm text-rose-600">
              {error}
            </p>
          )}
          {loadingDoc && <p className="text-sm text-slate-500">Loading document…</p>}

          <div className="grid min-w-0 gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
            {/* Left control panel — switches with the mode tab. */}
            <div className="flex min-w-0 flex-col gap-4">
              {mode === "content" && (
                <>
                  <Card className="rounded-2xl shadow-sm">
                    <CardHeader>
                      <CardTitle>Document</CardTitle>
                      <CardDescription>
                        Target the resume at one job, and save it to your
                        profile when it is ready.
                      </CardDescription>
                    </CardHeader>
                    <CardContent className="flex flex-col gap-4">
                      <div className="flex flex-col gap-1.5">
                        <Label htmlFor="builder-target-jd">
                          Target job description (optional)
                        </Label>
                        <Textarea
                          id="builder-target-jd"
                          rows={3}
                          placeholder="Paste the job description you are targeting — Co-Pilot tailors its suggestions to it."
                          value={targetJd}
                          onChange={(e) => setTargetJd(e.target.value)}
                        />
                      </div>
                      <div className="flex flex-col gap-1.5 border-t border-slate-100 pt-4">
                        <Label htmlFor="builder-save-label">Save to profile as</Label>
                        <div className="flex flex-wrap items-end gap-2">
                          <Input
                            id="builder-save-label"
                            value={saveLabel}
                            placeholder={`${selectedTemplate.name} resume`}
                            onChange={(e) => setSaveLabel(e.target.value)}
                            className="min-w-40 flex-1"
                          />
                          <Button
                            variant="outline"
                            disabled={savingToProfile}
                            onClick={() => void handleSaveToProfile()}
                          >
                            {savingToProfile ? "Saving…" : "Save to profile"}
                          </Button>
                        </div>
                        {savedResumeId && (
                          <p className="text-sm text-emerald-700">
                            Saved as resume {savedResumeId}.{" "}
                            <Link href="/resumes" className="font-medium underline">
                              View in Resumes
                            </Link>
                          </p>
                        )}
                      </div>
                    </CardContent>
                  </Card>
                  <ContactSection
                    state={builderState}
                    onChange={onChange}
                    onImprove={onImprove}
                    copilotPanel={copilotTarget?.section === "contact" ? copilotPanel : undefined}
                    {...sectionAccordionProps("contact")}
                  />
                  <SummarySection
                    state={builderState}
                    onChange={onChange}
                    onImprove={onImprove}
                    copilotPanel={copilotTarget?.section === "summary" ? copilotPanel : undefined}
                    {...sectionAccordionProps("summary")}
                  />
                  <ExperienceSection
                    state={builderState}
                    onChange={onChange}
                    onImprove={onImprove}
                    copilotPanel={copilotTarget?.section === "experience" ? copilotPanel : undefined}
                    activeIndex={copilotTarget?.section === "experience" ? copilotTarget.index : undefined}
                    {...sectionAccordionProps("experience")}
                  />
                  <EducationSection
                    state={builderState}
                    onChange={onChange}
                    onImprove={onImprove}
                    copilotPanel={copilotTarget?.section === "education" ? copilotPanel : undefined}
                    activeIndex={copilotTarget?.section === "education" ? copilotTarget.index : undefined}
                    {...sectionAccordionProps("education")}
                  />
                  <SkillsSection
                    state={builderState}
                    onChange={onChange}
                    onImprove={onImprove}
                    copilotPanel={copilotTarget?.section === "skills" ? copilotPanel : undefined}
                    {...sectionAccordionProps("skills")}
                  />
                </>
              )}
              {mode === "designer" && (
                <DesignerPanel
                  design={design}
                  onDesignChange={setDesign}
                  visibleSections={visibleSections}
                  onToggleSection={(section) =>
                    setVisibleSections((prev) => ({ ...prev, [section]: !prev[section] }))
                  }
                  onResetDesign={() => setDesign(DEFAULT_DESIGN)}
                />
              )}
              {mode === "analyzer" && (
                <AnalyzerPanel
                  state={builderState}
                  targetJd={targetJd}
                  onTargetJdChange={setTargetJd}
                />
              )}
              {mode === "job-matcher" && (
                <JobMatcherPanel
                  state={builderState}
                  targetJd={targetJd}
                  onTargetJdChange={setTargetJd}
                />
              )}
            </div>

            {/* Right: live Letter-page preview on a grey canvas. */}
            <div className="min-w-0">
              <div className="lg:sticky lg:top-32">
                <h2 className="mb-2 text-sm font-semibold text-slate-900">Live preview</h2>
                <ResumePreview
                  template={selectedTemplate}
                  state={builderState}
                  design={design}
                  hideSections={hideSections}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {showTemplateLibrary && (
        <TemplateLibraryModal
          templates={templateList}
          state={builderState}
          selectedId={templateId}
          onSelect={(template) => setTemplateId(template.id)}
          onUse={() => {
            const template =
              templateList.find((t) => t.id === templateId) ??
              builtinTemplateById(templateId);
            void handleSelectTemplate(template);
          }}
          onClose={() => setShowTemplateLibrary(false)}
          loading={templatesLoading}
        />
      )}

      {showTour && <QuickTourModal open onClose={closeTour} />}

      {showJdModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="Start from job description"
        >
          <div className="w-full max-w-xl rounded-2xl bg-white p-6 shadow-xl">
            <h2 className="text-lg font-semibold text-slate-900">
              Start from a job description
            </h2>
            <p className="mt-1 text-sm text-slate-600">
              Paste the posting you are targeting. The Analyzer and Co-Pilot
              will tailor this resume to it, and Job Matcher will score the
              fit — all computed from the text you paste.
            </p>
            <div className="mt-4 flex flex-col gap-1.5">
              <Label htmlFor="jd-paste">Job description</Label>
              <Textarea
                id="jd-paste"
                value={jdDraft}
                onChange={(e) => setJdDraft(e.target.value)}
                rows={8}
                placeholder="Paste the job description here…"
                className="resize-y"
              />
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="outline" onClick={() => setShowJdModal(false)}>
                Cancel
              </Button>
              <Button disabled={!jdDraft.trim()} onClick={handleConfirmJd}>
                Use this description
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// useSearchParams() needs a Suspense boundary so the page still
// prerenders (Next.js CSR bailout rule for search params).
export default function ResumeBuilderPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Loading resume builder…</p>}>
      <ResumeBuilderPageContent />
    </Suspense>
  );
}
