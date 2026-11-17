package com.epitomejob.identity.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
  @field:Email @field:NotBlank val email: String,
  @field:NotBlank val fullName: String,
  @field:NotBlank val password: String
)
