// Public landing page — the new `/`.
//
// This page is public: anyone can read the whole thing without logging
// in. The login is a call to action, not the whole page. No fake
// testimonials, no invented user counts, no fake logos. The countdown
// in the hero is an ILLUSTRATIVE example (a job seeker who started 23
// days ago), labelled as such — never a fake personal countdown for a
// visitor who has not entered a date.
import Link from "next/link";
import {
  ArrowRight,
  BadgeCheck,
  Bell,
  Briefcase,
  CalendarCheck,
  Clock,
  FileText,
  Mail,
  MessagesSquare,
  PenLine,
  Sparkles,
} from "lucide-react";
import { clockStatus } from "@/lib/h1b-clock";
import { cn } from "@/lib/utils";

/** Example start date: 23 days ago (UTC) — illustrative only. */
function exampleStartISO(today: Date): string {
  const d = new Date(
    Date.UTC(
      today.getUTCFullYear(),
      today.getUTCMonth(),
      today.getUTCDate() - 23,
    ),
  );
  return d.toISOString().slice(0, 10);
}

const STEPS = [
  {
    n: "1",
    title: "Upload your resume",
    text: "Your resume is parsed and matched against real jobs — no retyping your history.",
    href: "/resumes",
  },
  {
    n: "2",
    title: "Get sponsor jobs",
    text: "The live feed shows jobs from employers that sponsor H-1B visas, matched to your profile.",
    href: "/jobs",
  },
  {
    n: "3",
    title: "Tailor + apply in one click",
    text: "The Co-Pilot tailors your resume to the job, you approve the diff, and it applies — with your say-so at every step.",
    href: "/jobs",
  },
  {
    n: "4",
    title: "Prep for interviews",
    text: "Practice with the Coach on questions for the exact job, grounded in your own experience.",
    href: "/interviews",
  },
];

const FEATURES = [
  {
    icon: Clock,
    title: "60-Day Clock",
    text: "A live countdown of your H-1B grace period, with a day-by-day plan built from your real jobs and resumes.",
    href: "/clock",
  },
  {
    icon: PenLine,
    title: "Resume Builder",
    text: "Edit every section with a live preview, a designer, and an analyzer that scores what you actually wrote.",
    href: "/resume-builder",
  },
  {
    icon: Briefcase,
    title: "Sponsor Jobs",
    text: "A live feed of jobs from employers that sponsor H-1B visas, with match scores against your resume.",
    href: "/jobs",
  },
  {
    icon: MessagesSquare,
    title: "Coach",
    text: "A multi-agent coach that answers from your resume and the job — with sources, not guesses.",
    href: "/coach",
  },
  {
    icon: CalendarCheck,
    title: "Interview Prep",
    text: "Likely questions for the job you are applying to, with model answers grounded in your experience.",
    href: "/interviews",
  },
  {
    icon: Mail,
    title: "Cover Letter",
    text: "A letter drafted from your resume and the job description, editable in the same editor.",
    href: "/cover-letter",
  },
  {
    icon: Bell,
    title: "Sponsor Alerts",
    text: "A nudge the moment a new H-1B-sponsoring job matching your profile appears.",
    href: "/clock",
  },
  {
    icon: BadgeCheck,
    title: "Offer Comparison",
    text: "Two offers side by side — salary, visa support, location, benefits — with the better value called out only where the numbers say so.",
    href: "/offers",
  },
  {
    icon: Sparkles,
    title: "One-Click Tailor",
    text: "Your resume rewritten for one job, diff shown, your approval required before anything is sent.",
    href: "/jobs",
  },
];

const PLANS = [
  {
    name: "30-day pass",
    price: "$79–99",
    per: "one-time",
    text: "Full access for 30 days — build, match, tailor, apply, and prep.",
  },
  {
    name: "Lifetime",
    price: "$149",
    per: "one-time",
    text: "Full access, no expiry — for the search after this one, too.",
  },
  {
    name: "Human-reviewed",
    price: "$299–499",
    per: "one-time",
    text: "A person reviews your resume and your plan, on top of everything else.",
  },
];

