package com.epitomejob.interview.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

/** POST /interviews/sessions body. */
data class CreateSessionRequest(
  @field:NotNull val jobId: UUID
)
