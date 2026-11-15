package com.epitomejob.releasecontrol.service;

import com.epitomejob.releasecontrol.dto.CreateReleaseRequest;
import com.epitomejob.releasecontrol.model.ReleaseAuditEvent;
import com.epitomejob.releasecontrol.model.ReleaseCandidate;
import com.epitomejob.releasecontrol.repository.ReleaseAuditRepository;
import com.epitomejob.releasecontrol.repository.ReleaseCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReleaseControlServiceTest {
  private ReleaseCandidateRepository candidates;
  private ReleaseAuditRepository audit;
  private ReleaseControlService service;

  private final UUID actor = UUID.fromString("e37f8a5a-1e27-4e5a-86e8-8b62a8d31c12");
  private final String sha = "a".repeat(40);

  @BeforeEach
  void setUp() {
    candidates = mock(ReleaseCandidateRepository.class);
    audit = mock(ReleaseAuditRepository.class);
    service = new ReleaseControlService(candidates, audit);
  }

  @Test
  void createsCandidateAndAuditEvent() {
    when(candidates.findByCreatedByAndIdempotencyKey(actor, "req-001"))
        .thenReturn(Optional.empty());
    when(candidates.save(any(ReleaseCandidate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ReleaseCandidate created = service.create(
        new CreateReleaseRequest("TechBalachandar/epitomejob", 17, sha, "req-001"),
        actor, "corr-001");

    assertEquals("TechBalachandar/epitomejob", created.getRepository());
    assertEquals(17, created.getPullRequestNumber());
    assertEquals(sha, created.getSourceSha());
    assertEquals("CREATED", created.getState().name());
    verify(audit).save(any(ReleaseAuditEvent.class));
  }

  @Test
  void sameIdempotencyKeyAndPayloadReturnsExistingCandidate() {
    ReleaseCandidate existing = new ReleaseCandidate(
        "TechBalachandar/epitomejob", 17, sha, actor, "req-001");
    when(candidates.findByCreatedByAndIdempotencyKey(actor, "req-001"))
        .thenReturn(Optional.of(existing));

    ReleaseCandidate result = service.create(
        new CreateReleaseRequest("TechBalachandar/epitomejob", 17, sha, "req-001"),
        actor, "corr-002");

    assertSame(existing, result);
    verify(candidates, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void reusedIdempotencyKeyWithDifferentPayloadConflicts() {
    ReleaseCandidate existing = new ReleaseCandidate(
        "TechBalachandar/epitomejob", 17, sha, actor, "req-001");
    when(candidates.findByCreatedByAndIdempotencyKey(actor, "req-001"))
        .thenReturn(Optional.of(existing));

    assertThrows(ResponseStatusException.class, () -> service.create(
        new CreateReleaseRequest("TechBalachandar/epitomejob", 18, sha, "req-001"),
        actor, "corr-003"));
    verify(candidates, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void candidateIsVisibleOnlyToItsCreator() {
    UUID candidateId = UUID.randomUUID();
    when(candidates.findByIdAndCreatedBy(candidateId, actor)).thenReturn(Optional.empty());

    assertThrows(ResponseStatusException.class, () -> service.get(candidateId, actor));
  }
}
