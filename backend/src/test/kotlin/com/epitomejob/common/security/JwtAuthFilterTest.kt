package com.epitomejob.common.security

import com.epitomejob.identity.service.JwtService
import io.jsonwebtoken.Claims
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class JwtAuthFilterTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
  }

  @Mock
  private lateinit var jwt: JwtService

  @Mock
  private lateinit var request: HttpServletRequest

  @Mock
  private lateinit var response: HttpServletResponse

  @Mock
  private lateinit var chain: FilterChain

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  @Test
  fun validBearerTokenSetsAuthentication() {
    val claims = mock(Claims::class.java)
    Mockito.`when`(claims.subject).thenReturn(USER_ID.toString())
    Mockito.`when`(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    Mockito.`when`(request.getHeader("Authorization")).thenReturn("Bearer valid-token")
    Mockito.`when`(jwt.parse("valid-token")).thenReturn(claims)

    JwtAuthFilter(jwt).doFilter(request, response, chain)

    val auth = SecurityContextHolder.getContext().authentication
    assertEquals(USER_ID.toString(), auth?.name)
    assertTrue(auth?.authorities?.any { a -> a.authority == "ROLE_USER" } ?: false)
    verify(chain).doFilter(request, response)
  }

  @Test
  fun tokenWithoutRoleClaimAuthenticatesWithNoAuthorities() {
    val claims = mock(Claims::class.java)
    Mockito.`when`(claims.subject).thenReturn(USER_ID.toString())
    // thenReturn(null) does not compile for this platform-typed stub in Kotlin;
    // thenAnswer returning null is the equivalent stub.
    Mockito.`when`(claims.get("role", String::class.java)).thenAnswer { null }
    Mockito.`when`(request.getHeader("Authorization")).thenReturn("Bearer valid-token")
    Mockito.`when`(jwt.parse("valid-token")).thenReturn(claims)

    JwtAuthFilter(jwt).doFilter(request, response, chain)

    val auth = SecurityContextHolder.getContext().authentication
    assertEquals(USER_ID.toString(), auth?.name)
    assertTrue(auth?.authorities?.isEmpty() ?: false)
  }

  @Test
  fun invalidTokenClearsContextAndStillChains() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, listOf())
    Mockito.`when`(request.getHeader("Authorization")).thenReturn("Bearer bad-token")
    Mockito.`when`(jwt.parse("bad-token")).thenThrow(RuntimeException("bad signature"))

    JwtAuthFilter(jwt).doFilter(request, response, chain)

    assertNull(SecurityContextHolder.getContext().authentication)
    verify(chain).doFilter(request, response)
  }

  @Test
  fun missingHeaderLeavesRequestUnauthenticated() {
    Mockito.`when`(request.getHeader("Authorization")).thenAnswer { null }

    JwtAuthFilter(jwt).doFilter(request, response, chain)

    assertNull(SecurityContextHolder.getContext().authentication)
    verify(chain).doFilter(request, response)
  }

  @Test
  fun nonBearerHeaderIsIgnored() {
    Mockito.`when`(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz")

    JwtAuthFilter(jwt).doFilter(request, response, chain)

    assertNull(SecurityContextHolder.getContext().authentication)
    verify(chain).doFilter(request, response)
  }
}
