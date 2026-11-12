// Pure helpers for the approval inbox. Kept free of React so they are easy
// to unit-test; the page wires them to the TanStack Query mutations.
import type { Approval, ApprovalStatus } from "./types";

/** Approvals still waiting on a decision, newest first. */
export function pendingApprovals(approvals: Approval[]): Approval[] {
  return approvals
    .filter((a) => a.status === "PENDING")
    .sort((a, b) => b.expiresAt.localeCompare(a.expiresAt));
}

/** The full list after a decision: the decided card carries its new status. */
export function applyDecision(
  approvals: Approval[],
  id: string,
  decision: "APPROVED" | "REJECTED",
): Approval[] {
  return approvals.map((a) =>
    a.id === id
      ? { ...a, status: decision, decidedAt: new Date().toISOString() }
      : a,
  );
}

/** Remove a decided approval from the pending list (Approve or Reject). */
export function removeDecided(approvals: Approval[], id: string): Approval[] {
  return approvals.filter((a) => a.id !== id);
}

/** Whole days/hours left before an approval expires. */
export function timeLeft(expiresAt: string, now = new Date()): string {
  const ms = new Date(expiresAt).getTime() - now.getTime();
  if (ms <= 0) return "Expired";
  const hours = Math.floor(ms / 3_600_000);
  if (hours < 1) return `${Math.max(1, Math.floor(ms / 60_000))} min left`;
  if (hours < 24) return `${hours}h left`;
  const days = Math.floor(hours / 24);
  return `${days}d ${hours % 24}h left`;
}

export function statusLabel(status: ApprovalStatus): string {
  switch (status) {
    case "PENDING":
      return "Pending";
    case "APPROVED":
      return "Approved";
    case "REJECTED":
      return "Rejected";
    case "EXPIRED":
      return "Expired";
  }
}

/** Human-readable summary of an approval payload (a JSON-encoded string). */
export function payloadSummary(payload: string): string {
  try {
    const data = JSON.parse(payload) as Record<string, unknown>;
    const parts = Object.entries(data).map(([k, v]) => `${k}: ${String(v)}`);
    return parts.join(" · ");
  } catch {
    return payload;
  }
}
