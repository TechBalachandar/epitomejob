package com.epitomejob.tracker.repository;

import com.epitomejob.tracker.model.TrackerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackerRepository extends JpaRepository<TrackerEntry, UUID> {
  Optional<TrackerEntry> findByUserIdAndJobId(UUID userId, UUID jobId);
  List<TrackerEntry> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
