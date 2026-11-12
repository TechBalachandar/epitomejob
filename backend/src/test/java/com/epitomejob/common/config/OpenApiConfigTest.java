package com.epitomejob.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

  @Test
  void openApiHasInfoAndBearerSecurity() {
    var openApi = new OpenApiConfig().epitomeJobOpenApi();

    assertEquals("EpitomeJob API", openApi.getInfo().getTitle());
    assertEquals("0.1.0", openApi.getInfo().getVersion());
    assertNotNull(openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.SECURITY_SCHEME_NAME));
    assertTrue(openApi.getSecurity().stream()
        .anyMatch(req -> req.containsKey(OpenApiConfig.SECURITY_SCHEME_NAME)));
  }
}
