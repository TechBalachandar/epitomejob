"use client";

import { useState } from "react";
import {
  ApiError,
  useAnswerInterviewQuestion,
  useCreateInterviewSession,
  useInterviewQuestions,
  useInterviewSession,
  useInterviewSessions,
  useJobs,
} from "@/lib/api";
import { toast } from "@/lib/toast-store";
import type { AnswerDto } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Play } from "lucide-react";

const RUBRIC_LABELS: { key: keyof AnswerDto["scores"]; label: string }[] = [
  { key: "structure", label: "Structure" },
  { key: "relevance", label: "Relevance" },
  { key: "specificity", label: "Specificity" },
  { key: "conciseness", label: "Conciseness" },
];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function Rubric({ answer }: { answer: AnswerDto }) {
  return (
    <div className="rounded-md bg-slate-50 px-4 py-3">
      <p className="text-sm font-medium text-slate-900">
        Mock rubric — deterministic checks, not AI feedback
      </p>
      <p className="mt-1 text-sm font-semibold text-indigo-700">
        Overall score: {answer.overallScore}/100
      </p>
      <ul className="mt-2 flex flex-wrap gap-2">
        {RUBRIC_LABELS.map(({ key, label }) => (
          <li key={key}>
            <Badge variant="secondary">
              {label}: {answer.scores[key]}/10
            </Badge>
          </li>
        ))}
      </ul>
      <ul className="mt-2 flex flex-col gap-1">
        {answer.feedback.map((f) => (
          <li key={f} className="list-disc pl-5 text-sm text-slate-600">{f}</li>
        ))}
      </ul>
    </div>
  );
}

