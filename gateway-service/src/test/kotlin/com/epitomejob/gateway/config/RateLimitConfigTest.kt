package com.epitomejob.gateway.config

import com.epitomejob.common.TraceConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.test.StepVerifier
import java.net.InetSocketAddress

/**
 * Unit tests for [RateLimitConfig]. No Mockito: the resolver is
 * exercised against real [MockServerWebExchange] objects, so each
 * key-resolution path is one the production code actually takes.
 */
class RateLimitConfigTest {

  private val config = RateLimitConfig()
  private val resolver: KeyResolver = config.userKeyResolver()

  @Test
  fun resolvesAuthenticatedUserIdFirst() {
    val exchange = MockServerWebExchange.from(
      MockServerHttpRequest.get("/jobs")
        .header(TraceConstants.USER_ID_HEADER, "user-123")
        .remoteAddress(InetSocketAddress("203.0.113.7", 1234))
        .build()
    )

    StepVerifier.create(resolver.resolve(exchange))
      .expectNext("user-123")
      .verifyComplete()
  }

  @Test
  fun fallsBackToClientIpWhenUnauthenticated() {
    val exchange = MockServerWebExchange.from(
      MockServerHttpRequest.get("/auth/login")
        .remoteAddress(InetSocketAddress("203.0.113.7", 1234))
        .build()
    )

    StepVerifier.create(resolver.resolve(exchange))
      .expectNext("203.0.113.7")
      .verifyComplete()
  }

  @Test
  fun neverReturnsAnEmptyKey() {
    val exchange = MockServerWebExchange.from(
      MockServerHttpRequest.get("/auth/login").build()
    )

    StepVerifier.create(resolver.resolve(exchange))
      .expectNext("unknown")
      .verifyComplete()
  }

  @Test
  fun redisRateLimiterBeanIsBuiltFromTheProperties() {
    val props = RateLimitConfig.RateLimitProperties()
    // Defaults are the documented limit: 2/sec refill, burst 100.
    assertEquals(2, props.replenishRate)
    assertEquals(100, props.burstCapacity)
    assertEquals(1, props.requestedTokens)

    val limiter: RedisRateLimiter = config.redisRateLimiter(props)
    assertNotNull(limiter)
  }
}
