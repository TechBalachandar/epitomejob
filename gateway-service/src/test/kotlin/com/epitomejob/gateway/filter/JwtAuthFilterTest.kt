package com.epitomejob.gateway.filter

import com.epitomejob.common.JwtValidator
import com.epitomejob.common.TraceConstants
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.SecretKey

/**
 * Unit tests for [JwtAuthFilter]. No Mockito: the exchange/request are
 * real [MockServerWebExchange] objects, the validator is the real
 * [JwtValidator], tokens are minted with real JJWT, and the chain is a
 * lambda that records whether it was invoked and with what exchange. Every
 * assertion therefore traces to code that actually ran on that test's path —
 * there are no stubs to go stale (see the Nov 13 test-stub rule).
 */
class JwtAuthFilterTest {

  companion object {
    private const val SECRET = "test-secret-that-is-long-enough-0123456789"
    private const val WRONG_SECRET = "a-different-test-secret-long-enough-0123456789"
  }

  private val filter = JwtAuthFilter(JwtValidator(SECRET))

  private fun mint(secret: String, subject: String, type: String?, ttlMs: Long): String {
    val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    val builder = Jwts.builder()
      .subject(subject)
      .claim(JwtValidator.CLAIM_EMAIL, "a@example.com")
      .claim(JwtValidator.CLAIM_ROLE, "ROLE_USER")
      .issuedAt(Date())
      .expiration(Date(System.currentTimeMillis() + ttlMs))
      .signWith(key)
    if (type != null) {
      builder.claim(JwtValidator.CLAIM_TYPE, type)
    }
    return builder.compact()
  }

  private fun validToken(): String =
    mint(SECRET, UUID.randomUUID().toString(), null, 900_000L)

  /** Runs the filter and returns what the chain saw (or nothing, if blocked). */
  private class Run(val exchange: MockServerWebExchange) {
    val chainInvoked = AtomicBoolean(false)
    val seenByChain = AtomicReference<ServerWebExchange>()

    fun run(filter: JwtAuthFilter) {
      val chain = GatewayFilterChain { e ->
        chainInvoked.set(true)
        seenByChain.set(e)
        Mono.empty<Void>()
      }
      filter.filter(exchange, chain).block()
    }
  }

  private fun runOn(path: String, authHeader: String?): Run {
    val request = MockServerHttpRequest.get(path)
    if (authHeader != null) {
      request.header(TraceConstants.AUTHORIZATION_HEADER, authHeader)
    }
    val run = Run(MockServerWebExchange.from(request.build()))
    run.run(filter)
    return run
  }

  @Test
  fun authPathsPassWithoutToken() {
    val run = runOn("/auth/login", null)
    assertTrue(run.chainInvoked.get(), "POST /auth/login must not need a token")
  }

  @Test
  fun actuatorHealthPassesWithoutToken() {
    val run = runOn("/actuator/health", null)
    assertTrue(run.chainInvoked.get(), "/actuator/health must stay public")
  }

  @Test
  fun securedPathWithoutHeaderIs401() {
    val run = runOn("/profiles/me", null)
    assertFalse(run.chainInvoked.get(), "request must not reach the downstream")
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.response.statusCode)
    assertTrue(
      run.exchange.response.headers.contentType.toString().contains("application/json")
    )
  }

  @Test
  fun nonBearerHeaderIs401() {
    val run = runOn("/profiles/me", "Basic dXNlcjpwYXNz")
    assertFalse(run.chainInvoked.get())
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.response.statusCode)
  }

  @Test
  fun validTokenPassesAndSetsTrustedUserHeaders() {
    val userId = UUID.randomUUID()
    val token = mint(SECRET, userId.toString(), null, 900_000L)
    // A spoofed X-User-Id from the client must be replaced, not trusted.
    val request = MockServerHttpRequest.get("/profiles/me")
      .header(TraceConstants.AUTHORIZATION_HEADER, "Bearer $token")
      .header(TraceConstants.USER_ID_HEADER, "spoofed-id")
      .build()
    val run = Run(MockServerWebExchange.from(request))
    run.run(filter)

    assertTrue(run.chainInvoked.get())
    val headers = run.seenByChain.get().request.headers
    assertEquals(userId.toString(), headers.getFirst(TraceConstants.USER_ID_HEADER))
    assertEquals("a@example.com", headers.getFirst(TraceConstants.USER_EMAIL_HEADER))
    assertEquals("ROLE_USER", headers.getFirst(TraceConstants.USER_ROLE_HEADER))
  }

  @Test
  fun expiredTokenIs401() {
    val token = mint(SECRET, UUID.randomUUID().toString(), null, -1_000L)
    val run = runOn("/profiles/me", "Bearer $token")
    assertFalse(run.chainInvoked.get())
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.response.statusCode)
  }

  @Test
  fun tokenWithBadSignatureIs401() {
    val token = mint(WRONG_SECRET, UUID.randomUUID().toString(), null, 900_000L)
    val run = runOn("/profiles/me", "Bearer $token")
    assertFalse(run.chainInvoked.get())
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.response.statusCode)
  }

  @Test
  fun refreshTokenUsedAsApiCredentialIs401() {
    val token = mint(
      SECRET, UUID.randomUUID().toString(),
      JwtValidator.TOKEN_TYPE_REFRESH, 900_000L
    )
    val run = runOn("/profiles/me", "Bearer $token")
    assertFalse(run.chainInvoked.get())
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.response.statusCode)
  }

  @Test
  fun garbageTokenIs401() {
    val run = runOn("/profiles/me", "Bearer not-a-jwt")
    assertFalse(run.chainInvoked.get())
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.response.statusCode)
  }

  @Test
  fun publicPathMatching() {
    assertTrue(JwtAuthFilter.matches("/auth/**", "/auth/login"))
    assertTrue(JwtAuthFilter.matches("/auth/**", "/auth/refresh"))
    assertTrue(JwtAuthFilter.matches("/actuator/**", "/actuator/health"))
    assertFalse(JwtAuthFilter.matches("/auth/**", "/profiles/me"))
    assertFalse(JwtAuthFilter.matches("/auth/**", "/authentic"))
  }
}
