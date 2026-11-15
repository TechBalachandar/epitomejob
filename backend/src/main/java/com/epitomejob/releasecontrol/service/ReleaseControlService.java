package com.epitomejob.releasecontrol.service;

import com.epitomejob.releasecontrol.dto.CreateReleaseRequest;
import com.epitomejob.releasecontrol.model.ReleaseAuditEvent;
import com.epitomejob.releasecontrol.model.ReleaseCandidate;
import com.epitomejob.releasecontrol.repository.ReleaseAuditRepository;
import com.epitomejob.releasecontrol.repository.ReleaseCandidateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class ReleaseControlService {
  private final ReleaseCandidateRepository candidates;
  private final ReleaseAuditRepository audit;

  public ReleaseControlService(ReleaseCandidateRepository candidates, ReleaseAuditRepository audit) {
    this.candidates = candidates;
    this.audit = audit;
  }

  @Transactional
  public ReleaseCandidate create(CreateReleaseRequest request, UUID actorId, String correlationId) {
    var existing = candidates.findByCreatedByAndIdempotencyKey(actorId, request.idempotencyKey());
    if (existing.isPresent()) {
      ReleaseCandidate candidate = existing.get();
      if (!candidate.getRepository().equals(request.repository())
          || candidate.getPullRequestNumber() != request.pullRequestNumber()
          || !candidate.getSourceSha().equalsIgnoreCase(request.sourceSha())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Idempotency key was already used with a different release candidate");
      }
      return candidate;
    }

    ReleaseCandidate candidate = candidates.save(new ReleaseCandidate(
        request.repository(), request.pullRequestNumber(), request.sourceSha().toLowerCase(),
        actorId, request.idempotencyKey()));
    audit.save(new ReleaseAuditEvent(candidate.getId(), actorId, "HUMAN",
        "RELEASE_CANDIDATE_CREATED", candidate.getState().name(), candidate.getSourceSha(),
        correlationId, "Release candidate created"));
    return candidate;
  }

  @Transactional(readOnly = true)
  public ReleaseCandidate get(UUID id, UUID actorId) {
    return candidates.findByIdAndCreatedBy(id, actorId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found"));
  }

  @Transactional(readOnly = true)
  public java.util.List<ReleaseAuditEvent> timeline(UUID id, UUID actorId) {
    get(id, actorId);
    return audit.findByReleaseIdOrderByOccurredAtAsc(id);
  }
}
