import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApprovalCard } from "@/components/approval-card";
import {
  applyDecision,
  pendingApprovals,
  removeDecided,
  timeLeft,
} from "@/lib/approvals";
import type { Approval } from "@/lib/types";

function approval(overrides: Partial<Approval> = {}): Approval {
  return {
    id: "appr-1",
    userId: "user-1",
    action: "apply_to_job",
    payload: JSON.stringify({ jobId: "job-2", resumeId: "resume-1" }),
    status: "PENDING",
    expiresAt: new Date(Date.now() + 20 * 3_600_000).toISOString(),
    decidedAt: null,
    ...overrides,
  };
}

describe("approval helpers", () => {
  it("pendingApprovals keeps only PENDING items", () => {
    const list = [
      approval({ id: "a" }),
      approval({ id: "b", status: "APPROVED" }),
      approval({ id: "c", status: "REJECTED" }),
    ];
    expect(pendingApprovals(list).map((a) => a.id)).toEqual(["a"]);
  });

  it("applyDecision stamps the decision and decidedAt", () => {
    const decided = applyDecision([approval()], "appr-1", "APPROVED");
    expect(decided[0]?.status).toBe("APPROVED");
    expect(decided[0]?.decidedAt).not.toBeNull();
  });

  it("removeDecided drops the decided card from the inbox", () => {
    const list = [approval({ id: "a" }), approval({ id: "b" })];
    expect(removeDecided(list, "a").map((a) => a.id)).toEqual(["b"]);
  });

  it("timeLeft reports hours for same-day expiry", () => {
    const now = new Date("2026-11-12T12:00:00Z");
    const expires = new Date("2026-11-12T20:00:00Z").toISOString();
    expect(timeLeft(expires, now)).toBe("8h left");
  });

  it("timeLeft reports Expired for a past expiry", () => {
    const now = new Date("2026-11-12T12:00:00Z");
    const expires = new Date("2026-11-12T11:00:00Z").toISOString();
    expect(timeLeft(expires, now)).toBe("Expired");
  });
});

describe("ApprovalCard", () => {
  it("Approve calls onApprove with the approval id", async () => {
    const user = userEvent.setup();
    const onApprove = vi.fn();
    const onReject = vi.fn();
    render(
      <ApprovalCard
        approval={approval()}
        onApprove={onApprove}
        onReject={onReject}
      />,
    );
    await user.click(screen.getByRole("button", { name: /approve/i }));
    expect(onApprove).toHaveBeenCalledTimes(1);
    expect(onApprove).toHaveBeenCalledWith("appr-1");
    expect(onReject).not.toHaveBeenCalled();
  });

  it("Reject calls onReject with the approval id", async () => {
    const user = userEvent.setup();
    const onApprove = vi.fn();
    const onReject = vi.fn();
    render(
      <ApprovalCard
        approval={approval()}
        onApprove={onApprove}
        onReject={onReject}
      />,
    );
    await user.click(screen.getByRole("button", { name: /reject/i }));
    expect(onReject).toHaveBeenCalledTimes(1);
    expect(onReject).toHaveBeenCalledWith("appr-1");
    expect(onApprove).not.toHaveBeenCalled();
  });

  it("shows the payload summary and both buttons", () => {
    render(
      <ApprovalCard approval={approval()} onApprove={vi.fn()} onReject={vi.fn()} />,
    );
    expect(screen.getByText(/jobId: job-2/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /approve/i })).toBeEnabled();
    expect(screen.getByRole("button", { name: /reject/i })).toBeEnabled();
  });
});
