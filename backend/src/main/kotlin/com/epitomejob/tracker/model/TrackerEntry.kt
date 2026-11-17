package com.epitomejob.tracker.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "entries", schema = "tracker")
class TrackerEntry(
  @Id
  var id: UUID = UUID.randomUUID(),

  @Column(nullable = false)
  var userId: UUID,

  @Column(nullable = false)
  var jobId: UUID,

  var resumeId: UUID?,

  @Column(nullable = false)
  var status: String = AppConstants.Tracker.STATUS_SAVED,

  @Column(columnDefinition = "TEXT")
  var notes: String? = null,

  @Column(nullable = false)
  var createdAt: Instant = Instant.now(),

  @Column(nullable = false)
  var updatedAt: Instant = Instant.now()
) {

  constructor(userId: UUID, jobId: UUID, resumeId: UUID?) : this(
    id = UUID.randomUUID(),
    userId = userId,
    jobId = jobId,
    resumeId = resumeId
  )

  fun touch() {
    updatedAt = Instant.now()
  }
}
