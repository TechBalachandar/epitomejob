package com.epitomejob.eureka

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer

/**
 * Standalone Eureka registry for the EpitomeJob microservices (Phase 5A).
 * Port 8761. It does not register with itself and does not fetch a registry
 * (see application.yml) — it IS the registry.
 *
 * The kotlin-spring (all-open) compiler plugin opens this class for Spring,
 * since Kotlin classes are final by default.
 */
@SpringBootApplication
@EnableEurekaServer
class EurekaServerApplication

fun main(args: Array<String>) {
  runApplication<EurekaServerApplication>(*args)
}
