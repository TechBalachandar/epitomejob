import { ArrowUpRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const RESOURCES = [
  {
    name: "freeCodeCamp",
    url: "https://www.freecodecamp.org/",
    description: "Free coding curriculum — JavaScript, Python, data structures, and more.",
  },
  {
    name: "Coursera (audit for free)",
    url: "https://www.coursera.org/",
    description: "University courses you can audit free — search any course, choose Audit on enrolment.",
  },
  {
    name: "Kaggle Learn",
    url: "https://www.kaggle.com/learn",
    description: "Short, hands-on micro-courses in Python, machine learning, and SQL.",
  },
];

export default function LearningPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Learning</h1>
        <p className="mt-1 text-sm text-slate-500">
          The EpitomeJob learning library arrives with the second wedge
          (career switchers). For now, here are free resources to start with.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Coming later</CardTitle>
          <CardDescription>
            Per the plan, the Learning module (M10) is cut from v1. When it
            lands, it will be a curator — linking out to courses matched to
            your gaps — not a course library of its own.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Badge variant="secondary">Second wedge — after web v1.0</Badge>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-3">
        {RESOURCES.map((r) => (
          <Card key={r.url}>
            <CardHeader>
              <CardTitle className="flex items-center gap-1.5 text-base">
                {r.name}
                <ArrowUpRight className="h-4 w-4 text-slate-400" aria-hidden />
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <p className="text-sm text-slate-600">{r.description}</p>
              <a
                href={r.url}
                target="_blank"
                rel="noreferrer"
                className="text-sm font-medium text-indigo-600 hover:underline"
              >
                Visit {r.name}
              </a>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
