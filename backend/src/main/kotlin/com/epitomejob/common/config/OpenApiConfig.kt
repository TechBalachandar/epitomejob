package com.epitomejob.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** OpenAPI docs: JSON at /v3/api-docs, UI at /swagger-ui.html. */
@Configuration
class OpenApiConfig {

  companion object {
    const val SECURITY_SCHEME_NAME = "bearerAuth"
  }

  @Bean
  fun epitomeJobOpenApi(): OpenAPI {
    return OpenAPI()
      .info(
        Info()
          .title("EpitomeJob API")
          .version("0.1.0")
          .description("AI career assistance — Phase 0: identity, profile, resume, approval gate.")
      )
      .components(
        Components().addSecuritySchemes(
          SECURITY_SCHEME_NAME,
          SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
        )
      )
      .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))
  }
}
