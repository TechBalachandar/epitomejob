"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useProfile, useUpdateProfile, ApiError } from "@/lib/api";
import { CompletenessMeter } from "@/components/completeness-meter";
import { toast } from "@/lib/toast-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

const schema = z.object({
  headline: z.string().min(2, "Add a headline"),
  summary: z.string().min(2, "Add a short summary"),
  location: z.string().min(2, "Add a location"),
  workType: z.enum(["REMOTE", "HYBRID", "ONSITE"]),
  salaryMin: z.coerce.number().int().nonnegative().nullable(),
  salaryMax: z.coerce.number().int().nonnegative().nullable(),
});

type FormValues = z.infer<typeof schema>;

export default function ProfilePage() {
  const { data: profile, isLoading, isError } = useProfile();
  const update = useUpdateProfile();
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
    },
  });

  useEffect(() => {
    if (profile) {
      reset({
        headline: profile.headline,
        summary: profile.summary,
        location: profile.location,
        workType: (profile.workType as FormValues["workType"]) ?? "HYBRID",
        salaryMin: profile.salaryMin,
        salaryMax: profile.salaryMax,
      });
    }
  }, [profile, reset]);

  // Live completeness: filled fields / 6.
  const values = watch();
  const filled = [
    values.headline,
    values.summary,
    values.location,
    values.workType,
    values.salaryMin,
    values.salaryMax,
  ].filter((v) => v !== "" && v !== null && v !== undefined).length;
  const liveCompleteness = Math.round((filled / 6) * 100);

  async function onSubmit(data: z.output<typeof schema>) {
    try {
      await update.mutateAsync({
        headline: data.headline,
        summary: data.summary,
        location: data.location,
        workType: data.workType,
        salaryMin: data.salaryMin,
        salaryMax: data.salaryMax,
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
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>About you</CardTitle>
              <CardDescription>Shown to matching and to the Coach.</CardDescription>
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
                    <Label htmlFor="salaryMin">Salary min (USD)</Label>
                    <Input id="salaryMin" type="number" min={0} step={1000} {...register("salaryMin")} />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="salaryMax">Salary max (USD)</Label>
                    <Input id="salaryMax" type="number" min={0} step={1000} {...register("salaryMax")} />
                  </div>
                </div>
                <Button type="submit" disabled={isSubmitting} className="w-fit">
                  {isSubmitting ? "Saving…" : "Save profile"}
                </Button>
              </form>
            </CardContent>
          </Card>

          <Card className="h-fit">
            <CardHeader>
              <CardTitle>Completeness</CardTitle>
            </CardHeader>
            <CardContent>
              <CompletenessMeter value={liveCompleteness} />
              <p className="mt-3 text-sm text-slate-600">
                {liveCompleteness === 100
                  ? "Complete — matching is using your full profile."
                  : "Fill in every field to reach 100%."}
              </p>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
