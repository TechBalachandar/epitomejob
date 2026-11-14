package com.epitomejob.resumebuilder.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JwtAuthFilterTest {

  private static final String SECRET = "test-only-secret-change-me-0123456789abcdef0123456789abcdef";
  private static final UUID USER_ID = UUID.randomUUID();

  private JwtAuthFilter filter;

  @BeforeEach
  void setUp() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    filter = new JwtAuthFilter(new JwtTokenService(SECRET));
  }

  private String validToken() {
    return "Bearer " + new JwtTokenServiceTestHelper().mint(USER_ID, SECRET);
  }

  @Test
  void validBearerReachesTheChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resume-builder/templates");
    request.setServletPath("/resume-builder/templates");
    request.addHeader("Authorization", validToken());
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(org.springframework.security.core.context.SecurityContextHolder
        .getContext().getAuthentication().getName()).isEqualTo(USER_ID.toString());
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @Test
  void missingBearerIs401AndNeverReachesTheChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resume-builder/templates");
    request.setServletPath("/resume-builder/templates");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void badBearerIs401AndNeverReachesTheChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resume-builder/templates");
    request.setServletPath("/resume-builder/templates");
    request.addHeader("Authorization", "Bearer garbage");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void healthPathSkipsTheFilter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    request.setServletPath("/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  /** Small helper so this test does not duplicate the JJWT minting code. */
  static class JwtTokenServiceTestHelper {
    String mint(UUID userId, String secret) {
      return io.jsonwebtoken.Jwts.builder()
          .subject(userId.toString())
          .issuedAt(new java.util.Date())
          .expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(900)))
          .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
              secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
          .compact();
    }
  }
}
