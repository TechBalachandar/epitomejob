package com.epitomejob.interview.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sessions", schema = "interview")
class InterviewSession(
  @Column(nullable = false)
  var userId: UUID = UUID.randomUUID(),

  @Column(nullable = false)
  var jobId: UUID = UUID.randomUUID()
) {

  @Id
  var id: UUID = UUID.randomUUID()

  @Column(nullable = false)
  var status: String = AppConstants.Interview.STATUS_IN_PROGRESS

  @Column(nullable = false)
  var createdAt: Instant = Instant.now()

  var completedAt: Instant? = null
}
