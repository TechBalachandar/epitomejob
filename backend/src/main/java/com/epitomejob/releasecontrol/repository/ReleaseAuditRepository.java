package com.epitomejob.releasecontrol.repository;

import com.epitomejob.releasecontrol.model.ReleaseAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReleaseAuditRepository extends JpaRepository<ReleaseAuditEvent, UUID> {
  List<ReleaseAuditEvent> findByReleaseIdOrderByOccurredAtAsc(UUID releaseId);
}
