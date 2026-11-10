package com.epitomejob.approval;
import com.epitomejob.common.SecurityUtil;
import com.epitomejob.identity.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Service
public class ApprovalService {
  private final ApprovalRepository approvals;
  public ApprovalService(ApprovalRepository approvals) { this.approvals = approvals; }
  /** Creates a pending approval. The write it proposes does NOT execute here — only after approve(). */
  @Transactional
  public Approval propose(String action, String payload) {
    UUID userId = SecurityUtil.currentUserId();
    String token = UUID.randomUUID().toString();
    Approval approval = new Approval(userId, action, payload, AuthService.sha256(token),
        Instant.now().plusSeconds(24 * 3600));
    return approvals.save(approval);
  }
  /** Idempotent: approving twice executes once — the second call returns the same APPROVED record. */
  @Transactional
  public Approval decide(UUID id, boolean approve) {
    UUID userId = SecurityUtil.currentUserId();
    Approval approval = approvals.findById(id)
        .filter(a -> a.getUserId().equals(userId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found"));
    if (!"PENDING".equals(approval.getStatus())) return approval; // already decided — do not re-execute
    if (approval.getExpiresAt().isBefore(Instant.now()))
      throw new ResponseStatusException(HttpStatus.GONE, "Approval expired — please re-propose");
    approval.setStatus(approve ? "APPROVED" : "REJECTED");
    approval.setDecidedAt(Instant.now());
    return approvals.save(approval);
  }
  @Transactional(readOnly = true)
  public List<Approval> pendingMine() {
    return approvals.findByUserIdAndStatusOrderByExpiresAtAsc(SecurityUtil.currentUserId(), "PENDING");
  }
}
