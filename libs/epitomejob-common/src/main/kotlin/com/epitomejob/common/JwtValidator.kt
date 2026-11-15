package com.epitomejob.common

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Validates the access JWTs the monolith issues. Mirrors the monolith's
 * {@code JwtService} (HS256, {@code parseSignedClaims} — never
 * {@code parseEncryptedClaims}; that was the SampAgent Slice 1 bug that broke
 * every authenticated call) so the gateway accepts exactly the tokens the
 * monolith mints and rejects everything else.
 *
 * <p>Construction fails fast when the secret is shorter than 32 characters,
 * same rule as the monolith ({@code AppConstants.Security.MIN_JWT_SECRET_LENGTH}).
 * The secret comes from the {@code APP_JWT_SECRET} env var; the dev default
 * is the same 42-char value the monolith's compose file uses and must be
 * replaced in any shared deployment.
 */
class JwtValidator(secret: String?) {

  /** Why a token was rejected. The gateway maps every reason to HTTP 401. */
  enum class Reason {
    EXPIRED,
    BAD_SIGNATURE,
    MALFORMED,
    NOT_AN_ACCESS_TOKEN
  }

  /**
   * Thrown when a token is rejected. `reason()` is kept as a method (not a
   * property) so Java callers — and the gateway filter — keep calling
   * {@code reason()} exactly as before.
   */
  class JwtValidationException : RuntimeException {
    private val _reason: Reason

    constructor(reason: Reason, message: String) : super(message) {
      _reason = reason
    }

    constructor(reason: Reason, message: String, cause: Throwable) : super(message, cause) {
      _reason = reason
    }

    fun reason(): Reason = _reason
  }

  /** The claims the gateway needs from a valid access token. */
  data class JwtClaims(val userId: UUID, val email: String?, val role: String?)

  /** Claim names, matching the monolith's {@code AppConstants.Security}. */
  companion object {
    const val CLAIM_EMAIL = "email"
    const val CLAIM_ROLE = "role"
    const val CLAIM_TYPE = "type"
    const val TOKEN_TYPE_REFRESH = "refresh"

    const val MIN_JWT_SECRET_LENGTH = 32
  }

  private val key: SecretKey

  init {
    if (secret == null || secret.length < MIN_JWT_SECRET_LENGTH) {
      throw IllegalStateException(
        "JWT secret must be at least 32 characters — set APP_JWT_SECRET"
      )
    }
    key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
  }

  /**
   * Parses and verifies a token. Returns the claims on success.
   *
   * @throws JwtValidationException with [Reason.EXPIRED] for an expired
   *         token, [Reason.BAD_SIGNATURE] for a bad signature,
   *         [Reason.MALFORMED] for anything else unparseable, and
   *         [Reason.NOT_AN_ACCESS_TOKEN] for a refresh token (refresh
   *         tokens are only valid at `POST /auth/refresh`, never as
   *         API credentials).
   */
  fun validate(token: String?): JwtClaims {
    val claims: Claims = try {
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
    } catch (e: ExpiredJwtException) {
      throw JwtValidationException(Reason.EXPIRED, "Token has expired", e)
    } catch (e: SignatureException) {
      throw JwtValidationException(Reason.BAD_SIGNATURE, "Token signature is invalid", e)
    } catch (e: JwtException) {
      throw JwtValidationException(Reason.MALFORMED, "Token is malformed", e)
    } catch (e: IllegalArgumentException) {
      throw JwtValidationException(Reason.MALFORMED, "Token is malformed", e)
    }
    if (TOKEN_TYPE_REFRESH == claims.get(CLAIM_TYPE, String::class.java)) {
      throw JwtValidationException(
        Reason.NOT_AN_ACCESS_TOKEN,
        "Refresh tokens cannot be used as API credentials"
      )
    }
    val userId: UUID = try {
      UUID.fromString(claims.subject)
    } catch (e: IllegalArgumentException) {
      throw JwtValidationException(Reason.MALFORMED, "Token subject is not a user id", e)
    }
    return JwtClaims(
      userId,
      claims.get(CLAIM_EMAIL, String::class.java),
      claims.get(CLAIM_ROLE, String::class.java)
    )
  }
}
