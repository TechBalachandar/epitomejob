package com.epitomejob.gateway.config

import com.epitomejob.common.TraceConstants
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

/**
 * Redis-backed rate limiting for every route (see `application.yml`:
 * each route applies the `RequestRateLimiter` filter with the beans
 * defined here).
 *
 * Limit: token bucket, **100 requests per minute per user/IP** —
 * `RedisRateLimiter` refills per second, so this is expressed as
 * `replenishRate=2` tokens/second (≈120/min sustained) with a
 * `burstCapacity=100` token bucket: a caller can burst up to 100
 * requests, then is held to ~2/second. Values live in
 * `gateway.rate-limit` in `application.yml` and are overridable
 * by env (`GATEWAY_RATE_LIMIT_REPLENISH_RATE` etc.).
 *
 * Key: the authenticated user id (`X-User-Id`, set by
 * [com.epitomejob.gateway.filter.JwtAuthFilter] after JWT validation)
 * when present, otherwise the client IP, otherwise `"unknown"` (the
 * resolver never returns empty, so legitimate requests are never denied by
 * the gateway's `deny-empty-key` default).
 */
@Configuration
@EnableConfigurationProperties(RateLimitConfig.RateLimitProperties::class)
class RateLimitConfig {

  @ConfigurationProperties(prefix = "gateway.rate-limit")
  class RateLimitProperties {
    /** Tokens added per second (2/s ≈ 120/min sustained). */
    var replenishRate: Int = 2
    /** Bucket size: max burst in one go. */
    var burstCapacity: Int = 100
    /** Tokens charged per request. */
    var requestedTokens: Int = 1
  }

  @Bean
  fun userKeyResolver(): KeyResolver = KeyResolver { exchange ->
    val userId = exchange.request.headers.getFirst(TraceConstants.USER_ID_HEADER)
    if (userId != null && userId.isNotBlank()) {
      Mono.just(userId)
    } else {
      val remote = exchange.request.remoteAddress
      val hostAddress = remote?.address?.hostAddress
      if (hostAddress != null) {
        Mono.just(hostAddress)
      } else {
        Mono.just("unknown")
      }
    }
  }

  @Bean
  fun redisRateLimiter(props: RateLimitProperties): RedisRateLimiter =
    RedisRateLimiter(props.replenishRate, props.burstCapacity, props.requestedTokens)
}
