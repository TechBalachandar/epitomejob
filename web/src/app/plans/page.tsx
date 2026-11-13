"use client";

// W4 billing plans page (contract §1f). Calls GET /api/billing/status
// first; the three plans below are the hardcoded display copy matching the
// backend seed rows (§1b) — shown even while billing is off, when the
// backend 404s on /billing/plans. When billing is on, the live plan list
// is used when it answers, with this copy as the fallback.
// Nothing here gates any feature on entitlement (contract §1e): Coach
// stays free in dev.
// Stripe redirect (billing worker note): after a Stripe Checkout the
// backend redirects back here as /plans?checkout=success&session_id=…
// or /plans?checkout=cancelled. The success case shows a confirmation and
// refreshes the entitlement; the cancel case shows that no payment was taken.
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  ApiError,
  createBillingCheckout,
  fetchBillingPlans,
  fetchBillingStatus,
  fetchMyEntitlement,
} from "@/lib/api";
import type { BillingPlan, BillingStatus, Entitlement } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export const BILLING_OFF_NOTE =
  "Billing isn't connected yet — checkout opens once Stripe is linked. No payment is taken.";
export const DEMO_NOTE = "Demo checkout — no real payment is taken.";
export const PENDING_MANUAL_MESSAGE =
  "Thanks — our team will contact you to complete your package.";
export const DEMO_CREATED_MESSAGE =
  "Demo checkout created — no real payment is taken.";
export const CHECKOUT_SUCCESS_MESSAGE =
  "Payment complete — your pass is active.";
export const CHECKOUT_CANCELLED_MESSAGE =
  "Checkout was cancelled — no payment was taken.";

/** Hardcoded display copy, matching the seed rows in contract §1b exactly. */
export const DISPLAY_PLANS: BillingPlan[] = [
  {
    code: "SPRINT_PASS",
    name: "Sprint Pass",
    priceCents: 7900,
    currency: "USD",
    durationDays: 30,
    description: "30 days of full access — one-time payment.",
    active: true,
  },
  {
    code: "LIFETIME",
    name: "Lifetime",
    priceCents: 14900,
    currency: "USD",
    durationDays: null,
    description: "Lifetime access — one-time payment.",
    active: true,
  },
  {
    code: "HUMAN_REVIEWED",
    name: "Human-Reviewed Package",
    priceCents: 29900,
    currency: "USD",
    durationDays: null,
    description:
      "Human-reviewed resume + plan. Fulfilled manually by our team — we contact you after checkout.",
    active: true,
  },
];

function formatPrice(plan: BillingPlan): string {
  const amount = plan.priceCents / 100;
  return `$${Number.isInteger(amount) ? amount : amount.toFixed(2)}`;
}

