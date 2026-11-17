package com.epitomejob.identity.service

import com.epitomejob.identity.model.User
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JwtServiceTest {

  companion object {
    private const val SECRET = "test-secret-that-is-at-least-32-chars-long!!"
  }

  private fun service(): JwtService {
    return JwtService(SECRET, 900_000L, 604_800_000L)
  }

  @Test
  fun signedTokenParsesBack() {
    val user = User("a@b.com", "hash", "A B")
    val token = service().generateAccess(user)
    assertEquals(user.id, service().extractUserId(token))
  }

  @Test
  fun tamperedTokenFails() {
    val user = User("a@b.com", "hash", "A B")
    val token = service().generateAccess(user)
    assertThrows(JwtException::class.java) { service().parse(token + "x") }
  }

  @Test
  fun tokenSignedWithAnotherSecretFails() {
    val user = User("a@b.com", "hash", "A B")
    val token = service().generateAccess(user)
    val otherKey = JwtService("a-different-secret-that-is-also-32-chars!!", 900_000L, 604_800_000L)
    assertThrows(JwtException::class.java) { otherKey.parse(token) }
  }

  @Test
  fun expiredTokenFails() {
    val expired = JwtService(SECRET, -1_000L, 604_800_000L)
    val user = User("a@b.com", "hash", "A B")
    val token = expired.generateAccess(user)
    assertThrows(ExpiredJwtException::class.java) { expired.parse(token) }
  }

  @Test
  fun refreshTokenCarriesRefreshTypeClaim() {
    val user = User("a@b.com", "hash", "A B")
    val token = service().generateRefresh(user)
    assertEquals("refresh", service().parse(token).get("type", String::class.java))
    assertEquals(user.id, service().extractUserId(token))
  }

  @Test
  fun shortSecretIsRejectedAtStartup() {
    assertThrows(IllegalStateException::class.java) {
      JwtService("too-short", 900_000L, 604_800_000L)
    }
  }

  @Test
  fun expirationComesFromConfiguration() {
    assertEquals(900_000L, service().expirationMs())
    assertEquals(604_800_000L, service().refreshExpirationMs())
  }

  @Test
  fun accessTokenCarriesEmailAndRoleClaims() {
    val user = User("a@b.com", "hash", "A B")
    val token = service().generateAccess(user)
    val claims = service().parse(token)
    assertEquals("a@b.com", claims.get("email", String::class.java))
    assertTrue(claims.get("role", String::class.java).startsWith("ROLE_"))
  }
}
