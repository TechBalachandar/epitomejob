"use client";

import { useEffect, useRef, useState } from "react";
import { sendCoachMessage } from "@/lib/api";
import type { ChatMessage, CoachCitation } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { SendHorizonal } from "lucide-react";

const WELCOME: ChatMessage = {
  id: "welcome",
  from: "coach",
  text: "Hello! I am your EpitomeJob Coach. Ask me about your resume, matching jobs, or interview prep.",
  at: new Date().toISOString(),
};

const SUGGESTIONS = [
  "How can I improve my resume?",
  "Which jobs match me best?",
  "How do I prepare for interviews?",
  "Which jobs sponsor H-1B visas?",
];

// Generic status lines shown while the (synchronous) agent call is in
// flight. They describe the agent's work in general — the API returns no
// per-specialist progress, so no live feed is invented (contract §9).
const STATUS_LINES = [
  "Checking your resume…",
  "Scoring matches…",
  "Gathering citations…",
];
const STATUS_DELAY_MS = 3000;
const STATUS_ROTATE_MS = 2500;

/** "job:<uuid>" → "job:10000000…" so citations stay readable. */
function shortDocId(docId: string): string {
  const [kind, id] = docId.split(":");
  if (!id) return docId;
  return `${kind}:${id.slice(0, 8)}…`;
}

function citationExcerpt(chunk: string): string {
  return chunk.length > 140 ? `${chunk.slice(0, 140)}…` : chunk;
}

function CitationList({ citations }: { citations: CoachCitation[] }) {
  return (
    <div className="flex flex-col gap-1">
      {citations.map((c, i) => (
        <p
          key={`${c.docId}-${i}`}
          className="text-xs leading-relaxed text-slate-500"
        >
          <span className="font-medium text-slate-600">
            {shortDocId(c.docId)}
          </span>{" "}
          · “{citationExcerpt(c.chunk)}” · score {c.score.toFixed(4)}
        </p>
      ))}
    </div>
  );
}

export default function CoachPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([WELCOME]);
  const [input, setInput] = useState("");
  const [thinking, setThinking] = useState(false);
  // -1 = "Working…"; 0..n indexes STATUS_LINES once the 3s delay passes.
  const [statusIndex, setStatusIndex] = useState(-1);
  const bottomRef = useRef<HTMLDivElement>(null);
  const idRef = useRef(0);

  useEffect(() => {
    if (!thinking) return;
    const timer = window.setTimeout(
      () => setStatusIndex(0),
      STATUS_DELAY_MS,
    );
    return () => window.clearTimeout(timer);
  }, [thinking]);

  useEffect(() => {
    if (statusIndex < 0) return;
    const timer = window.setInterval(
      () => setStatusIndex((i) => (i + 1) % STATUS_LINES.length),
      STATUS_ROTATE_MS,
    );
    return () => window.clearInterval(timer);
  }, [statusIndex]);

  async function send(text: string) {
    const trimmed = text.trim();
    if (!trimmed || thinking) return;
    const userMsg: ChatMessage = {
      id: `u-${++idRef.current}`,
      from: "user",
      text: trimmed,
      at: new Date().toISOString(),
    };
    setMessages((m) => [...m, userMsg]);
    setInput("");
    setStatusIndex(-1);
    setThinking(true);
    try {
      const res = await sendCoachMessage(trimmed);
      const reply: ChatMessage = {
        id: `c-${++idRef.current}`,
        from: "coach",
        text: res.reply,
        at: new Date().toISOString(),
        citations: res.citations ?? [],
        specialistsUsed: res.specialistsUsed ?? [],
      };
      setMessages((m) => [...m, reply]);
    } catch {
      const reply: ChatMessage = {
        id: `c-${++idRef.current}`,
        from: "coach",
        text: "Sorry — I could not reach the Coach just now. Please try again.",
        at: new Date().toISOString(),
      };
      setMessages((m) => [...m, reply]);
    } finally {
      setThinking(false);
      setStatusIndex(-1);
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Coach</h1>
        <p className="mt-1 text-sm text-slate-500">
          The multi-agent Coach reads your resume, jobs, and matches before
          answering — and only ever proposes actions for your approval.
        </p>
      </div>

      <Card className="flex min-h-96 flex-col">
        <CardHeader>
          <CardTitle>Chat with your Coach</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-1 flex-col gap-4">
          <div className="flex max-h-[52vh] flex-1 flex-col gap-3 overflow-y-auto pr-1">
            {messages.map((m) => (
              <div
                key={m.id}
                className={cn(
                  "flex max-w-[85%] flex-col gap-1.5",
                  m.from === "user" ? "self-end items-end" : "self-start items-start",
                )}
              >
                <div
                  className={cn(
                    "rounded-xl px-3.5 py-2.5 text-sm leading-relaxed",
                    m.from === "user"
                      ? "bg-indigo-600 text-white"
                      : "bg-slate-100 text-slate-800",
                  )}
                >
                  {m.text}
                </div>
                {m.from === "coach" &&
                  m.specialistsUsed &&
                  m.specialistsUsed.length > 0 && (
                    <p className="text-xs text-slate-500">
                      Worked with: {m.specialistsUsed.join(", ")}
                    </p>
                  )}
                {m.from === "coach" && m.citations && m.citations.length > 0 && (
                  <CitationList citations={m.citations} />
                )}
              </div>
            ))}
            {thinking && (
              <div className="self-start rounded-xl bg-slate-100 px-3.5 py-2.5 text-sm text-slate-500">
                {statusIndex >= 0 ? STATUS_LINES[statusIndex] : "Working…"}
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          <div className="flex flex-wrap gap-2">
            {SUGGESTIONS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => void send(s)}
                className="rounded-full border border-slate-300 px-3 py-1 text-xs font-medium text-slate-600 hover:border-indigo-400 hover:text-indigo-700"
              >
                {s}
              </button>
            ))}
          </div>

          <form
            className="flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              void send(input);
            }}
          >
            <Input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask about your resume, jobs, or interviews…"
              aria-label="Message the Coach"
            />
            <Button type="submit" disabled={thinking || input.trim() === ""} aria-label="Send message">
              <SendHorizonal className="h-4 w-4" aria-hidden />
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
