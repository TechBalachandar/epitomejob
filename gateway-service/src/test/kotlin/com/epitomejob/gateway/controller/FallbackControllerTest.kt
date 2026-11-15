package com.epitomejob.gateway.controller

import com.epitomejob.common.TraceConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.test.StepVerifier

/**
 * Unit tests for [FallbackController]: the circuit-breaker fallbacks
 * must answer 503 with the shared `ApiError` shape and carry the
 * request's trace id through.
 */
class FallbackControllerTest {

  private val controller = FallbackController()

  private fun exchangeWithTraceId(): MockServerWebExchange =
    MockServerWebExchange.from(
      MockServerHttpRequest.get("/profiles/me")
        .header(TraceConstants.REQUEST_ID_HEADER, "trace-9")
        .build()
    )

  @Test
  fun backendFallbackIs503WithApiError() {
    StepVerifier.create(controller.backendFallback(exchangeWithTraceId()))
      .assertNext { response ->
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("SERVICE_UNAVAILABLE", response.body?.code)
        assertTrue(response.body?.message?.contains("backend") == true)
        assertEquals("trace-9", response.body?.traceId)
      }
      .verifyComplete()
  }

  @Test
  fun mcpFallbackIs503WithApiError() {
    StepVerifier.create(controller.mcpFallback(exchangeWithTraceId()))
      .assertNext { response ->
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("SERVICE_UNAVAILABLE", response.body?.code)
        assertTrue(response.body?.message?.contains("MCP") == true)
        assertEquals("trace-9", response.body?.traceId)
      }
      .verifyComplete()
  }
}
