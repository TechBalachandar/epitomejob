package com.epitomejob.gateway.config;

import com.epitomejob.common.TraceConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Redis-backed rate limiting for every route (see {@code application.yml}:
 * each route applies the {@code RequestRateLimiter} filter with the beans
 * defined here).
 *
 * <p>Limit: token bucket, <b>100 requests per minute per user/IP</b> —
 * {@code RedisRateLimiter} refills per second, so this is expressed as
 * {@code replenishRate=2} tokens/second (≈120/min sustained) with a
 * {@code burstCapacity=100} token bucket: a caller can burst up to 100
 * requests, then is held to ~2/second. Values live in
 * {@code gateway.rate-limit} in {@code application.yml} and are overridable
 * by env ({@code GATEWAY_RATE_LIMIT_REPLENISH_RATE} etc.).
 *
 * <p>Key: the authenticated user id ({@code X-User-Id}, set by
 * {@link com.epitomejob.gateway.filter.JwtAuthFilter} after JWT validation)
 * when present, otherwise the client IP, otherwise {@code "unknown"} (the
 * resolver never returns empty, so legitimate requests are never denied by
 * the gateway's {@code deny-empty-key} default).
 */
@Configuration
@EnableConfigurationProperties(RateLimitConfig.RateLimitProperties.class)
public class RateLimitConfig {

  @ConfigurationProperties(prefix = "gateway.rate-limit")
  public static class RateLimitProperties {
    /** Tokens added per second (2/s ≈ 120/min sustained). */
    private int replenishRate = 2;
    /** Bucket size: max burst in one go. */
    private int burstCapacity = 100;
    /** Tokens charged per request. */
    private int requestedTokens = 1;

    public int getReplenishRate() {
      return replenishRate;
    }

    public void setReplenishRate(int replenishRate) {
      this.replenishRate = replenishRate;
    }

    public int getBurstCapacity() {
      return burstCapacity;
    }

    public void setBurstCapacity(int burstCapacity) {
      this.burstCapacity = burstCapacity;
    }

    public int getRequestedTokens() {
      return requestedTokens;
    }

    public void setRequestedTokens(int requestedTokens) {
      this.requestedTokens = requestedTokens;
    }
  }

  @Bean
  public KeyResolver userKeyResolver() {
    return exchange -> {
      String userId = exchange.getRequest().getHeaders()
          .getFirst(TraceConstants.USER_ID_HEADER);
      if (userId != null && !userId.isBlank()) {
        return Mono.just(userId);
      }
      var remote = exchange.getRequest().getRemoteAddress();
      if (remote != null && remote.getAddress() != null) {
        return Mono.just(remote.getAddress().getHostAddress());
      }
      return Mono.just("unknown");
    };
  }

  @Bean
  public RedisRateLimiter redisRateLimiter(RateLimitProperties props) {
    return new RedisRateLimiter(props.getReplenishRate(), props.getBurstCapacity(),
        props.getRequestedTokens());
  }
}
