"use client";

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { updateProfile, updateTrackerEntry, useDecideApproval, usePendingApprovals, ApiError } from "@/lib/api";
import type { ProfileUpdate } from "@/lib/types";
import { toast } from "@/lib/toast-store";
import { ApprovalCard } from "@/components/approval-card";
import { Card, CardContent } from "@/components/ui/card";
import { Inbox } from "lucide-react";

export default function ApprovalsPage() {
  const queryClient = useQueryClient();
  // Polling fallback every 5s (see usePendingApprovals): the stream sends
  // the pending list once and completes, so the inbox re-reads to stay fresh.
  const { data: approvals, isLoading, isError } = usePendingApprovals();
  const decide = useDecideApproval();
  const [streamNote, setStreamNote] = useState<string | null>(null);

  // Live stream: EventSource to the proxied backend stream. EventSource
  // cannot set headers, so the BFF authenticates the stream from the
  // httpOnly refresh cookie instead (withCredentials sends it).
  useEffect(() => {
    const source = new EventSource("/api/approvals/stream", {
      withCredentials: true,
    });
    source.onmessage = () => {
      setStreamNote("Live stream connected — pending list refreshed.");
      void queryClient.invalidateQueries({
        queryKey: ["approvals", "pending"],
      });
    };
    source.onerror = () => {
      // Stream is best-effort; the 5s poll keeps the inbox fresh.
      source.close();
    };
    return () => source.close();
  }, [queryClient]);

  async function handleDecision(id: string, decision: "approve" | "reject") {
    try {
      const decided = await decide.mutateAsync({ id, decision });
      // W2 apply flow (contract §6): an approved APPLY_JOB flips the
      // tracker entry to APPLIED. Rejected/expired approvals leave it SAVED.
      if (decision === "approve" && decided.action === "APPLY_JOB") {
        try {
          const payload = JSON.parse(decided.payload) as { trackerId?: string };
          if (payload.trackerId) {
            await updateTrackerEntry(payload.trackerId, { status: "APPLIED" });
            void queryClient.invalidateQueries({ queryKey: ["tracker", "me"] });
          }
        } catch {
          toast({
            title: "Approved, but the tracker was not updated",
            description: "Open the Tracker page and move the job to Applied manually.",
            variant: "error",
          });
        }
      }
      // W3 (contract §9): an approved UPDATE_PROFILE executes via the
      // existing PUT /profiles/me call with payload.fields, mirroring the
      // APPLY_JOB → tracker flip above. DRAFT_APPLICATION needs no extra
      // call — the draft text lives in the approval payload and the inbox
      // shows it.
      if (decision === "approve" && decided.action === "UPDATE_PROFILE") {
        try {
          const payload = JSON.parse(decided.payload) as { fields?: ProfileUpdate };
          if (payload.fields) {
            await updateProfile(payload.fields);
            void queryClient.invalidateQueries({ queryKey: ["profile", "me"] });
          }
        } catch {
          toast({
            title: "Approved, but the profile was not updated",
            description: "Open the Profile page and apply the change manually.",
            variant: "error",
          });
        }
      }
      toast({
        title: decision === "approve" ? "Approved" : "Rejected",
        description:
          decision === "approve"
            ? "The action is confirmed and the card left your inbox."
            : "The request was rejected and the card left your inbox.",
        variant: "success",
      });
    } catch (err) {
      toast({
        title: "Decision failed",
        description: err instanceof ApiError ? err.message : "Try again.",
        variant: "error",
      });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Approvals</h1>
        <p className="mt-1 text-sm text-slate-500">
          Nothing is ever sent on your behalf without your approval. Approve
          or reject each request — every decision is recorded.
        </p>
      </div>

      {streamNote && (
        <p className="rounded-md bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
          {streamNote}
        </p>
      )}

      {isLoading && <p className="text-sm text-slate-500">Loading approvals…</p>}
      {isError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load approvals. Make sure you are signed in.
        </p>
      )}

      {!isLoading && (!approvals || approvals.length === 0) && (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <Inbox className="h-10 w-10 text-slate-300" aria-hidden />
            <p className="text-sm font-medium text-slate-900">Inbox zero</p>
            <p className="max-w-sm text-sm text-slate-500">
              No pending approvals. When the Coach or a match proposes an
              action — like applying to a job — it will appear here first.
            </p>
          </CardContent>
        </Card>
      )}

      <div className="flex flex-col gap-4">
        {approvals?.map((a) => (
          <ApprovalCard
            key={a.id}
            approval={a}
            disabled={decide.isPending}
            onApprove={(id) => void handleDecision(id, "approve")}
            onReject={(id) => void handleDecision(id, "reject")}
          />
        ))}
      </div>
    </div>
  );
}
