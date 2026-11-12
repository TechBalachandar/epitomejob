"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { loginRequest, ApiError } from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import { toast } from "@/lib/toast-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import Link from "next/link";

const schema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
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
      const res = await loginRequest(values);
      login(res);
      toast({ title: "Welcome back", variant: "success" });
      router.replace("/");
    } catch (err) {
      const message =
        err instanceof ApiError ? err.message : "Login failed. Try again.";
      setServerError(message);
    }
  }

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>Sign in to EpitomeJob</CardTitle>
        <CardDescription>
          Sign in with your EpitomeJob account.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="dev@example.com"
              {...register("email")}
            />
            {errors.email && (
              <p className="text-xs text-rose-600">{errors.email.message}</p>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              placeholder="DevPass123!"
              {...register("password")}
            />
            {errors.password && (
              <p className="text-xs text-rose-600">{errors.password.message}</p>
            )}
          </div>
          {serverError && (
            <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {serverError}
            </p>
          )}
          <Button type="submit" disabled={isSubmitting} className="w-full">
            {isSubmitting ? "Signing in…" : "Sign in"}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-slate-500">
          No account yet?{" "}
          <Link href="/register" className="font-medium text-indigo-600 hover:underline">
            Create one
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
