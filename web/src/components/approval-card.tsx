"use client";

import { payloadSummary, timeLeft } from "@/lib/approvals";
import type { Approval } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Check, X } from "lucide-react";

interface ApprovalCardProps {
  approval: Approval;
  disabled?: boolean;
  onApprove: (id: string) => void;
  onReject: (id: string) => void;
}

export function ApprovalCard({ approval, disabled, onApprove, onReject }: ApprovalCardProps) {
  // An expired approval can no longer be decided — the backend correctly
  // refuses it with 410 — so it gets a muted card and no decision buttons.
  const isExpired = new Date(approval.expiresAt).getTime() <= Date.now();
  return (
    <Card className={isExpired ? "opacity-60" : undefined}>
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="capitalize">{approval.action.replaceAll("_", " ")}</CardTitle>
            <CardDescription>{payloadSummary(approval.payload)}</CardDescription>
          </div>
          <Badge variant="warning">{timeLeft(approval.expiresAt)}</Badge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-wrap items-center gap-2">
        {isExpired ? (
          <p className="text-sm text-slate-500">
            Expired — this request can no longer be decided. Apply again to
            propose a new one.
          </p>
        ) : (
          <>
            <Button disabled={disabled} onClick={() => onApprove(approval.id)}>
              <Check className="h-4 w-4" aria-hidden /> Approve
            </Button>
            <Button
              variant="outline"
              disabled={disabled}
              onClick={() => onReject(approval.id)}
            >
              <X className="h-4 w-4" aria-hidden /> Reject
            </Button>
          </>
        )}
        <span className="ml-auto text-xs text-slate-400">
          Expires {new Date(approval.expiresAt).toLocaleString()}
        </span>
      </CardContent>
    </Card>
  );
}
