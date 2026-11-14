package com.epitomejob.gateway.controller;

import com.epitomejob.common.TraceConstants;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FallbackController}: the circuit-breaker fallbacks
 * must answer 503 with the shared {@code ApiError} shape and carry the
 * request's trace id through.
 */
class FallbackControllerTest {

  private final FallbackController controller = new FallbackController();

  private MockServerWebExchange exchangeWithTraceId() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get("/profiles/me")
            .header(TraceConstants.REQUEST_ID_HEADER, "trace-9")
            .build());
  }

  @Test
  void backendFallbackIs503WithApiError() {
    StepVerifier.create(controller.backendFallback(exchangeWithTraceId()))
        .assertNext(response -> {
          assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
          assertEquals("SERVICE_UNAVAILABLE", response.getBody().code());
          assertTrue(response.getBody().message().contains("backend"));
          assertEquals("trace-9", response.getBody().traceId());
        })
        .verifyComplete();
  }

  @Test
  void mcpFallbackIs503WithApiError() {
    StepVerifier.create(controller.mcpFallback(exchangeWithTraceId()))
        .assertNext(response -> {
          assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
          assertEquals("SERVICE_UNAVAILABLE", response.getBody().code());
          assertTrue(response.getBody().message().contains("MCP"));
          assertEquals("trace-9", response.getBody().traceId());
        })
        .verifyComplete();
  }
}
