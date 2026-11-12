"use client";

export function CompletenessMeter({ value }: { value: number }) {
  const pct = Math.max(0, Math.min(100, Math.round(value)));
  return (
    <div>
      <div className="flex items-baseline justify-between">
        <span className="text-sm font-medium text-slate-700">
          Profile completeness
        </span>
        <span className="text-sm font-semibold text-slate-900">{pct}%</span>
      </div>
      <div
        className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-200"
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="Profile completeness"
      >
        <div
          className="h-full rounded-full bg-indigo-600 transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}
