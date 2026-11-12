"use client";

import { useRef, useState } from "react";
import { coachReply } from "@/lib/coach";
import type { ChatMessage } from "@/lib/types";
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

export default function CoachPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([WELCOME]);
  const [input, setInput] = useState("");
  const [thinking, setThinking] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const idRef = useRef(0);

  function send(text: string) {
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
    setThinking(true);
    window.setTimeout(() => {
      const reply: ChatMessage = {
        id: `c-${++idRef.current}`,
        from: "coach",
        text: coachReply(trimmed),
        at: new Date().toISOString(),
      };
      setMessages((m) => [...m, reply]);
      setThinking(false);
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, 900);
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Coach</h1>
        <p className="mt-1 text-sm text-slate-500">
          Scripted mock replies in W0. The real multi-agent Coach arrives in
          W3.
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
                  "max-w-[85%] rounded-xl px-3.5 py-2.5 text-sm leading-relaxed",
                  m.from === "user"
                    ? "self-end bg-indigo-600 text-white"
                    : "self-start bg-slate-100 text-slate-800",
                )}
              >
                {m.text}
              </div>
            ))}
            {thinking && (
              <div className="self-start rounded-xl bg-slate-100 px-3.5 py-2.5 text-sm text-slate-500">
                Coach is thinking…
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          <div className="flex flex-wrap gap-2">
            {SUGGESTIONS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => send(s)}
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
              send(input);
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
