package com.epitomejob.common

import com.epitomejob.common.JwtValidator.JwtClaims
import com.epitomejob.common.JwtValidator.JwtValidationException
import com.epitomejob.common.JwtValidator.Reason
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Unit tests for [JwtValidator]. Every token here is minted with real
 * JJWT (no mocks of the validator itself), so the tests prove the same
 * parse path the gateway uses at runtime.
 */
class JwtValidatorTest {

  companion object {
    // >= 32 chars, as required. Never the real dev secret — tests mint their own.
    private const val SECRET = "test-secret-that-is-long-enough-0123456789"
    private const val WRONG_SECRET = "a-different-test-secret-long-enough-0123456789"
  }

  private val validator = JwtValidator(SECRET)

  private fun mint(
    secret: String,
    subject: String,
    email: String,
    role: String,
    type: String?,
    ttlMs: Long
  ): String {
    val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    val builder = Jwts.builder()
      .subject(subject)
      .claim(JwtValidator.CLAIM_EMAIL, email)
      .claim(JwtValidator.CLAIM_ROLE, role)
      .issuedAt(Date())
      .expiration(Date(System.currentTimeMillis() + ttlMs))
      .signWith(key)
    if (type != null) {
      builder.claim(JwtValidator.CLAIM_TYPE, type)
    }
    return builder.compact()
  }

  @Test
  fun validAccessTokenReturnsClaims() {
    val userId = UUID.randomUUID()
    val token = mint(SECRET, userId.toString(), "a@example.com", "ROLE_USER", null, 900_000L)

    val claims: JwtClaims = validator.validate(token)

    assertEquals(userId, claims.userId)
    assertEquals("a@example.com", claims.email)
    assertEquals("ROLE_USER", claims.role)
  }

  @Test
  fun expiredTokenIsRejectedAsExpired() {
    val token = mint(SECRET, UUID.randomUUID().toString(), "a@example.com",
      "ROLE_USER", null, -1_000L)

    val ex = assertThrows(JwtValidationException::class.java) { validator.validate(token) }
    assertEquals(Reason.EXPIRED, ex.reason())
  }

  @Test
  fun tokenSignedWithAnotherSecretIsRejectedAsBadSignature() {
    val token = mint(WRONG_SECRET, UUID.randomUUID().toString(), "a@example.com",
      "ROLE_USER", null, 900_000L)

    val ex = assertThrows(JwtValidationException::class.java) { validator.validate(token) }
    assertEquals(Reason.BAD_SIGNATURE, ex.reason())
  }

  @Test
  fun refreshTokenIsRejectedAsNotAnAccessToken() {
    val token = mint(SECRET, UUID.randomUUID().toString(), "a@example.com",
      "ROLE_USER", JwtValidator.TOKEN_TYPE_REFRESH, 900_000L)

    val ex = assertThrows(JwtValidationException::class.java) { validator.validate(token) }
    assertEquals(Reason.NOT_AN_ACCESS_TOKEN, ex.reason())
  }

  @Test
  fun garbageTokenIsRejectedAsMalformed() {
    val ex = assertThrows(JwtValidationException::class.java) { validator.validate("not-a-jwt") }
    assertEquals(Reason.MALFORMED, ex.reason())
  }

  @Test
  fun nullTokenIsRejectedAsMalformed() {
    // No stub involved: validate(null) must fail closed, not throw NPE.
    val ex = assertThrows(JwtValidationException::class.java) { validator.validate(null) }
    assertEquals(Reason.MALFORMED, ex.reason())
  }

  @Test
  fun shortSecretFailsFastAtConstruction() {
    val ex = assertThrows(IllegalStateException::class.java) { JwtValidator("too-short") }
    assertTrue(ex.message?.contains("APP_JWT_SECRET") ?: false)
  }
}
