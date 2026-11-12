"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { registerRequest, ApiError } from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import { toast } from "@/lib/toast-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const schema = z
  .object({
    fullName: z.string().min(2, "Enter your full name"),
    email: z.string().email("Enter a valid email address"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string().min(8, "Confirm your password"),
  })
  .refine((v) => v.password === v.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setServerError(null);
    try {
      const res = await registerRequest({
        email: values.email,
        fullName: values.fullName,
        password: values.password,
      });
      login(res);
      toast({ title: "Account created", description: "Welcome to EpitomeJob.", variant: "success" });
      router.replace("/");
    } catch (err) {
      const message =
        err instanceof ApiError ? err.message : "Registration failed. Try again.";
      setServerError(message);
    }
  }

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>Create your account</CardTitle>
        <CardDescription>
          Create your account. It is stored in the real backend and you will
          be signed in right away.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="fullName">Full name</Label>
            <Input id="fullName" autoComplete="name" placeholder="Alex Carter" {...register("fullName")} />
            {errors.fullName && (
              <p className="text-xs text-rose-600">{errors.fullName.message}</p>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" autoComplete="email" placeholder="you@example.com" {...register("email")} />
            {errors.email && (
              <p className="text-xs text-rose-600">{errors.email.message}</p>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">Password</Label>
            <Input id="password" type="password" autoComplete="new-password" placeholder="At least 8 characters" {...register("password")} />
            {errors.password && (
              <p className="text-xs text-rose-600">{errors.password.message}</p>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="confirmPassword">Confirm password</Label>
            <Input id="confirmPassword" type="password" autoComplete="new-password" {...register("confirmPassword")} />
            {errors.confirmPassword && (
              <p className="text-xs text-rose-600">{errors.confirmPassword.message}</p>
            )}
          </div>
          {serverError && (
            <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {serverError}
            </p>
          )}
          <Button type="submit" disabled={isSubmitting} className="w-full">
            {isSubmitting ? "Creating account…" : "Create account"}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-slate-500">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-indigo-600 hover:underline">
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
