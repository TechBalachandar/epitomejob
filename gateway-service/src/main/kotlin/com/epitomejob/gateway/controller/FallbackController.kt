package com.epitomejob.gateway.controller

import com.epitomejob.common.ApiError
import com.epitomejob.common.TraceConstants
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Fallback endpoints for the routes' `CircuitBreaker` filters
 * (`fallbackUri: forward:/fallback/...`). The forward preserves the
 * original HTTP method, so these are `@RequestMapping` without a
 * method restriction.
 *
 * The circuit breaker hands the cause over as the
 * `CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR` exchange attribute; the
 * body deliberately does not repeat it — the monolith's rule is to never
 * leak internals, and a gateway 503 needs no more than "try again".
 */
@RestController
class FallbackController {

  @RequestMapping("/fallback/backend")
  fun backendFallback(exchange: ServerWebExchange): Mono<ResponseEntity<ApiError>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
          ApiError(
            "SERVICE_UNAVAILABLE",
            "The backend is temporarily unavailable. Please try again.",
            traceId(exchange)
          )
        )
    )

  @RequestMapping("/fallback/mcp")
  fun mcpFallback(exchange: ServerWebExchange): Mono<ResponseEntity<ApiError>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
          ApiError(
            "SERVICE_UNAVAILABLE",
            "The MCP service is temporarily unavailable. Please try again.",
            traceId(exchange)
          )
        )
    )

  @RequestMapping("/fallback/resume-builder")
  fun resumeBuilderFallback(exchange: ServerWebExchange): Mono<ResponseEntity<ApiError>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
          ApiError(
            "SERVICE_UNAVAILABLE",
            "The Resume Builder is temporarily unavailable. Please try again.",
            traceId(exchange)
          )
        )
    )

  private fun traceId(exchange: ServerWebExchange): String? =
    exchange.request.headers.getFirst(TraceConstants.REQUEST_ID_HEADER)
}
