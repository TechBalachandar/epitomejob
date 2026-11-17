package com.epitomejob.identity.service

import com.epitomejob.common.util.HashUtil
import com.epitomejob.identity.model.RefreshToken
import com.epitomejob.identity.model.User
import com.epitomejob.identity.repository.RefreshTokenRepository
import com.epitomejob.identity.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
  }

  @Mock
  private lateinit var users: UserRepository

  @Mock
  private lateinit var refreshTokens: RefreshTokenRepository

  @Mock
  private lateinit var passwords: PasswordEncoder

  @Mock
  private lateinit var jwt: JwtService

  private fun service(): AuthService {
    return AuthService(users, refreshTokens, passwords, jwt)
  }

  private fun user(): User {
    return User("a@b.com", "hashed", "A B")
  }

  private fun stubTokenIssue(user: User) {
    Mockito.`when`(jwt.generateAccess(user)).thenReturn("access-token")
    Mockito.`when`(jwt.generateRefresh(user)).thenReturn("refresh-token")
    Mockito.`when`(jwt.expirationMs()).thenReturn(900_000L)
    Mockito.`when`(jwt.refreshExpirationMs()).thenReturn(604_800_000L)
    Mockito.`when`(refreshTokens.save(any())).thenAnswer { inv -> inv.getArgument(0) }
  }

  // ---- register ----

  @Test
  fun registerSavesEncodedPassword() {
    Mockito.`when`(users.existsByEmail("a@b.com")).thenReturn(false)
    Mockito.`when`(passwords.encode("Passw0rd!")).thenReturn("hashed")
    Mockito.`when`(users.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val saved = service().register("a@b.com", "A B", "Passw0rd!")

    assertEquals("hashed", saved.passwordHash)
    verify(users).save(any(User::class.java))
  }

  @Test
  fun registerDuplicateEmailConflicts() {
    Mockito.`when`(users.existsByEmail("a@b.com")).thenReturn(true)
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().register("a@b.com", "A B", "Passw0rd!")
    }
    assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    verify(users, never()).save(any())
  }

  @Test
  fun registerShortPasswordIsRejected() {
    Mockito.`when`(users.existsByEmail("a@b.com")).thenReturn(false)
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().register("a@b.com", "A B", "short")
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun registerNullPasswordIsRejected() {
    Mockito.`when`(users.existsByEmail("a@b.com")).thenReturn(false)
    assertThrows(ResponseStatusException::class.java) {
      service().register("a@b.com", "A B", null)
    }
  }

  // ---- login ----

  @Test
  fun loginIssuesTokenPair() {
    val user = user()
    Mockito.`when`(users.findByEmail("a@b.com")).thenReturn(Optional.of(user))
    Mockito.`when`(passwords.matches("Passw0rd!", "hashed")).thenReturn(true)
    stubTokenIssue(user)

    val result = service().login("a@b.com", "Passw0rd!")

    assertEquals("access-token", result.accessToken)
    assertEquals("refresh-token", result.refreshToken)
    assertEquals(900_000L, result.expiresInMs)
    verify(refreshTokens).save(any(RefreshToken::class.java))
  }

  @Test
  fun loginWrongPasswordIsUnauthorized() {
    val user = user()
    Mockito.`when`(users.findByEmail("a@b.com")).thenReturn(Optional.of(user))
    Mockito.`when`(passwords.matches("wrong", "hashed")).thenReturn(false)
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().login("a@b.com", "wrong")
    }
    assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    assertEquals("Invalid credentials", ex.reason)
  }

  @Test
  fun loginUnknownEmailIsUnauthorizedWithSameMessage() {
    Mockito.`when`(users.findByEmail("ghost@b.com")).thenReturn(Optional.empty())
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().login("ghost@b.com", "whatever")
    }
    assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    assertEquals("Invalid credentials", ex.reason)
  }

  // ---- refresh ----

  @Test
  fun refreshRotatesOldToken() {
    val user = user()
    val stored = RefreshToken(
      USER_ID, HashUtil.sha256("refresh-token"),
      Instant.now().plusSeconds(3600)
    )
    Mockito.`when`(refreshTokens.findByTokenHash(HashUtil.sha256("refresh-token")))
      .thenReturn(Optional.of(stored))
    Mockito.`when`(users.findById(USER_ID)).thenReturn(Optional.of(user))
    stubTokenIssue(user)

    val result = service().refresh("refresh-token")

    assertTrue(stored.revoked, "old token must die on rotation")
    assertEquals("access-token", result.accessToken)
  }

  @Test
  fun refreshWithRevokedTokenRevokesAllUserTokens() {
    val revoked = RefreshToken(
      USER_ID, HashUtil.sha256("old"),
      Instant.now().plusSeconds(3600)
    )
    revoked.revoked = true
    val live = RefreshToken(
      USER_ID, HashUtil.sha256("live"),
      Instant.now().plusSeconds(3600)
    )
    Mockito.`when`(refreshTokens.findByTokenHash(HashUtil.sha256("old")))
      .thenReturn(Optional.of(revoked))
    Mockito.`when`(jwt.extractUserId("old")).thenReturn(USER_ID)
    Mockito.`when`(refreshTokens.findByUserIdAndRevokedFalse(USER_ID)).thenReturn(mutableListOf(live))

    val ex = assertThrows(ResponseStatusException::class.java) { service().refresh("old") }

    assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    assertTrue(live.revoked, "reuse of a revoked token must revoke every live token")
  }

  @Test
  fun refreshWithExpiredStoredTokenIsUnauthorized() {
    val expired = RefreshToken(
      USER_ID, HashUtil.sha256("stale"),
      Instant.now().minusSeconds(60)
    )
    Mockito.`when`(refreshTokens.findByTokenHash(HashUtil.sha256("stale")))
      .thenReturn(Optional.of(expired))
    Mockito.`when`(jwt.extractUserId("stale")).thenThrow(RuntimeException("unparseable"))

    val ex = assertThrows(ResponseStatusException::class.java) { service().refresh("stale") }

    assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
  }

  @Test
  fun refreshWithUnknownUserIsUnauthorized() {
    val stored = RefreshToken(
      USER_ID, HashUtil.sha256("refresh-token"),
      Instant.now().plusSeconds(3600)
    )
    Mockito.`when`(refreshTokens.findByTokenHash(HashUtil.sha256("refresh-token")))
      .thenReturn(Optional.of(stored))
    Mockito.`when`(users.findById(USER_ID)).thenReturn(Optional.empty())

    assertThrows(ResponseStatusException::class.java) { service().refresh("refresh-token") }
  }

  // ---- logout ----

  @Test
  fun logoutRevokesToken() {
    val stored = RefreshToken(
      USER_ID, HashUtil.sha256("refresh-token"),
      Instant.now().plusSeconds(3600)
    )
    Mockito.`when`(refreshTokens.findByTokenHash(HashUtil.sha256("refresh-token")))
      .thenReturn(Optional.of(stored))

    service().logout("refresh-token")

    assertTrue(stored.revoked)
  }

  @Test
  fun logoutUnknownTokenIsNoop() {
    Mockito.`when`(refreshTokens.findByTokenHash(HashUtil.sha256("nope")))
      .thenReturn(Optional.empty())
    service().logout("nope") // must not throw
  }
}
