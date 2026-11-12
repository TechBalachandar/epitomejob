"use client";

import Link from "next/link";
import { useProfile } from "@/lib/api";
import { MOCK_ACTIVITY } from "@/lib/mock-data";
import { CompletenessMeter } from "@/components/completeness-meter";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { ArrowRight, Briefcase, CalendarCheck, FileText, Sparkles } from "lucide-react";

const STATS = [
  { label: "Resumes", value: "1", icon: FileText, href: "/resumes" },
  { label: "Matches", value: "4", icon: Sparkles, href: "/matches" },
  { label: "Applications", value: "5", icon: Briefcase, href: "/tracker" },
  { label: "Interviews", value: "1 upcoming", icon: CalendarCheck, href: "/interviews" },
];

const NEXT_STEPS = [
  { text: "Finish your profile — headline and salary range are still blank.", href: "/profile" },
  { text: "Review your 92% match: Agentic AI Engineer at Meridian Care.", href: "/matches" },
  { text: "2 approvals are waiting for your decision.", href: "/approvals" },
  { text: "Run a 5-question mock interview before Friday.", href: "/interviews" },
];

export default function DashboardPage() {
  const { data: profile } = useProfile();
  const completeness = profile?.completeness ?? 80;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Good morning, Dev</h1>
        <p className="mt-1 text-sm text-slate-500">
          Here is where your job search stands today.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {STATS.map(({ label, value, icon: Icon, href }) => (
          <Link key={label} href={href}>
            <Card className="transition-shadow hover:shadow-md">
              <CardContent className="flex items-center gap-3 pt-5">
                <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
                  <Icon className="h-5 w-5" aria-hidden />
                </span>
                <span>
                  <span className="block text-xl font-bold text-slate-900">{value}</span>
                  <span className="block text-xs text-slate-500">{label}</span>
                </span>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Your profile</CardTitle>
            <CardDescription>
              Complete profiles get matched more accurately.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <CompletenessMeter value={completeness} />
            <p className="text-sm text-slate-600">
              {profile?.headline ??
                "Senior Software Engineer — Java, Spring Boot, Agentic AI"}
            </p>
            <Link
              href="/profile"
              className={cn(buttonVariants({ variant: "outline" }), "w-fit")}
            >
              Edit profile <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>60-day clock</CardTitle>
            <CardDescription>H-1B grace period tracker</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <p className="text-3xl font-bold text-slate-900">
              60 <span className="text-base font-medium text-slate-500">days left</span>
            </p>
            <p className="text-sm text-slate-600">
              Widget shell only in W0 — the real countdown, deadline, and
              sponsor-aware matching land with the H-1B wedge in W2.
            </p>
            <Badge variant="secondary" className="w-fit">
              Coming in W2
            </Badge>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Next steps</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-3">
              {NEXT_STEPS.map((step) => (
                <li key={step.text}>
                  <Link
                    href={step.href}
                    className="flex items-start gap-2 text-sm text-slate-700 hover:text-indigo-700"
                  >
                    <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-indigo-600" aria-hidden />
                    {step.text}
                  </Link>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Recent activity</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-3">
              {MOCK_ACTIVITY.map((a) => (
                <li key={a.id} className="flex flex-col gap-0.5 border-b border-slate-100 pb-3 last:border-0 last:pb-0">
                  <span className="text-sm text-slate-700">{a.text}</span>
                  <span className="text-xs text-slate-400">{a.at}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
