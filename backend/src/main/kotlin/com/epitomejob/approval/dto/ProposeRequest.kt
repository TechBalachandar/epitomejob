package com.epitomejob.approval.dto

import jakarta.validation.constraints.NotBlank

/**
 * Propose an action. The payload is a JSON string (it is stored as TEXT) —
 * clients send `{"jobId":"job-1","resumeId":"..."}`, not an object.
 */
data class ProposeRequest(
  @field:NotBlank val action: String,
  @field:NotBlank val payload: String
)
