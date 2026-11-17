package com.epitomejob.identity.dto

/** What login/register/refresh return. Never includes a password hash. */
data class AuthResponse(
  val accessToken: String,
  val refreshToken: String,
  val expiresInMs: Long,
  val userId: String,
  val email: String,
  val role: String
)
