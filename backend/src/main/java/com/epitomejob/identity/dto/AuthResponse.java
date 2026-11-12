package com.epitomejob.identity.dto;

/** What login/register/refresh return. Never includes a password hash. */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresInMs,
    String userId,
    String email,
    String role) {}