export default function LandingPage() {
  const today = new Date();
  const example = clockStatus(exampleStartISO(today), today);
  const daysUsed = example.daysLeft !== null ? 60 - example.daysLeft : 23;
  const daysLeft = example.daysLeft ?? 37;

  return (
    <div className="flex flex-col">
      {/* Hero */}
      <section className="border-b border-slate-200/80 bg-gradient-to-b from-brand-50 via-white to-white">
        <div className="mx-auto grid w-full max-w-6xl items-center gap-10 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:py-24">
          <div className="flex flex-col justify-center gap-6">
            <p className="w-fit rounded-full border border-brand-200 bg-white px-3 py-1 text-xs font-semibold uppercase tracking-wide text-brand-700 shadow-sm">
              AI career assistance for laid-off H-1B workers
            </p>
            <h1 className="text-4xl font-extrabold leading-[1.08] tracking-tight text-slate-900 sm:text-5xl lg:text-[3.4rem]">
              Laid off on H-1B? You have 60 days. We help you land a
              sponsoring job before the clock runs out.
            </h1>
            <p className="max-w-xl text-lg leading-relaxed text-slate-600">
              EpitomeJob finds the employers that sponsor H-1B visas,
              tailors your resume to each job, and prepares you for the
              interview — one plan, one day at a time.
            </p>
            <div className="flex flex-wrap gap-3">
              <Link
                href="/register"
                className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-3.5 text-sm font-semibold text-white shadow-md transition-all hover:bg-brand-700 hover:shadow-lg active:scale-[0.98]"
              >
                Start your 60 days <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
              <Link
                href="/register"
                className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-6 py-3.5 text-sm font-semibold text-slate-900 shadow-sm transition-all hover:border-brand-600 hover:text-brand-700 active:scale-[0.98]"
              >
                See sponsor jobs
              </Link>
            </div>
            <p className="text-sm text-slate-500">
              Already have an account?{" "}
              <Link href="/login" className="font-semibold text-brand-600 hover:underline">
                Sign in
              </Link>
            </p>
          </div>

          {/* Illustrative countdown — labelled as an example, never personal. */}
          <div
            className={cn(
              "flex flex-col justify-center gap-5 rounded-2xl border border-brand-100 bg-white p-7 shadow-lift",
            )}
            aria-label="Example 60-day countdown"
          >
            <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <Clock className="h-4 w-4 text-clock-600" aria-hidden />
              Example — not your clock
            </p>
            <p className="text-6xl font-extrabold tabular-nums tracking-tight text-slate-900">
              {daysLeft}{" "}
              <span className="text-xl font-medium text-slate-500">days left</span>
            </p>
            <div
              className="h-3 w-full overflow-hidden rounded-full bg-slate-100"
              role="progressbar"
              aria-valuenow={daysUsed}
              aria-valuemin={0}
              aria-valuemax={60}
              aria-label="Example: days of the 60-day grace period used"
            >
              <span
                className="block h-full rounded-full bg-gradient-to-r from-brand-600 to-clock-500"
                style={{ width: `${(daysUsed / 60) * 100}%` }}
              />
            </div>
            <p className="text-sm leading-relaxed text-slate-600">
              A job seeker who started {daysUsed} days ago has {daysLeft} days
              left of the 60-day grace period. Your clock starts when you set
              your date — and your plan is built from your real jobs and
              resumes.
            </p>
            <Link
              href="/register"
              className="inline-flex w-fit items-center gap-2 text-sm font-semibold text-brand-600 hover:underline"
            >
              Set your clock <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section id="how-it-works" className="border-b border-slate-200/80 bg-surface">
        <div className="mx-auto w-full max-w-6xl px-4 py-16 sm:px-6">
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900">How it works</h2>
          <p className="mt-2 text-base text-slate-600">
            Four steps, in order. Nothing is sent to an employer without your approval.
          </p>
          <ol className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((step) => (
              <li key={step.n} className="group flex flex-col gap-3 rounded-2xl border border-slate-200/80 bg-white p-6 shadow-card transition-all hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-lift">
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white shadow-sm ring-4 ring-brand-50">
                  {step.n}
                </span>
                <Link href={step.href} className="text-base font-bold text-slate-900 transition-colors group-hover:text-brand-700">
                  {step.title}
                </Link>
                <p className="text-sm leading-relaxed text-slate-600">{step.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* What you get */}
      <section id="features" className="border-b border-slate-200/80 bg-white">
        <div className="mx-auto w-full max-w-6xl px-4 py-16 sm:px-6">
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900">What you get</h2>
          <p className="mt-2 text-base text-slate-600">
            One system for the whole search — not a resume tool bolted onto a job board.
          </p>
          <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, text, href }) => (
              <Link
                key={title}
                href={href}
                className="group flex flex-col gap-3 rounded-2xl border border-slate-200/80 bg-white p-6 shadow-card transition-all hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-lift"
              >
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600 transition-colors group-hover:bg-brand-100">
                  <Icon className="h-5 w-5" aria-hidden />
                </span>
                <span className="text-base font-bold text-slate-900 transition-colors group-hover:text-brand-700">
                  {title}
                </span>
                <span className="text-sm leading-relaxed text-slate-600">{text}</span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* Daily visit */}
      <section className="border-b border-slate-200/80 bg-gradient-to-br from-brand-700 via-brand-800 to-brand-900">
        <div className="mx-auto w-full max-w-6xl px-4 py-14 sm:px-6">
          <h2 className="text-3xl font-extrabold tracking-tight text-white">Built to be visited daily</h2>
          <p className="mt-3 max-w-2xl text-base leading-relaxed text-brand-100">
            Every morning your home screen has something new: today&apos;s plan
            from your 60-day clock, sponsor jobs posted today, one interview
            question to practice, and one tip for your resume — plus a streak
            for every day you show up and apply.
          </p>
          <Link
            href="/register"
            className="mt-8 inline-flex items-center gap-2 rounded-lg bg-white px-6 py-3.5 text-sm font-semibold text-brand-700 shadow-md transition-all hover:bg-brand-50 hover:shadow-lg active:scale-[0.98]"
          >
            Start your 60 days <ArrowRight className="h-4 w-4" aria-hidden />
          </Link>
        </div>
      </section>

      {/* Pricing */}
      <section id="pricing" className="bg-white">
        <div className="mx-auto w-full max-w-6xl px-4 py-16 sm:px-6">
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900">Pricing</h2>
          <p className="mt-2 text-base text-slate-600">
            One-time payments. No subscription, no monthly charge.
          </p>
          <div className="mt-10 grid gap-5 sm:grid-cols-3">
            {PLANS.map((plan) => (
              <div key={plan.name} className="flex flex-col gap-3 rounded-2xl border border-slate-200/80 bg-white p-6 shadow-card transition-all hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-lift">
                <p className="text-base font-bold text-slate-900">{plan.name}</p>
                <p className="text-4xl font-extrabold tracking-tight text-slate-900">
                  {plan.price}{" "}
                  <span className="text-sm font-medium text-slate-500">{plan.per}</span>
                </p>
                <p className="text-sm leading-relaxed text-slate-600">{plan.text}</p>
                <Link
                  href="/plans"
                  className="mt-2 inline-flex w-fit items-center gap-1 text-sm font-semibold text-brand-600 hover:underline"
                >
                  See plans <ArrowRight className="h-4 w-4" aria-hidden />
                </Link>
              </div>
            ))}
          </div>
          <p className="mt-8 text-sm text-slate-500">
            Already have an account?{" "}
            <Link href="/login" className="font-semibold text-brand-600 hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </section>
    </div>
  );
}
