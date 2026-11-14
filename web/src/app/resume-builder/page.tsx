// Resume Builder — /resume-builder. Three steps in one page:
//   entry → templates → editor.
// Entry: "I have a resume" (upload PDF/DOC/DOCX → parse → templates with
// the parsed state) or "Build fresh" (empty state → templates).
// Templates: live gallery from GET /resume-builder/templates.
// Editor: editable fields on the left, live preview on the right, one
// document state object; Co-Pilot per section/block; PDF/DOCX export and
// Save to profile. The document id lives in the URL (?documentId=) after
// creation — the W6 ?resumeId= pattern — so refresh keeps working.
"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  ArrowLeft,
  Download,
  FilePlus2,
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
  useBuilderTemplates,
} from "@/lib/api";
import {
  BUILDER_ACCEPT,
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
import type {
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
import { TemplateGallery } from "@/components/resume-builder/template-gallery";
import { ResumePreview } from "@/components/resume-builder/resume-preview";
import { CopilotPanel } from "@/components/resume-builder/copilot-panel";

type Step = "entry" | "templates" | "editor";

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

function SectionCard({
  title,
  onImprove,
  improveLabel,
  copilotPanel,
  children,
}: {
  title: string;
  onImprove?: () => void;
  improveLabel?: string;
  copilotPanel?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>{title}</CardTitle>
          {onImprove && improveLabel && (
            <ImproveButton label={improveLabel} onClick={onImprove} />
          )}
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {children}
        {copilotPanel}
      </CardContent>
    </Card>
  );
}

function ContactSection({
  state,
  onChange,
  onImprove,
  copilotPanel,
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
}) {
  const c = state.contact;
  const set = (patch: Partial<typeof c>) => onChange(patchContact(state, patch));
  return (
    <SectionCard
      title="Contact"
      onImprove={() => onImprove({ section: "contact", label: "Contact" })}
      improveLabel="Improve contact with AI"
      copilotPanel={copilotPanel}
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
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
}) {
  return (
    <SectionCard
      title="Summary"
      onImprove={() => onImprove({ section: "summary", label: "Summary" })}
      improveLabel="Improve summary with AI"
      copilotPanel={copilotPanel}
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
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
  activeIndex?: number;
}) {
  return (
    <SectionCard title="Experience">
      {state.experience.length === 0 && (
        <p className="text-sm text-slate-500">No experience yet — add your first role.</p>
      )}
      {state.experience.map((item, i) => (
        <div key={item.id} className="flex flex-col gap-3 rounded-lg border border-slate-200 p-3">
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
      <div>
        <Button variant="outline" size="sm" onClick={() => onChange(addExperienceItem(state))}>
          <Plus className="h-3.5 w-3.5" aria-hidden />
          Add experience
        </Button>
      </div>
    </SectionCard>
  );
}

function EducationSection({
  state,
  onChange,
  onImprove,
  copilotPanel,
  activeIndex,
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
  activeIndex?: number;
}) {
  return (
    <SectionCard title="Education">
      {state.education.length === 0 && (
        <p className="text-sm text-slate-500">No education yet — add your first entry.</p>
      )}
      {state.education.map((item, i) => (
        <div key={item.id} className="flex flex-col gap-3 rounded-lg border border-slate-200 p-3">
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
      <div>
        <Button variant="outline" size="sm" onClick={() => onChange(addEducationItem(state))}>
          <Plus className="h-3.5 w-3.5" aria-hidden />
          Add education
        </Button>
      </div>
    </SectionCard>
  );
}

function SkillsSection({
  state,
  onChange,
  onImprove,
  copilotPanel,
}: {
  state: ResumeBuilderState;
  onChange: (next: ResumeBuilderState) => void;
  onImprove: (target: CopilotTarget) => void;
  copilotPanel?: ReactNode;
}) {
  return (
    <SectionCard
      title="Skills"
      onImprove={() => onImprove({ section: "skills", label: "Skills" })}
      improveLabel="Improve skills with AI"
      copilotPanel={copilotPanel}
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="builder-skills">Skills (comma separated)</Label>
        <Input
          id="builder-skills"
          placeholder="Java, Spring Boot, Kafka"
          value={state.skills.join(", ")}
          onChange={(e) =>
            onChange({
              ...state,
              skills: e.target.value
                .split(",")
                .map((s) => s.trim())
                .filter(Boolean),
            })
          }
        />
      </div>
    </SectionCard>
  );
}

function ResumeBuilderPageContent() {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();
  const documentIdParam = searchParams.get("documentId");

  const [step, setStep] = useState<Step>("entry");
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

  const { data: templates, isLoading: templatesLoading } = useBuilderTemplates();
  const templateList = templates ?? [];
  const selectedTemplate: BuilderTemplate =
    templateList.find((t) => t.id === templateId) ??
    builtinTemplateById(templateId);

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
        setStep(doc.templateId ? "editor" : "templates");
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

  function setDocumentIdInUrl(id: string | null) {
    setDocumentId(id);
    const params = new URLSearchParams(searchParams.toString());
    if (id) params.set("documentId", id);
    else params.delete("documentId");
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
      if (doc.id) {
        setDocumentIdInUrl(doc.id);
        setLoadedDocId(doc.id);
      }
      if (doc.templateId) setTemplateId(doc.templateId);
      if (doc.title) {
        setDocTitle(doc.title);
        setSaveLabel(doc.title);
      }
      setStep("templates");
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
    setStep("templates");
  }

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
      setStep("editor");
    } catch (err) {
      // The document still works locally even if the first save fails —
      // the editor's Save button retries it.
      setError(
        err instanceof ApiError
          ? err.message
          : "Could not save the document yet — you can keep editing and save from the editor.",
      );
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
  const onImprove = (target: CopilotTarget) => setCopilotTarget(target);

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Resume Builder</h1>
        <p className="mt-1 text-sm text-slate-500">
          Upload your resume to start from what you have, or build fresh from
          a template — then edit, improve each section with AI, and download
          it as PDF or DOCX.
        </p>
      </div>

      {error && (
        <p role="alert" className="text-sm text-rose-600">
          {error}
        </p>
      )}
      {loadingDoc && <p className="text-sm text-slate-500">Loading document…</p>}

      {step === "entry" && (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>I have a resume</CardTitle>
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
            <Card>
              <CardHeader>
                <CardTitle>Build fresh</CardTitle>
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
            <Card>
              <CardHeader>
                <CardTitle>Upload your resume</CardTitle>
                <CardDescription>
                  PDF, DOC or DOCX, up to 10 MB.
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                <div
                  data-testid="builder-dropzone"
                  className={`flex min-h-32 flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center ${
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

      {step === "templates" && (
        <div className="flex flex-col gap-4">
          <div>
            <Button variant="ghost" size="sm" onClick={() => setStep("entry")}>
              <ArrowLeft className="h-4 w-4" aria-hidden />
              Back
            </Button>
          </div>
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Choose a template</h2>
            <p className="mt-1 text-sm text-slate-500">
              Each thumbnail is a live preview of the template with your
              content — pick one to start editing.
            </p>
          </div>
          {templatesLoading && <p className="text-sm text-slate-500">Loading templates…</p>}
          {!templatesLoading && templateList.length === 0 && (
            <p className="text-sm text-slate-500">No templates available.</p>
          )}
          <TemplateGallery
            templates={templateList}
            state={builderState}
            selectedId={templateId}
            onSelect={(t) => void handleSelectTemplate(t)}
          />
        </div>
      )}

      {step === "editor" && (
        <div className="flex min-w-0 flex-col gap-4">
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="ghost" size="sm" onClick={() => setStep("templates")}>
              <ArrowLeft className="h-4 w-4" aria-hidden />
              Change template
            </Button>
            <span className="text-sm font-medium text-slate-700">
              Template: {selectedTemplate.name}
            </span>
          </div>

          <Card>
            <CardContent className="flex flex-col gap-3 pt-5">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="builder-doc-title">Document title</Label>
                <Input
                  id="builder-doc-title"
                  value={docTitle}
                  placeholder={`${selectedTemplate.name} resume`}
                  onChange={(e) => setDocTitle(e.target.value)}
                  className="sm:w-72"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="builder-target-jd">Target job description (optional)</Label>
                <Textarea
                  id="builder-target-jd"
                  rows={3}
                  placeholder="Paste the job description you are targeting — Co-Pilot tailors its suggestions to it."
                  value={targetJd}
                  onChange={(e) => setTargetJd(e.target.value)}
                />
              </div>
              <div className="flex flex-wrap gap-2">
                <Button disabled={saving} onClick={() => void handleSave()}>
                  <Save className="h-4 w-4" aria-hidden />
                  {saving ? "Saving…" : "Save"}
                </Button>
                <Button
                  variant="outline"
                  disabled={exporting !== null}
                  onClick={() => void handleExport("pdf")}
                >
                  <Download className="h-4 w-4" aria-hidden />
                  {exporting === "pdf" ? "Exporting…" : "Download PDF"}
                </Button>
                <Button
                  variant="outline"
                  disabled={exporting !== null}
                  onClick={() => void handleExport("docx")}
                >
                  <Download className="h-4 w-4" aria-hidden />
                  {exporting === "docx" ? "Exporting…" : "Download DOCX"}
                </Button>
              </div>
              <div className="flex flex-wrap items-end gap-2 border-t border-slate-100 pt-3">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="builder-save-label">Save to profile as</Label>
                  <Input
                    id="builder-save-label"
                    value={saveLabel}
                    placeholder={`${selectedTemplate.name} resume`}
                    onChange={(e) => setSaveLabel(e.target.value)}
                    className="sm:w-72"
                  />
                </div>
                <Button
                  variant="outline"
                  disabled={savingToProfile}
                  onClick={() => void handleSaveToProfile()}
                >
                  {savingToProfile ? "Saving…" : "Save to profile"}
                </Button>
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

          <div className="grid min-w-0 gap-6 lg:grid-cols-2">
            <div className="flex min-w-0 flex-col gap-4">
              <ContactSection
                state={builderState}
                onChange={onChange}
                onImprove={onImprove}
                copilotPanel={copilotTarget?.section === "contact" ? copilotPanel : undefined}
              />
              <SummarySection
                state={builderState}
                onChange={onChange}
                onImprove={onImprove}
                copilotPanel={copilotTarget?.section === "summary" ? copilotPanel : undefined}
              />
              <ExperienceSection
                state={builderState}
                onChange={onChange}
                onImprove={onImprove}
                copilotPanel={copilotTarget?.section === "experience" ? copilotPanel : undefined}
                activeIndex={copilotTarget?.section === "experience" ? copilotTarget.index : undefined}
              />
              <EducationSection
                state={builderState}
                onChange={onChange}
                onImprove={onImprove}
                copilotPanel={copilotTarget?.section === "education" ? copilotPanel : undefined}
                activeIndex={copilotTarget?.section === "education" ? copilotTarget.index : undefined}
              />
              <SkillsSection
                state={builderState}
                onChange={onChange}
                onImprove={onImprove}
                copilotPanel={copilotTarget?.section === "skills" ? copilotPanel : undefined}
              />
            </div>
            <div className="min-w-0">
              <div className="lg:sticky lg:top-20">
                <h2 className="mb-2 text-sm font-semibold text-slate-900">Live preview</h2>
                <ResumePreview template={selectedTemplate} state={builderState} />
              </div>
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
