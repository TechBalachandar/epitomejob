package com.epitomejob.approval.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.util.HashUtil;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.approval.model.Approval;
import com.epitomejob.approval.repository.ApprovalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ApprovalService {

  private final ApprovalRepository approvals;

  public ApprovalService(ApprovalRepository approvals) {
    this.approvals = approvals;
  }

  /** Creates a pending approval. The write it proposes does NOT execute here — only after approve(). */
  @Transactional
  public Approval propose(String action, String payload) {
    UUID userId = SecurityUtil.currentUserId();
    String token = UUID.randomUUID().toString();
    Approval approval = new Approval(userId, action, payload, HashUtil.sha256(token),
        Instant.now().plus(AppConstants.Approval.EXPIRY_HOURS, ChronoUnit.HOURS));
    return approvals.save(approval);
  }

  /** Idempotent: approving twice executes once — the second call returns the same record. */
  @Transactional
  public Approval decide(UUID id, boolean approve) {
    UUID userId = SecurityUtil.currentUserId();
    Approval approval = approvals.findById(id)
        .filter(a -> a.getUserId().equals(userId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.APPROVAL_NOT_FOUND));
    if (!AppConstants.Approval.STATUS_PENDING.equals(approval.getStatus())) {
      return approval; // already decided — do not re-execute
    }
    if (approval.getExpiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.GONE, AppConstants.Errors.APPROVAL_EXPIRED);
    }
    approval.setStatus(approve
        ? AppConstants.Approval.STATUS_APPROVED
        : AppConstants.Approval.STATUS_REJECTED);
    approval.setDecidedAt(Instant.now());
    return approvals.save(approval);
  }

  @Transactional(readOnly = true)
  public List<Approval> pendingMine() {
    return approvals.findByUserIdAndStatusOrderByExpiresAtAsc(SecurityUtil.currentUserId(),
        AppConstants.Approval.STATUS_PENDING);
  }
}
