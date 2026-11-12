package com.epitomejob.common.security;

import com.epitomejob.identity.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock
  private JwtService jwt;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain chain;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validBearerTokenSetsAuthentication() throws Exception {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
    when(jwt.parse("valid-token")).thenReturn(claims);

    new JwtAuthFilter(jwt).doFilter(request, response, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertEquals(USER_ID.toString(), auth.getName());
    assertTrue(auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    verify(chain).doFilter(request, response);
  }

  @Test
  void tokenWithoutRoleClaimAuthenticatesWithNoAuthorities() throws Exception {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn(null);
    when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
    when(jwt.parse("valid-token")).thenReturn(claims);

    new JwtAuthFilter(jwt).doFilter(request, response, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertEquals(USER_ID.toString(), auth.getName());
    assertTrue(auth.getAuthorities().isEmpty());
  }

  @Test
  void invalidTokenClearsContextAndStillChains() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            USER_ID.toString(), null, java.util.List.of()));
    when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
    when(jwt.parse("bad-token")).thenThrow(new RuntimeException("bad signature"));

    new JwtAuthFilter(jwt).doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(chain).doFilter(request, response);
  }

  @Test
  void missingHeaderLeavesRequestUnauthenticated() throws Exception {
    when(request.getHeader("Authorization")).thenReturn(null);

    new JwtAuthFilter(jwt).doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(chain).doFilter(request, response);
  }

  @Test
  void nonBearerHeaderIsIgnored() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

    new JwtAuthFilter(jwt).doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(chain).doFilter(request, response);
  }
}
