package com.epitomejob.tracker.repository

import com.epitomejob.tracker.model.TrackerEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface TrackerRepository : JpaRepository<TrackerEntry, UUID> {
  fun findByUserIdAndJobId(userId: UUID, jobId: UUID): Optional<TrackerEntry>
  fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<TrackerEntry>
}
