package com.epitomejob.releasecontrol.repository;

import com.epitomejob.releasecontrol.model.ReleaseCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseCandidateRepository extends JpaRepository<ReleaseCandidate, UUID> {
  Optional<ReleaseCandidate> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);
  Optional<ReleaseCandidate> findByIdAndCreatedBy(UUID id, UUID createdBy);
}
