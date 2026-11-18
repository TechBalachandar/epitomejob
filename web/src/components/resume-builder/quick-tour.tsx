// Resume Builder — Quick Tour. First-visit onboarding in Teal's style:
// a dark teal welcome card ("Ready to Improve Your Resume!") with a gold
// "Start Quick Tour" button and a "Skip the Tour" link. The tour itself is
// a short step-through of the real areas (home, Content, Designer,
// Analyzer / Job Matcher) — a guided card tour, not a fake product tour.
// The page owns the open state and the localStorage "seen" flag; this
// component only renders the modal and its steps.
"use client";

import { useState } from "react";
import { ArrowLeft, ArrowRight, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";

const TOUR_STEPS: { title: string; body: string }[] = [
  {
    title: "Your resumes, in one place",
    body: "The home lists every resume you have built. Create one from a blank page, a job description, or a template — and open any of them to keep editing.",
  },
  {
    title: "Edit in Content",
    body: "Each section has a checkbox to include it in your resume and a + to add another entry. Co-Pilot can rewrite any section for you — just tap “Improve with AI”.",
  },
  {
    title: "Design it your way",
    body: "The Designer changes the font, line height, accent colour, alignment, margins and skills layout — and the live preview on the right updates instantly.",
  },
  {
    title: "Check the match",
    body: "The Analyzer scores your resume and the Job Matcher compares it to a real job description, keyword by keyword, so you can see exactly what to fix before you apply.",
  },
];

export function QuickTourModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  // 0 = welcome card; 1..TOUR_STEPS.length = tour steps. The modal
  // unmounts when it closes (the page renders it only while open), so the
  // step resets to the welcome card on every fresh open — no effect needed.
  const [step, setStep] = useState(0);

  if (!open) return null;
  const inTour = step > 0;
  const current = TOUR_STEPS[step - 1];

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Resume builder quick tour"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4"
    >
      <div className="w-full max-w-md rounded-2xl bg-[#0f4c3a] p-8 text-center text-white shadow-2xl">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-white/10">
          <Sparkles className="h-7 w-7 text-amber-300" aria-hidden />
        </div>
        {!inTour ? (
          <>
            <h2 className="mt-5 text-2xl font-bold">Ready to Improve Your Resume!</h2>
            <p className="mt-3 text-sm leading-relaxed text-teal-100">
              The Resume Builder helps you create great resumes so you can
              apply to jobs faster. Take a quick tour to see how it works.
            </p>
            <Button
              size="lg"
              className="mt-6 w-full bg-amber-400 font-semibold text-slate-900 hover:bg-amber-300"
              onClick={() => setStep(1)}
            >
              Start Quick Tour
            </Button>
            <button
              type="button"
              onClick={onClose}
              className="mt-4 text-sm text-teal-100 underline underline-offset-4 hover:text-white"
            >
              Skip the Tour
            </button>
          </>
        ) : (
          <>
            <p className="text-xs font-semibold uppercase tracking-widest text-teal-200">
              Step {step} of {TOUR_STEPS.length}
            </p>
            <h2 className="mt-3 text-2xl font-bold">{current.title}</h2>
            <p className="mt-3 text-sm leading-relaxed text-teal-100">{current.body}</p>
            <div className="mt-6 flex items-center justify-between gap-2">
              <Button
                variant="outline"
                className="border-white/30 bg-transparent text-white hover:bg-white/10 hover:text-white"
                disabled={step === 1}
                onClick={() => setStep((s) => Math.max(1, s - 1))}
              >
                <ArrowLeft className="h-4 w-4" aria-hidden />
                Back
              </Button>
              <span className="flex gap-1.5" aria-hidden>
                {TOUR_STEPS.map((_, i) => (
                  <span
                    key={i}
                    className={`h-2 w-2 rounded-full ${i + 1 === step ? "bg-amber-300" : "bg-white/30"}`}
                  />
                ))}
              </span>
              {step < TOUR_STEPS.length ? (
                <Button
                  className="bg-amber-400 font-semibold text-slate-900 hover:bg-amber-300"
                  onClick={() => setStep((s) => s + 1)}
                >
                  Next
                  <ArrowRight className="h-4 w-4" aria-hidden />
                </Button>
              ) : (
                <Button
                  className="bg-amber-400 font-semibold text-slate-900 hover:bg-amber-300"
                  onClick={onClose}
                >
                  Done
                </Button>
              )}
            </div>
            <button
              type="button"
              onClick={onClose}
              className="mt-4 text-sm text-teal-100 underline underline-offset-4 hover:text-white"
            >
              Skip the Tour
            </button>
          </>
        )}
      </div>
    </div>
  );
}

/** localStorage key marking the tour as seen (shown once per browser). */
export const BUILDER_TOUR_SEEN_KEY = "epitomejob:builder-tour-seen";
