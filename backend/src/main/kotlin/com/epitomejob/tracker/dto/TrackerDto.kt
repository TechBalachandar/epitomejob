package com.epitomejob.tracker.dto

import com.epitomejob.job.model.Job
import com.epitomejob.tracker.model.TrackerEntry
import java.time.Instant
import java.util.UUID

/** Tracker shape, exact field names per the W2 contract §3. */
data class TrackerDto(
  val id: UUID,
  val jobId: UUID,
  val title: String,
  val company: String,
  val resumeId: UUID?,
  val status: String,
  val notes: String?,
  val createdAt: Instant,
  val updatedAt: Instant
) {

  companion object {
    @JvmStatic
    fun from(entry: TrackerEntry, job: Job): TrackerDto {
      return TrackerDto(entry.id, entry.jobId, job.title, job.company,
        entry.resumeId, entry.status, entry.notes,
        entry.createdAt, entry.updatedAt)
    }
  }
}
