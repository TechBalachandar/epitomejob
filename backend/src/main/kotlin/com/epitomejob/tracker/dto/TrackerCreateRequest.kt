package com.epitomejob.tracker.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

/** POST /tracker body. resumeId is optional. */
data class TrackerCreateRequest(
  @field:NotNull val jobId: UUID,
  val resumeId: UUID? = null
)
