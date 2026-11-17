package com.epitomejob.gateway

import com.epitomejob.common.JwtValidator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/**
 * EpitomeJob API gateway (Phase 5A). Spring Cloud Gateway on WebFlux/Netty.
 *
 * `scanBasePackages` includes `com.epitomejob.common` so the shared
 * `TraceIdFilter` (a `@Component` in the common library) is registered. No
 * `@EnableDiscoveryClient` needed: the Eureka client starter auto-configures
 * discovery, and the routes use `lb://` URIs against the registry.
 */
@SpringBootApplication(scanBasePackages = ["com.epitomejob.gateway", "com.epitomejob.common"])
class GatewayServiceApplication {

  /**
   * The JWT validator, built from the same secret the monolith uses
   * (`APP_JWT_SECRET`; dev default is the monolith's compose default —
   * change it in any shared deployment).
   */
  @Bean
  fun jwtValidator(@Value("\${app.jwt.secret}") secret: String): JwtValidator =
    JwtValidator(secret)
}

fun main(args: Array<String>) {
  runApplication<GatewayServiceApplication>(*args)
}
