"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useProfile, useResumes, useUpdateProfile, ApiError } from "@/lib/api";
import { completenessItems } from "@/lib/dashboard";
import { CompletenessMeter } from "@/components/completeness-meter";
import { SkillsEditor } from "@/components/skills-editor";
import { toast } from "@/lib/toast-store";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Check, X } from "lucide-react";
import { latestResume } from "@/lib/dashboard";

const CURRENCIES = ["USD", "EUR", "GBP", "INR", "CAD", "AUD"];

const schema = z.object({
  headline: z.string().min(2, "Add a headline"),
  summary: z.string().min(2, "Add a short summary"),
  location: z.string().min(2, "Add a location"),
  workType: z.enum(["REMOTE", "HYBRID", "ONSITE"]),
  salaryMin: z.coerce.number().int().nonnegative().nullable(),
  salaryMax: z.coerce.number().int().nonnegative().nullable(),
  visibility: z.enum(["PRIVATE", "PUBLIC"]),
  currency: z.string().regex(/^[A-Z]{3}$/, "Pick a currency"),
  h1bClockStartDate: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

function normalizeWorkType(value: string | null | undefined): FormValues["workType"] {
  const upper = (value ?? "").toUpperCase();
  if (upper === "REMOTE" || upper === "HYBRID" || upper === "ONSITE") return upper;
  return "HYBRID";
}

export default function ProfilePage() {
  const { data: profile, isLoading, isError } = useProfile();
  const { data: resumes } = useResumes();
  const update = useUpdateProfile();
  const [skills, setSkills] = useState<string[]>([]);
  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<z.input<typeof schema>, unknown, z.output<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      headline: "",
      summary: "",
      location: "",
      workType: "HYBRID",
      salaryMin: null,
      salaryMax: null,
      visibility: "PRIVATE",
      currency: "USD",
      h1bClockStartDate: "",
    },
  });

  useEffect(() => {
    if (profile) {
      reset({
        headline: profile.headline,
        summary: profile.summary,
        location: profile.location,
        workType: normalizeWorkType(profile.workType),
        salaryMin: profile.salaryMin,
        salaryMax: profile.salaryMax,
        visibility:
          profile.visibility === "PUBLIC" ? "PUBLIC" : "PRIVATE",
        currency: profile.currency || "USD",
        h1bClockStartDate: profile.h1bClockStartDate ?? "",
      });
      setSkills(profile.skills ?? []);
    }
  }, [profile, reset]);

  const currency = watch("currency") || "USD";
  const checklist = profile ? completenessItems(profile) : [];
  const resume = latestResume(resumes);
  const currencyOptions =
    profile?.currency && !CURRENCIES.includes(profile.currency)
      ? [profile.currency, ...CURRENCIES]
      : CURRENCIES;

  async function onSubmit(data: z.output<typeof schema>) {
    try {
      await update.mutateAsync({
        headline: data.headline,
        summary: data.summary,
        location: data.location,
        workType: data.workType,
        salaryMin: data.salaryMin,
        salaryMax: data.salaryMax,
        skills,
        visibility: data.visibility,
        currency: data.currency,
        // An empty date input means "leave unchanged" — W5 has no way to
        // clear a set clock date back to null.
        ...(data.h1bClockStartDate
          ? { h1bClockStartDate: data.h1bClockStartDate }
          : {}),
      });
      toast({ title: "Profile saved", variant: "success" });
    } catch (err) {
      toast({
        title: "Could not save profile",
        description: err instanceof ApiError ? err.message : "Try again.",
        variant: "error",
      });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Profile</h1>
        <p className="mt-1 text-sm text-slate-500">
          Your profile drives matching — the more complete it is, the better
          the matches.
        </p>
      </div>

      {isLoading && <p className="text-sm text-slate-500">Loading profile…</p>}
      {isError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load your profile. Make sure you are signed in.
        </p>
      )}

      {profile && (
        <div className="grid gap-4 lg:grid-cols-3">
          <div className="flex flex-col gap-4 lg:col-span-2">
            <Card>
              <CardHeader>
                <CardTitle>At a glance</CardTitle>
                <CardDescription>How matching and the Coach see you.</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-3 text-sm">
                <p className="text-base font-semibold text-slate-900">
                  {profile.headline || "No headline yet"}
                </p>
                {profile.summary && (
                  <p className="text-slate-600">{profile.summary}</p>
                )}
                <dl className="grid gap-x-6 gap-y-2 sm:grid-cols-2">
                  <div>
                    <dt className="text-xs text-slate-500">Location</dt>
                    <dd className="text-slate-900">{profile.location || "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Work type</dt>
                    <dd className="text-slate-900">{profile.workType || "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Salary range</dt>
                    <dd className="text-slate-900">
                      {profile.salaryMin !== null && profile.salaryMax !== null
                        ? `${profile.currency} ${profile.salaryMin.toLocaleString()} – ${profile.salaryMax.toLocaleString()}`
                        : "—"}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Visibility</dt>
                    <dd className="text-slate-900">{profile.visibility}</dd>
                  </div>
                </dl>
                <div>
                  <p className="text-xs text-slate-500">Skills</p>
                  {profile.skills && profile.skills.length > 0 ? (
                    <div className="mt-1 flex flex-wrap gap-2">
                      {profile.skills.map((skill) => (
                        <Badge key={skill} variant="secondary">{skill}</Badge>
                      ))}
                    </div>
                  ) : (
                    <p className="mt-1 text-slate-600">No skills listed yet.</p>
                  )}
                </div>
                <div>
                  <p className="text-xs text-slate-500">Linked resume</p>
                  {resume ? (
                    <p className="mt-1 text-slate-900">
                      {resume.fileName}{" "}
                      <Badge
                        variant={resume.status === "READY" ? "default" : "secondary"}
                        className="ml-1"
                      >
                        {resume.status}
                      </Badge>
                    </p>
                  ) : (
                    <p className="mt-1 text-slate-600">
                      No resume yet —{" "}
                      <Link href="/resumes" className="text-indigo-700 hover:underline">
                        upload one
                      </Link>
                      .
                    </p>
                  )}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Edit profile</CardTitle>
                <CardDescription>Changes save to your profile.</CardDescription>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="headline">Headline</Label>
                    <Input id="headline" {...register("headline")} />
                    {errors.headline && <p className="text-xs text-rose-600">{errors.headline.message}</p>}
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="summary">Summary</Label>
                    <Textarea id="summary" rows={4} {...register("summary")} />
                    {errors.summary && <p className="text-xs text-rose-600">{errors.summary.message}</p>}
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="location">Location</Label>
                      <Input id="location" {...register("location")} />
                      {errors.location && <p className="text-xs text-rose-600">{errors.location.message}</p>}
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="workType">Work type</Label>
                      <Select id="workType" {...register("workType")}>
                        <option value="REMOTE">Remote</option>
                        <option value="HYBRID">Hybrid</option>
                        <option value="ONSITE">Onsite</option>
                      </Select>
                    </div>
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="salaryMin">Salary min ({currency})</Label>
                      <Input id="salaryMin" type="number" min={0} step={1000} {...register("salaryMin")} />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="salaryMax">Salary max ({currency})</Label>
                      <Input id="salaryMax" type="number" min={0} step={1000} {...register("salaryMax")} />
                    </div>
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="visibility">Visibility</Label>
                      <Select id="visibility" {...register("visibility")}>
                        <option value="PRIVATE">Private</option>
                        <option value="PUBLIC">Public</option>
                      </Select>
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="currency">Currency</Label>
                      <Select id="currency" {...register("currency")}>
                        {currencyOptions.map((c) => (
                          <option key={c} value={c}>{c}</option>
                        ))}
                      </Select>
                      {errors.currency && <p className="text-xs text-rose-600">{errors.currency.message}</p>}
                    </div>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label>Skills</Label>
                    <SkillsEditor value={skills} onChange={setSkills} />
                    <p className="text-xs text-slate-500">
                      Skills feed matching — the jobs you match best change
                      when your skills change.
                    </p>
                  </div>
                  <div className="rounded-lg border border-slate-200 p-4">
                    <h3 className="text-sm font-semibold text-slate-900">Visa</h3>
                    <div className="mt-3 flex flex-col gap-1.5">
                      <Label htmlFor="h1bClockStartDate">H-1B clock start date</Label>
                      <Input
                        id="h1bClockStartDate"
                        type="date"
                        {...register("h1bClockStartDate")}
                      />
                      <p className="text-xs text-slate-500">
                        Optional. The 60-day grace-period countdown on the
                        dashboard starts from this date. Clearing a set date
                        isn&apos;t supported yet — leave it as is to keep it.
                      </p>
                    </div>
                  </div>
                  <Button type="submit" disabled={isSubmitting} className="w-fit">
                    {isSubmitting ? "Saving…" : "Save profile"}
                  </Button>
                </form>
              </CardContent>
            </Card>
          </div>

          <Card className="h-fit">
            <CardHeader>
              <CardTitle>Completeness</CardTitle>
              <CardDescription>
                Score from the server — skills and the clock date add no points.
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <CompletenessMeter value={profile.completeness} />
              <p className="text-sm text-slate-600">
                {profile.completeness === 100
                  ? "Complete — matching is using your full profile."
                  : "Fill in every field to reach 100%."}
              </p>
              <ul className="flex flex-col gap-2">
                {checklist.map((item) => (
                  <li key={item.key} className="flex items-center gap-2 text-sm">
                    {item.done ? (
                      <Check className="h-4 w-4 text-emerald-600" aria-label="done" />
                    ) : (
                      <X className="h-4 w-4 text-rose-500" aria-label="missing" />
                    )}
                    <span className={item.done ? "text-slate-700" : "text-slate-500"}>
                      {item.label}
                    </span>
                    <span className="ml-auto text-xs text-slate-400">
                      {item.done ? "Done" : "Missing"}
                    </span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
