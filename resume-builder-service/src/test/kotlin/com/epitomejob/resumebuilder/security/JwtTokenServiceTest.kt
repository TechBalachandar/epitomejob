package com.epitomejob.resumebuilder.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class JwtTokenServiceTest {

  companion object {
    private const val SECRET: String = "test-only-secret-change-me-0123456789abcdef0123456789abcdef"

    private fun key(secret: String): SecretKey {
      return Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    }
  }

  private val service = JwtTokenService(SECRET)

  private fun mint(userId: UUID, type: String, expiry: Date, secret: String): String {
    return Jwts.builder()
      .subject(userId.toString())
      .claim("type", type)
      .issuedAt(Date())
      .expiration(expiry)
      .signWith(key(secret))
      .compact()
  }

  @Test
  fun validTokenReturnsSubject() {
    val userId = UUID.randomUUID()
    val token = mint(
      userId, "access",
      Date.from(Instant.now().plusSeconds(900)), SECRET
    )
    assertThat(service.userIdFromToken(token)).isEqualTo(userId)
  }

  @Test
  fun tokenWithoutTypeClaimIsAccepted() {
    val userId = UUID.randomUUID()
    val token = Jwts.builder().subject(userId.toString())
      .issuedAt(Date()).expiration(Date.from(Instant.now().plusSeconds(900)))
      .signWith(key(SECRET)).compact()
    assertThat(service.userIdFromToken(token)).isEqualTo(userId)
  }

  @Test
  fun refreshTokenIsRejected() {
    val token = mint(
      UUID.randomUUID(), "refresh",
      Date.from(Instant.now().plusSeconds(900)), SECRET
    )
    assertThatThrownBy { service.userIdFromToken(token) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun expiredTokenIsRejected() {
    val token = mint(
      UUID.randomUUID(), "access",
      Date.from(Instant.now().minusSeconds(60)), SECRET
    )
    assertThatThrownBy { service.userIdFromToken(token) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun tokenSignedWithAnotherSecretIsRejected() {
    val token = mint(
      UUID.randomUUID(), "access",
      Date.from(Instant.now().plusSeconds(900)),
      "a-completely-different-secret-0123456789abcdef0123456789"
    )
    assertThatThrownBy { service.userIdFromToken(token) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun garbageIsRejected() {
    assertThatThrownBy { service.userIdFromToken("not.a.token") }
      .isInstanceOf(IllegalArgumentException::class.java)
  }
}
