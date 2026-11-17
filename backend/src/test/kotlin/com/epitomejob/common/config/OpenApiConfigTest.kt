package com.epitomejob.common.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class OpenApiConfigTest {

  @Test
  fun openApiHasInfoAndBearerSecurity() {
    val openApi = OpenApiConfig().epitomeJobOpenApi()

    assertEquals("EpitomeJob API", openApi.info.title)
    assertEquals("0.1.0", openApi.info.version)
    assertNotNull(openApi.components.securitySchemes[OpenApiConfig.SECURITY_SCHEME_NAME])
    assertTrue(openApi.security.any { req -> req.containsKey(OpenApiConfig.SECURITY_SCHEME_NAME) })
  }
}
