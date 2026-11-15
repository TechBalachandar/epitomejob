package com.epitomejob.identity.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.identity.model.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Signed JWTs (HS256; Key Vault secret in production).
 * LESSON from SampAgent Slice 1: a signed token must be parsed with
 * parseSignedClaims, never parseEncryptedClaims — that bug broke every
 * authenticated call. This class is the only place tokens are parsed.
 */
@Service
class JwtService(
  @Value("\${app.jwt.secret}") secret: String?,
  @Value("\${app.jwt.expiration-ms}") expirationMs: Long,
  @Value("\${app.jwt.refresh-expiration-ms}") refreshExpirationMs: Long
) {

  private val key: SecretKey
  private val accessExpirationMs: Long = expirationMs
  private val refreshExpirationMsValue: Long = refreshExpirationMs

  init {
    if (secret == null || secret.length < AppConstants.Security.MIN_JWT_SECRET_LENGTH) {
      throw IllegalStateException("app.jwt.secret must be at least 32 characters — set APP_JWT_SECRET")
    }
    key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
  }

  fun generateAccess(user: User): String {
    return Jwts.builder().subject(user.id.toString())
      .claim(AppConstants.Security.CLAIM_EMAIL, user.email)
      .claim(AppConstants.Security.CLAIM_ROLE, AppConstants.Security.ROLE_PREFIX + user.role.name)
      .issuedAt(Date()).expiration(Date(System.currentTimeMillis() + accessExpirationMs))
      .signWith(key).compact()
  }

  fun generateRefresh(user: User): String {
    return Jwts.builder().subject(user.id.toString())
      .claim(AppConstants.Security.CLAIM_TYPE, AppConstants.Security.TOKEN_TYPE_REFRESH)
      .issuedAt(Date()).expiration(Date(System.currentTimeMillis() + refreshExpirationMsValue))
      .signWith(key).compact()
  }

  fun parse(token: String): Claims {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
  }

  fun extractUserId(token: String): UUID {
    return UUID.fromString(parse(token).subject)
  }

  /** Access-token lifetime, as configured — the value login reports to clients. */
  fun expirationMs(): Long {
    return accessExpirationMs
  }

  fun refreshExpirationMs(): Long {
    return refreshExpirationMsValue
  }
}