function PlansPageContent() {
  const searchParams = useSearchParams();
  // Stripe redirect outcome (?checkout=success / ?checkout=cancelled).
  const checkoutOutcome = searchParams.get("checkout");
  const [status, setStatus] = useState<BillingStatus | null>(null);
  const [plans, setPlans] = useState<BillingPlan[]>(DISPLAY_PLANS);
  const [statusError, setStatusError] = useState(false);
  const [checkingOut, setCheckingOut] = useState<string | null>(null);
  // Per-plan outcome message (PENDING_MANUAL, demo-created, or a backend
  // error such as the 503 "not configured" message, shown verbatim).
  const [messages, setMessages] = useState<Record<string, string>>({});
  // Refreshed on a Stripe success redirect (the webhook may still be
  // landing, so a NONE here only means "not active yet").
  const [entitlement, setEntitlement] = useState<Entitlement | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const s = await fetchBillingStatus();
        if (cancelled) return;
        setStatus(s);
        if (s.enabled) {
          try {
            const live = await fetchBillingPlans();
            if (!cancelled && live.length > 0) setPlans(live);
          } catch {
            // Live plans unavailable — keep the hardcoded display copy.
          }
        }
      } catch {
        if (!cancelled) setStatusError(true);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (checkoutOutcome !== "success") return;
    let cancelled = false;
    void fetchMyEntitlement()
      .then((e) => {
        if (!cancelled) setEntitlement(e);
      })
      .catch(() => {
        // Entitlement not readable yet (e.g. webhook still landing) — the
        // confirmation banner stands on its own.
      });
    return () => {
      cancelled = true;
    };
  }, [checkoutOutcome]);

  async function checkout(plan: BillingPlan) {
    setCheckingOut(plan.code);
    setMessages((m) => {
      const next = { ...m };
      delete next[plan.code];
      return next;
    });
    try {
      const res = await createBillingCheckout(plan.code);
      if (res.status === "PENDING_MANUAL") {
        setMessages((m) => ({ ...m, [plan.code]: PENDING_MANUAL_MESSAGE }));
        return;
      }
      if (res.checkoutUrl) {
        window.open(res.checkoutUrl, "_blank", "noopener,noreferrer");
        return;
      }
      // Fake provider demo checkout: no Stripe URL is issued.
      setMessages((m) => ({ ...m, [plan.code]: DEMO_CREATED_MESSAGE }));
    } catch (err) {
      // The backend's message verbatim (e.g. the 503 BILLING_NOT_CONFIGURED
      // message); ApiError already carries body.message from apiFetch.
      const message =
        err instanceof ApiError ? err.message : "Checkout failed. Try again.";
      setMessages((m) => ({ ...m, [plan.code]: message }));
    } finally {
      setCheckingOut(null);
    }
  }

  const billingEnabled = status?.enabled ?? false;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Plans</h1>
        <p className="mt-1 text-sm text-slate-500">
          One-time plans for full EpitomeJob access. Nothing is charged
          until checkout completes.
        </p>
      </div>

      {checkoutOutcome === "success" && (
        <p role="status" className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          {CHECKOUT_SUCCESS_MESSAGE}
          {entitlement?.status === "ACTIVE" && entitlement.planCode && (
            <> Current plan: {entitlement.planCode}.</>
          )}
        </p>
      )}
      {checkoutOutcome === "cancelled" && (
        <p role="status" className="rounded-md bg-slate-100 px-3 py-2 text-sm text-slate-700">
          {CHECKOUT_CANCELLED_MESSAGE}
        </p>
      )}

      {status === null && !statusError && (
        <p className="text-sm text-slate-500">Loading plans…</p>
      )}
      {statusError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load billing status. Make sure you are signed in and the
          backend is running.
        </p>
      )}
      {status !== null && !billingEnabled && (
        <p role="note" className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {BILLING_OFF_NOTE}
        </p>
      )}
      {status !== null && billingEnabled && status.provider === "fake" && (
        <p role="note" className="rounded-md bg-sky-50 px-3 py-2 text-sm text-sky-800">
          {DEMO_NOTE}
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        {plans.map((plan) => (
          <Card key={plan.code}>
            <CardHeader>
              <CardTitle>{plan.name}</CardTitle>
              <p className="text-2xl font-bold text-slate-900">
                {formatPrice(plan)}
                <span className="ml-1 text-sm font-normal text-slate-500">
                  one-time
                </span>
              </p>
              {plan.durationDays !== null && (
                <p className="text-xs text-slate-500">
                  {plan.durationDays} days of access
                </p>
              )}
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <p className="text-sm leading-relaxed text-slate-600">
                {plan.description}
              </p>
              <Button
                disabled={!billingEnabled || checkingOut === plan.code}
                onClick={() => void checkout(plan)}
                aria-label={`Checkout ${plan.name}`}
              >
                {checkingOut === plan.code ? "Starting checkout…" : "Checkout"}
              </Button>
              {messages[plan.code] && (
                <p role="status" className="text-sm text-slate-700">
                  {messages[plan.code]}
                </p>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

// useSearchParams() needs a Suspense boundary so the page still
// prerenders (Next.js CSR bailout rule for search params).
export default function PlansPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Loading plans…</p>}>
      <PlansPageContent />
    </Suspense>
  );
}
