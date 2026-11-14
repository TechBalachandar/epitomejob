package com.epitomejob.gateway.filter;

import com.epitomejob.common.JwtValidator;
import com.epitomejob.common.TraceConstants;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JwtAuthFilter}. No Mockito: the exchange/request are
 * real {@link MockServerWebExchange} objects, the validator is the real
 * {@link JwtValidator}, tokens are minted with real JJWT, and the chain is a
 * lambda that records whether it was invoked and with what exchange. Every
 * assertion therefore traces to code that actually ran on that test's path —
 * there are no stubs to go stale (see the Nov 13 test-stub rule).
 */
class JwtAuthFilterTest {

  private static final String SECRET = "test-secret-that-is-long-enough-0123456789";
  private static final String WRONG_SECRET = "a-different-test-secret-long-enough-0123456789";

  private final JwtAuthFilter filter = new JwtAuthFilter(new JwtValidator(SECRET));

  private String mint(String secret, String subject, String type, long ttlMs) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    var builder = Jwts.builder()
        .subject(subject)
        .claim(JwtValidator.CLAIM_EMAIL, "a@example.com")
        .claim(JwtValidator.CLAIM_ROLE, "ROLE_USER")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + ttlMs))
        .signWith(key);
    if (type != null) {
      builder.claim(JwtValidator.CLAIM_TYPE, type);
    }
    return builder.compact();
  }

  private String validToken() {
    return mint(SECRET, UUID.randomUUID().toString(), null, 900_000);
  }

  /** Runs the filter and returns what the chain saw (or nothing, if blocked). */
  private static class Run {
    final MockServerWebExchange exchange;
    final AtomicBoolean chainInvoked = new AtomicBoolean(false);
    final AtomicReference<ServerWebExchange> seenByChain = new AtomicReference<>();

    Run(MockServerWebExchange exchange) {
      this.exchange = exchange;
    }

    void run(JwtAuthFilter filter) {
      GatewayFilterChain chain = e -> {
        chainInvoked.set(true);
        seenByChain.set(e);
        return reactor.core.publisher.Mono.empty();
      };
      filter.filter(exchange, chain).block();
    }
  }

  private Run runOn(String path, String authHeader) {
    var request = MockServerHttpRequest.get(path);
    if (authHeader != null) {
      request.header(TraceConstants.AUTHORIZATION_HEADER, authHeader);
    }
    Run run = new Run(MockServerWebExchange.from(request.build()));
    run.run(filter);
    return run;
  }

  @Test
  void authPathsPassWithoutToken() {
    Run run = runOn("/auth/login", null);
    assertTrue(run.chainInvoked.get(), "POST /auth/login must not need a token");
  }

  @Test
  void actuatorHealthPassesWithoutToken() {
    Run run = runOn("/actuator/health", null);
    assertTrue(run.chainInvoked.get(), "/actuator/health must stay public");
  }

  @Test
  void securedPathWithoutHeaderIs401() {
    Run run = runOn("/profiles/me", null);
    assertFalse(run.chainInvoked.get(), "request must not reach the downstream");
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.getResponse().getStatusCode());
    assertTrue(run.exchange.getResponse().getHeaders().getContentType()
        .toString().contains("application/json"));
  }

  @Test
  void nonBearerHeaderIs401() {
    Run run = runOn("/profiles/me", "Basic dXNlcjpwYXNz");
    assertFalse(run.chainInvoked.get());
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.getResponse().getStatusCode());
  }

  @Test
  void validTokenPassesAndSetsTrustedUserHeaders() {
    UUID userId = UUID.randomUUID();
    String token = mint(SECRET, userId.toString(), null, 900_000);
    // A spoofed X-User-Id from the client must be replaced, not trusted.
    var request = MockServerHttpRequest.get("/profiles/me")
        .header(TraceConstants.AUTHORIZATION_HEADER, "Bearer " + token)
        .header(TraceConstants.USER_ID_HEADER, "spoofed-id")
        .build();
    Run run = new Run(MockServerWebExchange.from(request));
    run.run(filter);

    assertTrue(run.chainInvoked.get());
    var headers = run.seenByChain.get().getRequest().getHeaders();
    assertEquals(userId.toString(), headers.getFirst(TraceConstants.USER_ID_HEADER));
    assertEquals("a@example.com", headers.getFirst(TraceConstants.USER_EMAIL_HEADER));
    assertEquals("ROLE_USER", headers.getFirst(TraceConstants.USER_ROLE_HEADER));
  }

  @Test
  void expiredTokenIs401() {
    String token = mint(SECRET, UUID.randomUUID().toString(), null, -1_000);
    Run run = runOn("/profiles/me", "Bearer " + token);
    assertFalse(run.chainInvoked.get());
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.getResponse().getStatusCode());
  }

  @Test
  void tokenWithBadSignatureIs401() {
    String token = mint(WRONG_SECRET, UUID.randomUUID().toString(), null, 900_000);
    Run run = runOn("/profiles/me", "Bearer " + token);
    assertFalse(run.chainInvoked.get());
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.getResponse().getStatusCode());
  }

  @Test
  void refreshTokenUsedAsApiCredentialIs401() {
    String token = mint(SECRET, UUID.randomUUID().toString(),
        JwtValidator.TOKEN_TYPE_REFRESH, 900_000);
    Run run = runOn("/profiles/me", "Bearer " + token);
    assertFalse(run.chainInvoked.get());
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.getResponse().getStatusCode());
  }

  @Test
  void garbageTokenIs401() {
    Run run = runOn("/profiles/me", "Bearer not-a-jwt");
    assertFalse(run.chainInvoked.get());
    assertEquals(HttpStatus.UNAUTHORIZED, run.exchange.getResponse().getStatusCode());
  }

  @Test
  void publicPathMatching() {
    assertTrue(JwtAuthFilter.matches("/auth/**", "/auth/login"));
    assertTrue(JwtAuthFilter.matches("/auth/**", "/auth/refresh"));
    assertTrue(JwtAuthFilter.matches("/actuator/**", "/actuator/health"));
    assertFalse(JwtAuthFilter.matches("/auth/**", "/profiles/me"));
    assertFalse(JwtAuthFilter.matches("/auth/**", "/authentic"));
  }
}
