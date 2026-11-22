// Interview Prep from the job description.
// The user picks a job from the live feed or pastes a job description,
// and the Coach drafts a model answer for each likely interview question,
// grounded in the user's own resume (Resume Builder document first,
// profile fallback). Questions come from the existing
// GET /api/interviews/questions endpoint when a job is picked; model
// answers come from the Coach (POST /api/coach/chat) — the Coach needs
// Ollama on the user's Mac, so a failure here is reported honestly, and
// no canned answers are ever shown. The backend sessions/answers routes
// only score the user's own typed answers, so they cannot supply model
// answers and are not used here.
"use client";

import { useState } from "react";
import {
  ApiError,
  sendCoachMessage,
  useBuilderDocuments,
  useInterviewQuestions,
  useProfile,
} from "@/lib/api";
import type { CoachCitation, Job } from "@/lib/types";
import {
  buildPrepPrompt,
  buildResumeContext,
  checkGrounding,
  parsePrepReply,
  validatePastedJD,
  type PrepItem,
} from "@/lib/interview-prep";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

interface PrepResult {
  items: PrepItem[];
  unparsed: string | null;
  citations: CoachCitation[];
  specialistsUsed: string[];
}

/** "job:<uuid>" → "job:10000000…" so citations stay readable. */
function shortDocId(docId: string): string {
  const [kind, id] = docId.split(":");
  if (!id) return docId;
  return `${kind}:${id.slice(0, 8)}…`;
}

function Grounding({ answer, known }: {
  answer: string;
  known: { companies: string[]; roles: string[]; skills: string[]; schools: string[] };
}) {
  const check = checkGrounding(answer, known);
  const cited = [...check.citedCompanies, ...check.citedRoles, ...check.citedSkills];
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {cited.length > 0 ? (
          cited.map((c) => (
            <Badge key={c} variant="success">Cites: {c}</Badge>
          ))
        ) : (
          <Badge variant="secondary">No resume experience cited in this answer</Badge>
        )}
      </div>
      {check.flaggedPhrases.length > 0 && (
        <p role="alert" className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
          Check before using — this mentions {check.flaggedPhrases.join(", ")}, which
          isn&apos;t in your resume. It may be paraphrased, or the Coach may have
          filled a gap.
        </p>
      )}
    </div>
  );
}

