package com.epitomejob.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI docs: JSON at /v3/api-docs, UI at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

  public static final String SECURITY_SCHEME_NAME = "bearerAuth";

  @Bean
  public OpenAPI epitomeJobOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("EpitomeJob API")
            .version("0.1.0")
            .description("AI career assistance — Phase 0: identity, profile, resume, approval gate."))
        .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
  }
}
