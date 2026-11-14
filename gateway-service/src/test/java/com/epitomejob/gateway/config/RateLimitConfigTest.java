package com.epitomejob.gateway.config;

import com.epitomejob.common.TraceConstants;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link RateLimitConfig}. No Mockito: the resolver is
 * exercised against real {@link MockServerWebExchange} objects, so each
 * key-resolution path is one the production code actually takes.
 */
class RateLimitConfigTest {

  private final RateLimitConfig config = new RateLimitConfig();
  private final KeyResolver resolver = config.userKeyResolver();

  @Test
  void resolvesAuthenticatedUserIdFirst() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/jobs")
            .header(TraceConstants.USER_ID_HEADER, "user-123")
            .remoteAddress(new InetSocketAddress("203.0.113.7", 1234))
            .build());

    StepVerifier.create(resolver.resolve(exchange))
        .expectNext("user-123")
        .verifyComplete();
  }

  @Test
  void fallsBackToClientIpWhenUnauthenticated() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/auth/login")
            .remoteAddress(new InetSocketAddress("203.0.113.7", 1234))
            .build());

    StepVerifier.create(resolver.resolve(exchange))
        .expectNext("203.0.113.7")
        .verifyComplete();
  }

  @Test
  void neverReturnsAnEmptyKey() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/auth/login").build());

    StepVerifier.create(resolver.resolve(exchange))
        .expectNext("unknown")
        .verifyComplete();
  }

  @Test
  void redisRateLimiterBeanIsBuiltFromTheProperties() {
    RateLimitConfig.RateLimitProperties props = new RateLimitConfig.RateLimitProperties();
    // Defaults are the documented limit: 2/sec refill, burst 100.
    assertEquals(2, props.getReplenishRate());
    assertEquals(100, props.getBurstCapacity());
    assertEquals(1, props.getRequestedTokens());

    RedisRateLimiter limiter = config.redisRateLimiter(props);
    assertNotNull(limiter);
  }
}