export function InterviewPrep({ jobs }: { jobs: Job[] }) {
  const [mode, setMode] = useState<"job" | "paste">("job");
  const [pickedJobId, setPickedJobId] = useState("");
  const [pastedJD, setPastedJD] = useState("");
  const [jdError, setJdError] = useState<string | null>(null);
  const [result, setResult] = useState<PrepResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);

  const selectedJobId = pickedJobId || jobs[0]?.id || "";
  const selectedJob = jobs.find((j) => j.id === selectedJobId) ?? null;

  const { data: profile } = useProfile();
  const { data: builderDocuments } = useBuilderDocuments();
  const {
    data: questions,
    isLoading: questionsLoading,
    isError: questionsError,
  } = useInterviewQuestions(mode === "job" ? selectedJobId || null : null);

  const resumeContext = buildResumeContext({
    profile: profile ?? null,
    builderDocuments: builderDocuments ?? null,
  });
  const known = {
    companies: resumeContext.companies,
    roles: resumeContext.roles,
    skills: resumeContext.skills,
    schools: resumeContext.schools,
  };

  async function generate() {
    setError(null);
    setJdError(null);
    if (mode === "paste") {
      const jdErr = validatePastedJD(pastedJD);
      if (jdErr) {
        setJdError(jdErr);
        return;
      }
    }
    if (!resumeContext.text) {
      setError(
        "No resume on file — add your experience to your profile or the Resume Builder first, so model answers can be grounded in your real experience.",
      );
      return;
    }
    setIsGenerating(true);
    try {
      const prompt = buildPrepPrompt({
        job:
          mode === "job" && selectedJob
            ? {
                title: selectedJob.title,
                company: selectedJob.company,
                summary: selectedJob.summary,
                skills: selectedJob.skills,
              }
            : null,
        pastedJD: mode === "paste" ? pastedJD : undefined,
        questions:
          mode === "job"
            ? (questions ?? []).map((q) => q.text)
            : undefined,
        resumeContext,
      });
      const res = await sendCoachMessage(prompt);
      const parsed = parsePrepReply(res.reply ?? "");
      setResult({
        items: parsed.items,
        unparsed: parsed.unparsed,
        citations: res.citations ?? [],
        specialistsUsed: res.specialistsUsed ?? [],
      });
    } catch (err) {
      const status = err instanceof ApiError ? err.status : null;
      setError(
        status === 502 || status === 503 || status === 504
          ? "Could not reach the Coach — it needs Ollama running on your Mac. Start Ollama and try again; no model answers were generated."
          : err instanceof ApiError
            ? `Could not generate prep: ${err.message}`
            : "Could not generate prep. Try again.",
      );
    } finally {
      setIsGenerating(false);
    }
  }

  const canGenerate =
    !isGenerating &&
    (mode === "paste" ? pastedJD.trim().length > 0 : !!selectedJob) &&
    !questionsLoading;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Interview prep from the job description</CardTitle>
        <CardDescription>
          Pick a job or paste a job description, and the Coach drafts a model
          answer for each likely question — grounded in {resumeContext.sourceLabel}.
          Check anything flagged before using it in an interview.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex gap-2" role="group" aria-label="Prep source">
          <Button
            variant={mode === "job" ? "default" : "outline"}
            className="w-fit"
            onClick={() => setMode("job")}
          >
            Pick a job
          </Button>
          <Button
            variant={mode === "paste" ? "default" : "outline"}
            className="w-fit"
            onClick={() => setMode("paste")}
          >
            Paste a job description
          </Button>
        </div>

        {mode === "job" ? (
          <div className="flex flex-col gap-2">
            <Select
              value={selectedJobId}
              onChange={(e) => setPickedJobId(e.target.value)}
              aria-label="Pick a job to prepare for"
            >
              {jobs.map((j) => (
                <option key={j.id} value={j.id}>
                  {j.title} — {j.company}
                </option>
              ))}
            </Select>
            {selectedJob && (
              <p className="text-sm text-slate-500">{selectedJob.summary}</p>
            )}
            {selectedJob && selectedJob.skills.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {selectedJob.skills.map((s) => (
                  <Badge key={s} variant="secondary">{s}</Badge>
                ))}
              </div>
            )}
            {questionsLoading && (
              <p className="text-sm text-slate-500">Loading questions…</p>
            )}
            {questionsError && (
              <p className="text-sm text-slate-500">
                Could not load the role&apos;s questions — the Coach will
                generate likely questions from the job instead.
              </p>
            )}
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            <Textarea
              rows={8}
              value={pastedJD}
              onChange={(e) => setPastedJD(e.target.value)}
              placeholder="Paste the full job description here…"
              aria-label="Paste a job description"
            />
            {jdError && (
              <p role="alert" className="text-sm text-rose-600">{jdError}</p>
            )}
          </div>
        )}

        <Button
          className="w-fit"
          disabled={!canGenerate}
          onClick={() => void generate()}
        >
          {isGenerating ? "Drafting model answers…" : "Generate interview prep"}
        </Button>

        {error && (
          <p role="alert" className="text-sm text-rose-600">{error}</p>
        )}

        {result && result.items.length > 0 && (
          <div className="flex flex-col gap-4">
            {result.specialistsUsed.length > 0 && (
              <p className="text-xs text-slate-400">
                Worked with: {result.specialistsUsed.join(", ")}
              </p>
            )}
            <ol className="flex flex-col gap-4">
              {result.items.map((item, i) => (
                <li key={`${item.question}-${i}`} className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4">
                  <p className="text-sm font-medium text-slate-900">
                    Q{i + 1}. {item.question}
                  </p>
                  <p className="whitespace-pre-wrap text-sm text-slate-600">
                    {item.modelAnswer}
                  </p>
                  <Grounding answer={item.modelAnswer} known={known} />
                </li>
              ))}
            </ol>
            {result.citations.length > 0 && (
              <div className="flex flex-col gap-1">
                <p className="text-sm font-medium text-slate-900">Sources the Coach cited</p>
                {result.citations.map((c, i) => (
                  <p key={`${c.docId}-${i}`} className="text-xs leading-relaxed text-slate-500">
                    <span className="font-medium text-slate-600">{shortDocId(c.docId)}</span>{" "}
                    · “{c.chunk.length > 140 ? `${c.chunk.slice(0, 140)}…` : c.chunk}” · score {c.score.toFixed(4)}
                  </p>
                ))}
              </div>
            )}
          </div>
        )}

        {result && result.items.length === 0 && result.unparsed && (
          <div className="flex flex-col gap-2">
            <p className="text-sm text-slate-500">
              The Coach&apos;s reply didn&apos;t follow the expected
              question-and-answer format, so it is shown as received — nothing
              was reshaped or added.
            </p>
            <p className="whitespace-pre-wrap rounded-lg border border-slate-200 p-4 text-sm text-slate-600">
              {result.unparsed}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
