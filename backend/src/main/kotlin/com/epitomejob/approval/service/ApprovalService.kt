package com.epitomejob.approval.service

import com.epitomejob.approval.model.Approval
import com.epitomejob.approval.repository.ApprovalRepository
import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.util.HashUtil
import com.epitomejob.common.util.SecurityUtil
import com.epitomejob.notification.service.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ApprovalService(
  private val approvals: ApprovalRepository,
  private val notifications: NotificationService
) {

  /** Creates a pending approval. The write it proposes does NOT execute here — only after approve(). */
  @Transactional
  fun propose(action: String, payload: String): Approval {
    val userId = SecurityUtil.currentUserId()
    val token = UUID.randomUUID().toString()
    val approval = Approval(
      userId, action, payload, HashUtil.sha256(token),
      Instant.now().plus(AppConstants.Approval.EXPIRY_HOURS, ChronoUnit.HOURS)
    )
    val saved = approvals.save(approval)
    notifications.create(
      userId, AppConstants.Notification.TYPE_APPROVAL_PROPOSED,
      AppConstants.Notification.APPROVAL_TITLE_PREFIX + label(action),
      AppConstants.Notification.APPROVAL_BODY,
      AppConstants.Notification.LINK_APPROVALS
    )
    return saved
  }

  /** Idempotent: approving twice executes once — the second call returns the same record. */
  @Transactional
  fun decide(id: UUID, approve: Boolean): Approval {
    val userId = SecurityUtil.currentUserId()
    val approval = approvals.findById(id)
      .filter { it.userId == userId }
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.APPROVAL_NOT_FOUND)
      }
    if (AppConstants.Approval.STATUS_PENDING != approval.status) {
      return approval // already decided — do not re-execute
    }
    if (approval.expiresAt.isBefore(Instant.now())) {
      throw ResponseStatusException(HttpStatus.GONE, AppConstants.Errors.APPROVAL_EXPIRED)
    }
    approval.status = if (approve) AppConstants.Approval.STATUS_APPROVED
      else AppConstants.Approval.STATUS_REJECTED
    approval.decidedAt = Instant.now()
    return approvals.save(approval)
  }

  @Transactional(readOnly = true)
  fun pendingMine(): List<Approval> {
    return approvals.findByUserIdAndStatusOrderByExpiresAtAsc(
      SecurityUtil.currentUserId(), AppConstants.Approval.STATUS_PENDING
    )
  }

  /** Human label for an approval action; unknown actions use the action string itself. */
  private fun label(action: String): String = when (action) {
    AppConstants.Approval.ACTION_APPLY_JOB -> "Apply for a job"
    AppConstants.Approval.ACTION_UPDATE_PROFILE -> "Update your profile"
    AppConstants.Approval.ACTION_DRAFT_APPLICATION -> "Draft application"
    else -> action
  }
}
