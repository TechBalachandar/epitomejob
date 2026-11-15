package com.epitomejob.resumebuilder.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class JwtAuthFilterTest {

  companion object {
    private const val SECRET: String = "test-only-secret-change-me-0123456789abcdef0123456789abcdef"
    private val USER_ID: UUID = UUID.randomUUID()
  }

  private lateinit var filter: JwtAuthFilter

  @BeforeEach
  fun setUp() {
    SecurityContextHolder.clearContext()
    filter = JwtAuthFilter(JwtTokenService(SECRET))
  }

  private fun validToken(): String {
    return "Bearer " + JwtTokenServiceTestHelper().mint(USER_ID, SECRET)
  }

  @Test
  fun validBearerReachesTheChain() {
    val request = MockHttpServletRequest("GET", "/resume-builder/templates")
    request.servletPath = "/resume-builder/templates"
    request.addHeader("Authorization", validToken())
    val response = MockHttpServletResponse()
    val chain = mock(FilterChain::class.java)

    filter.doFilter(request, response, chain)

    verify(chain).doFilter(request, response)
    assertThat(response.status).isNotEqualTo(HttpServletResponse.SC_UNAUTHORIZED)
    assertThat(SecurityContextHolder.getContext().authentication.name).isEqualTo(USER_ID.toString())
    SecurityContextHolder.clearContext()
  }

  @Test
  fun missingBearerIs401AndNeverReachesTheChain() {
    val request = MockHttpServletRequest("GET", "/resume-builder/templates")
    request.servletPath = "/resume-builder/templates"
    val response = MockHttpServletResponse()
    val chain = mock(FilterChain::class.java)

    filter.doFilter(request, response, chain)

    assertThat(response.status).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED)
    verify(chain, never()).doFilter(request, response)
  }

  @Test
  fun badBearerIs401AndNeverReachesTheChain() {
    val request = MockHttpServletRequest("GET", "/resume-builder/templates")
    request.servletPath = "/resume-builder/templates"
    request.addHeader("Authorization", "Bearer garbage")
    val response = MockHttpServletResponse()
    val chain = mock(FilterChain::class.java)

    filter.doFilter(request, response, chain)

    assertThat(response.status).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED)
    verify(chain, never()).doFilter(request, response)
  }

  @Test
  fun healthPathSkipsTheFilter() {
    val request = MockHttpServletRequest("GET", "/actuator/health")
    request.servletPath = "/actuator/health"
    val response = MockHttpServletResponse()
    val chain = mock(FilterChain::class.java)

    filter.doFilter(request, response, chain)

    verify(chain).doFilter(request, response)
  }

  /** Small helper so this test does not duplicate the JJWT minting code. */
  class JwtTokenServiceTestHelper {
    fun mint(userId: UUID, secret: String): String {
      return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date())
        .expiration(Date.from(Instant.now().plusSeconds(900)))
        .signWith(
          Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
        )
        .compact()
    }
  }
}
