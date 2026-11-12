"use client";

import { CheckCircle2, Info, X, XCircle } from "lucide-react";
import { useToastStore } from "@/lib/toast-store";
import { cn } from "@/lib/utils";

export function Toaster() {
  const { toasts, dismiss } = useToastStore();
  return (
    <div
      className="fixed bottom-4 right-4 z-50 flex w-80 flex-col gap-2"
      aria-live="polite"
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          role="status"
          className={cn(
            "flex items-start gap-3 rounded-lg border p-3 shadow-lg",
            t.variant === "success" && "border-emerald-200 bg-emerald-50",
            t.variant === "error" && "border-rose-200 bg-rose-50",
            t.variant === "info" && "border-slate-200 bg-white",
          )}
        >
          {t.variant === "success" && (
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" aria-hidden />
          )}
          {t.variant === "error" && (
            <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" aria-hidden />
          )}
          {t.variant === "info" && (
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" aria-hidden />
          )}
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-slate-900">{t.title}</p>
            {t.description && (
              <p className="text-xs text-slate-600">{t.description}</p>
            )}
          </div>
          <button
            onClick={() => dismiss(t.id)}
            aria-label="Dismiss notification"
            className="shrink-0 rounded p-0.5 text-slate-400 hover:text-slate-600"
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        </div>
      ))}
    </div>
  );
}
