package com.epitomejob.gateway.controller;

import com.epitomejob.common.ApiError;
import com.epitomejob.common.TraceConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Fallback endpoints for the routes' {@code CircuitBreaker} filters
 * ({@code fallbackUri: forward:/fallback/...}). The forward preserves the
 * original HTTP method, so these are {@code @RequestMapping} without a
 * method restriction.
 *
 * <p>The circuit breaker hands the cause over as the
 * {@code CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR} exchange attribute; the
 * body deliberately does not repeat it — the monolith's rule is to never
 * leak internals, and a gateway 503 needs no more than "try again".
 */
@RestController
public class FallbackController {

  @RequestMapping("/fallback/backend")
  public Mono<ResponseEntity<ApiError>> backendFallback(ServerWebExchange exchange) {
    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiError("SERVICE_UNAVAILABLE",
            "The backend is temporarily unavailable. Please try again.",
            traceId(exchange))));
  }

  @RequestMapping("/fallback/mcp")
  public Mono<ResponseEntity<ApiError>> mcpFallback(ServerWebExchange exchange) {
    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiError("SERVICE_UNAVAILABLE",
            "The MCP service is temporarily unavailable. Please try again.",
            traceId(exchange))));
  }

  private static String traceId(ServerWebExchange exchange) {
    return exchange.getRequest().getHeaders()
        .getFirst(TraceConstants.REQUEST_ID_HEADER);
  }
}
