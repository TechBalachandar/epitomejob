package com.epitomejob.interview.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

/** POST /interviews/sessions/{id}/answers body. */
data class AnswerRequest(
  @field:NotNull val questionId: UUID,
  @field:NotBlank val answerText: String
)
