package com.epitomejob.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Standalone Eureka registry for the EpitomeJob microservices (Phase 5A).
 * Port 8761. It does not register with itself and does not fetch a registry
 * (see application.yml) — it IS the registry.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(EurekaServerApplication.class, args);
  }
}