export default function InterviewsPage() {
  const { data: jobsPage } = useJobs({ size: 50 });
  const jobs = jobsPage?.content ?? [];
  const [pickedJobId, setPickedJobId] = useState<string>("");
  // Default the job picker to the first job once the list loads.
  const selectedJobId = pickedJobId || jobs[0]?.id || "";
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [qIndex, setQIndex] = useState(0);
  const [answerText, setAnswerText] = useState("");
  const [lastAnswer, setLastAnswer] = useState<AnswerDto | null>(null);

  const { data: activeSession } = useInterviewSession(activeSessionId);
  const questionsJobId = activeSession?.jobId ?? (selectedJobId || null);
  const { data: questions } = useInterviewQuestions(questionsJobId);
  const { data: sessions, isLoading: sessionsLoading, isError: sessionsError } = useInterviewSessions();
  const createSession = useCreateInterviewSession();
  const answer = useAnswerInterviewQuestion(activeSessionId ?? "");

  const currentQuestion = questions?.[qIndex];
  const answeredIds = new Set((activeSession?.answers ?? []).map((a) => a.questionId));
  const answeredCurrent = currentQuestion ? answeredIds.has(currentQuestion.id) : false;

  async function startSession() {
    if (!selectedJobId) return;
    try {
      const session = await createSession.mutateAsync(selectedJobId);
      setActiveSessionId(session.id);
      setQIndex(0);
      setAnswerText("");
      setLastAnswer(null);
    } catch (err) {
      toast({ title: "Could not start session", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  async function submitAnswer() {
    if (!activeSessionId || !currentQuestion || !answerText.trim()) return;
    try {
      const result = await answer.mutateAsync({
        questionId: currentQuestion.id,
        answerText: answerText.trim(),
      });
      setLastAnswer(result);
      toast({ title: "Answer scored", description: `Overall score: ${result.overallScore}/100`, variant: "success" });
    } catch (err) {
      toast({ title: "Could not submit answer", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  function nextQuestion() {
    setLastAnswer(null);
    setAnswerText("");
    setQIndex((i) => Math.min(i + 1, (questions?.length ?? 1) - 1));
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Interviews</h1>
        <p className="mt-1 text-sm text-slate-500">
          Pick a job, start a mock interview, and answer the 5 questions one
          at a time. Each answer gets a deterministic rubric score — the AI
          interviewer arrives in W3.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Mock interview</CardTitle>
          <CardDescription>
            5 questions for the job&apos;s role, scored by a mock rubric.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <Select
              value={selectedJobId}
              onChange={(e) => setPickedJobId(e.target.value)}
              aria-label="Pick a job for the mock interview"
              className="flex-1"
            >
              {jobs.map((j) => (
                <option key={j.id} value={j.id}>
                  {j.title} — {j.company}
                </option>
              ))}
            </Select>
            <Button
              className="w-fit"
              disabled={!selectedJobId || createSession.isPending}
              onClick={() => void startSession()}
            >
              <Play className="h-4 w-4" aria-hidden /> Start mock interview
            </Button>
          </div>

          {activeSession && currentQuestion && (
            <div className="flex flex-col gap-4">
              <p className="text-sm text-slate-500">
                Question {qIndex + 1} of {questions?.length ?? 0}
                {activeSession.status === "COMPLETED" && (
                  <span className="ml-2">
                    <Badge variant="success">Completed — average {activeSession.averageScore}/100</Badge>
                  </span>
                )}
              </p>
              <p className="text-sm font-medium text-slate-900">
                Q{qIndex + 1}. {currentQuestion.text}
              </p>
              <p className="text-xs text-slate-400">
                {currentQuestion.category} · {currentQuestion.role}
                {answeredCurrent ? " · Answered" : ""}
              </p>
              <Textarea
                rows={4}
                value={answerText}
                onChange={(e) => setAnswerText(e.target.value)}
                placeholder="Type your answer…"
                aria-label={`Answer to question ${qIndex + 1}`}
              />
              <div className="flex gap-2">
                <Button
                  className="w-fit"
                  disabled={!answerText.trim() || answer.isPending}
                  onClick={() => void submitAnswer()}
                >
                  Submit answer
                </Button>
                {lastAnswer && qIndex + 1 < (questions?.length ?? 0) && (
                  <Button variant="outline" className="w-fit" onClick={nextQuestion}>
                    Next question
                  </Button>
                )}
              </div>
              {lastAnswer && <Rubric answer={lastAnswer} />}
            </div>
          )}
          {activeSession && !currentQuestion && (
            <p className="text-sm text-slate-500">Loading questions…</p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Session history</CardTitle>
          <CardDescription>Past mock interviews, newest first.</CardDescription>
        </CardHeader>
        <CardContent>
          {sessionsLoading && <p className="text-sm text-slate-500">Loading sessions…</p>}
          {sessionsError && (
            <p role="alert" className="text-sm text-rose-600">Could not load sessions.</p>
          )}
          {!sessionsLoading && (sessions?.length ?? 0) === 0 && (
            <p className="text-sm text-slate-500">No mock interview sessions yet.</p>
          )}
          <ul className="flex flex-col gap-3">
            {(sessions ?? []).map((s) => (
              <li
                key={s.id}
                className={`cursor-pointer rounded-lg border p-3 ${s.id === activeSessionId ? "border-indigo-600 ring-1 ring-indigo-600" : "border-slate-200"}`}
                onClick={() => {
                  setActiveSessionId(s.id);
                  setQIndex(0);
                  setAnswerText("");
                  setLastAnswer(null);
                }}
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm font-medium text-slate-900">{s.jobTitle}</p>
                  {s.status === "COMPLETED" ? (
                    <Badge variant="success">Score {s.averageScore}/100</Badge>
                  ) : (
                    <Badge>In progress</Badge>
                  )}
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  {formatDate(s.createdAt)} · {s.answers.length} answer{s.answers.length === 1 ? "" : "s"}
                </p>
                {s.id === activeSessionId && s.answers.length > 0 && (
                  <div className="mt-3 flex flex-col gap-3">
                    {s.answers.map((a) => (
                      <Rubric key={a.id} answer={a} />
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
