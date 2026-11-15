package com.epitomejob.resume.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Rename a resume's label only (W6 contract §2b). Label is required, 1–80 chars. */
data class RenameResumeRequest(
  @field:NotBlank @field:Size(max = 80) val label: String
)
