package com.epitomejob.gateway;

import com.epitomejob.common.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EpitomeJob API gateway (Phase 5A). Spring Cloud Gateway on WebFlux/Netty.
 *
 * <p>{@code scanBasePackages} includes {@code com.epitomejob.common} so the
 * shared {@code TraceIdFilter} (a {@code @Component} in the common library)
 * is registered. No {@code @EnableDiscoveryClient} needed: the Eureka client
 * starter auto-configures discovery, and the routes use {@code lb://}
 * URIs against the registry.
 */
@SpringBootApplication(scanBasePackages = {"com.epitomejob.gateway", "com.epitomejob.common"})
public class GatewayServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayServiceApplication.class, args);
  }

  /**
   * The JWT validator, built from the same secret the monolith uses
   * ({@code APP_JWT_SECRET}; dev default is the monolith's compose default —
   * change it in any shared deployment).
   */
  @org.springframework.context.annotation.Bean
  public JwtValidator jwtValidator(
      @Value("${app.jwt.secret}") String secret) {
    return new JwtValidator(secret);
  }
}
