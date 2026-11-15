package com.epitomejob.identity.dto

import jakarta.validation.constraints.NotBlank

data class TokenRequest(
  @field:NotBlank val refreshToken: String
)
