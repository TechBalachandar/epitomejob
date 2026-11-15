package com.epitomejob.approval.dto

import com.epitomejob.approval.model.Approval
import java.time.Instant
import java.util.UUID

/** What the approval endpoints return. The token hash is never exposed. */
data class ApprovalResponse(
  val id: UUID,
  val userId: UUID,
  val action: String,
  val payload: String,
  val status: String,
  val expiresAt: Instant,
  val decidedAt: Instant?
) {

  companion object {
    @JvmStatic
    fun from(a: Approval): ApprovalResponse {
      return ApprovalResponse(a.id, a.userId, a.action, a.payload, a.status, a.expiresAt, a.decidedAt)
    }
  }
}
