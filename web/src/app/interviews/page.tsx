"use client";

import { useState } from "react";
import { MOCK_INTERVIEWS, MOCK_QUESTIONS } from "@/lib/mock-data";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { CalendarCheck, Play } from "lucide-react";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export default function InterviewsPage() {
  const [running, setRunning] = useState(false);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [finished, setFinished] = useState(false);

  const upcoming = MOCK_INTERVIEWS.filter((i) => i.status === "UPCOMING");
  const past = MOCK_INTERVIEWS.filter((i) => i.status === "COMPLETED");

  function finish() {
    setFinished(true);
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Interviews</h1>
        <p className="mt-1 text-sm text-slate-500">
          Upcoming interviews and past feedback. The mock interview is a
          simple Q&amp;A in W0 — rubric scoring arrives with W2.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Mock interview</CardTitle>
          <CardDescription>
            3 questions, then a mock feedback summary.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {!running && !finished && (
            <Button className="w-fit" onClick={() => { setRunning(true); setFinished(false); setAnswers({}); }}>
              <Play className="h-4 w-4" aria-hidden /> Start mock interview
            </Button>
          )}
          {running && (
            <div className="flex flex-col gap-4">
              {MOCK_QUESTIONS.map((q, idx) => (
                <div key={q} className="flex flex-col gap-1.5">
                  <p className="text-sm font-medium text-slate-900">
                    Q{idx + 1}. {q}
                  </p>
                  <textarea
                    rows={3}
                    value={answers[idx] ?? ""}
                    onChange={(e) => setAnswers((a) => ({ ...a, [idx]: e.target.value }))}
                    placeholder="Type your answer…"
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-indigo-600"
                    aria-label={`Answer to question ${idx + 1}`}
                  />
                </div>
              ))}
              <Button className="w-fit" onClick={() => { setRunning(false); finish(); }}>
                Finish and see feedback
              </Button>
            </div>
          )}
          {finished && (
            <div className="rounded-md bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
              <p className="font-medium">Mock feedback (scripted)</p>
              <p className="mt-1">
                Strong structure on the approval-gate question. Add one
                concrete number to the matching-service answer, and keep each
                answer under 90 seconds. Overall mock score: 81/100.
              </p>
              <Button variant="outline" size="sm" className="mt-3" onClick={() => setFinished(false)}>
                Run it again
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Upcoming</CardTitle>
          </CardHeader>
          <CardContent>
            {upcoming.length === 0 && (
              <p className="text-sm text-slate-500">No upcoming interviews.</p>
            )}
            <ul className="flex flex-col gap-3">
              {upcoming.map((i) => (
                <li key={i.id} className="flex items-center gap-3 rounded-lg border border-slate-200 p-3">
                  <CalendarCheck className="h-5 w-5 shrink-0 text-indigo-600" aria-hidden />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-slate-900">
                      {i.role} — {i.company}
                    </span>
                    <span className="block text-xs text-slate-500">{formatDate(i.date)}</span>
                  </span>
                  <Badge>Upcoming</Badge>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Past interviews</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-4">
              {past.map((i) => (
                <li key={i.id} className="rounded-lg border border-slate-200 p-3">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-medium text-slate-900">
                      {i.role} — {i.company}
                    </p>
                    <Badge variant="success">Score {i.score}/100</Badge>
                  </div>
                  <p className="mt-1 text-xs text-slate-400">{formatDate(i.date)}</p>
                  <p className="mt-2 text-sm text-slate-600">{i.feedback}</p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
