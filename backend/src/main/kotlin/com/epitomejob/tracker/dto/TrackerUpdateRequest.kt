package com.epitomejob.tracker.dto

import jakarta.validation.constraints.NotBlank

/**
 * PUT /tracker/{id} body. notes absent (null) keeps the existing notes;
 * an invalid status is rejected with 400.
 */
data class TrackerUpdateRequest(
  @field:NotBlank val status: String,
  val notes: String? = null
)
