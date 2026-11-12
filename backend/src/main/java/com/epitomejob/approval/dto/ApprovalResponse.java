package com.epitomejob.approval.dto;

import com.epitomejob.approval.model.Approval;

import java.time.Instant;
import java.util.UUID;

/** What the approval endpoints return. The token hash is never exposed. */
public record ApprovalResponse(
    UUID id,
    UUID userId,
    String action,
    String payload,
    String status,
    Instant expiresAt,
    Instant decidedAt) {

  public static ApprovalResponse from(Approval a) {
    return new ApprovalResponse(a.getId(), a.getUserId(), a.getAction(), a.getPayload(),
        a.getStatus(), a.getExpiresAt(), a.getDecidedAt());
  }
}
