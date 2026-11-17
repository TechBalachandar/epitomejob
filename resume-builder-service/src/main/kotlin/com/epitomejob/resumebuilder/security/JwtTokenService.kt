package com.epitomejob.resumebuilder.security

import com.epitomejob.resumebuilder.common.constants.AppConstants
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Validates the access JWTs the gateway already checked. The subject is the
 * user's UUID; refresh tokens (claim `type=refresh`) are rejected as
 * API credentials — same rule as the gateway's JwtAuthFilter.
 *
 * KEY DERIVATION — must match the monolith/gateway: the raw UTF-8 bytes
 * of `app.jwt.secret` (NOT Base64-decoded). The monolith's JwtService
 * was fixed (Nov 5) to use `parseSignedClaims` on a signed token;
 * this uses the same call. Mac gate: a gateway-minted token must validate
 * here; if the gateway derives its key differently, this is the one line
 * that changes.
 */
@Service
class JwtTokenService(@Value("\${app.jwt.secret}") secret: String) {

  private val signingKey: SecretKey =
      Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

  /** Returns the user id (JWT subject) or throws IllegalArgumentException. */
  fun userIdFromToken(token: String): UUID {
    val claims = parse(token)
    if (AppConstants.Security.TOKEN_TYPE_REFRESH == claims[AppConstants.Security.CLAIM_TYPE]) {
      throw IllegalArgumentException(AppConstants.Errors.REFRESH_TOKEN_NOT_ALLOWED)
    }
    try {
      return UUID.fromString(claims.subject)
    } catch (ex: IllegalArgumentException) {
      throw IllegalArgumentException(AppConstants.Errors.INVALID_OR_EXPIRED_TOKEN, ex)
    } catch (ex: NullPointerException) {
      throw IllegalArgumentException(AppConstants.Errors.INVALID_OR_EXPIRED_TOKEN, ex)
    }
  }

  private fun parse(token: String): Claims {
    try {
      return Jwts.parser()
          .verifyWith(signingKey)
          .build()
          .parseSignedClaims(token)
          .payload
    } catch (ex: JwtException) {
      // Expired, badly signed, malformed — all one 401, no detail leaked.
      throw IllegalArgumentException(AppConstants.Errors.INVALID_OR_EXPIRED_TOKEN, ex)
    } catch (ex: IllegalArgumentException) {
      throw IllegalArgumentException(AppConstants.Errors.INVALID_OR_EXPIRED_TOKEN, ex)
    }
  }
}
