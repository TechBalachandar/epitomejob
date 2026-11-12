package com.epitomejob.approval.service;

import com.epitomejob.approval.model.Approval;
import com.epitomejob.approval.repository.ApprovalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID APPROVAL_ID = UUID.randomUUID();

  @Mock
  private ApprovalRepository approvals;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private ApprovalService service() {
    return new ApprovalService(approvals);
  }

  private Approval pendingApproval() {
    return new Approval(USER_ID, "CREATE_APPLICATION", "{\"jobId\":\"job-1\"}",
        "token-hash", Instant.now().plusSeconds(3600));
  }

  @Test
  void proposeCreatesPendingApprovalExpiringIn24Hours() {
    authenticate();
    when(approvals.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Approval created = service().propose("CREATE_APPLICATION", "{\"jobId\":\"job-1\"}");

    assertEquals("PENDING", created.getStatus());
    assertEquals("CREATE_APPLICATION", created.getAction());
    assertTrue(created.getExpiresAt().isAfter(Instant.now().plusSeconds(23 * 3600)),
        "approval must expire ~24h after proposal");
    assertTrue(created.getExpiresAt().isBefore(Instant.now().plusSeconds(25 * 3600)));
  }

  @Test
  void approveSetsApprovedAndDecidedAt() {
    authenticate();
    Approval approval = pendingApproval();
    when(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(approval));
    when(approvals.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Approval decided = service().decide(APPROVAL_ID, true);

    assertEquals("APPROVED", decided.getStatus());
    assertNotNull(decided.getDecidedAt());
  }

  @Test
  void rejectSetsRejected() {
    authenticate();
    Approval approval = pendingApproval();
    when(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(approval));
    when(approvals.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertEquals("REJECTED", service().decide(APPROVAL_ID, false).getStatus());
  }

  @Test
  void approveTwiceExecutesOnce() {
    authenticate();
    Approval approval = pendingApproval();
    when(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(approval));
    when(approvals.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service().decide(APPROVAL_ID, true);
    Approval second = service().decide(APPROVAL_ID, true);

    assertEquals("APPROVED", second.getStatus());
    verify(approvals, times(1)).save(any());
  }

  @Test
  void decidingAnotherUsersApprovalIsNotFound() {
    authenticate();
    Approval other = new Approval(UUID.randomUUID(), "CREATE_APPLICATION", "{}",
        "token-hash", Instant.now().plusSeconds(3600));
    when(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(other));

    var ex = assertThrows(ResponseStatusException.class, () -> service().decide(APPROVAL_ID, true));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void decidingMissingApprovalIsNotFound() {
    authenticate();
    when(approvals.findById(APPROVAL_ID)).thenReturn(Optional.empty());

    var ex = assertThrows(ResponseStatusException.class, () -> service().decide(APPROVAL_ID, true));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void decidingExpiredApprovalIsGone() {
    authenticate();
    Approval expired = new Approval(USER_ID, "CREATE_APPLICATION", "{}",
        "token-hash", Instant.now().minusSeconds(60));
    when(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(expired));

    var ex = assertThrows(ResponseStatusException.class, () -> service().decide(APPROVAL_ID, true));
    assertEquals(HttpStatus.GONE, ex.getStatusCode());
  }

  @Test
  void pendingMineReturnsOnlyPending() {
    authenticate();
    Approval approval = pendingApproval();
    when(approvals.findByUserIdAndStatusOrderByExpiresAtAsc(USER_ID, "PENDING"))
        .thenReturn(List.of(approval));

    assertEquals(List.of(approval), service().pendingMine());
    ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
    verify(approvals).findByUserIdAndStatusOrderByExpiresAtAsc(any(), status.capture());
    assertEquals("PENDING", status.getValue());
  }
}
