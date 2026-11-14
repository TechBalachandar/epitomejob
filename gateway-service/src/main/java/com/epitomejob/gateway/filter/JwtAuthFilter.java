package com.epitomejob.gateway.filter;

import com.epitomejob.common.ApiError;
import com.epitomejob.common.JwtValidator;
import com.epitomejob.common.JwtValidator.JwtClaims;
import com.epitomejob.common.JwtValidator.JwtValidationException;
import com.epitomejob.common.TraceConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global JWT authentication filter. Runs before routing (order -100, so it
 * also runs before the per-route {@code RequestRateLimiter}, which keys on
 * the user id this filter sets).
 *
 * <ul>
 *   <li>{@code /auth/**} and {@code /actuator/**} are public — no token needed.</li>
 *   <li>Every other route requires a valid Bearer access token: missing
 *       header, expired token, bad signature, malformed token, or a refresh
 *       token used as an API credential all return {@code 401} with an
 *       {@link ApiError} body. Nothing is forwarded downstream on failure.</li>
 *   <li>On success the request is mutated: any client-supplied
 *       {@code X-User-*} headers are stripped first (spoof protection),
 *       then {@code X-User-Id} / {@code X-User-Email} / {@code X-User-Role}
 *       are set from the validated claims for the downstream service.</li>
 * </ul>
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

  /** Paths that do not need a token. Ant-style, matched against the path. */
  private static final List<String> PUBLIC_PATHS = List.of("/auth/**", "/actuator/**");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JwtValidator jwtValidator;

  public JwtAuthFilter(JwtValidator jwtValidator) {
    this.jwtValidator = jwtValidator;
  }

  @Override
  public int getOrder() {
    // After TraceIdFilter (a WebFilter, runs first) and before routing and
    // before the RequestRateLimiter route filter (order 0).
    return -100;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (isPublic(path)) {
      return chain.filter(exchange);
    }

    String authHeader = exchange.getRequest().getHeaders()
        .getFirst(TraceConstants.AUTHORIZATION_HEADER);
    if (authHeader == null || !authHeader.startsWith(TraceConstants.BEARER_PREFIX)) {
      return unauthorized(exchange, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(TraceConstants.BEARER_PREFIX.length());
    final JwtClaims claims;
    try {
      claims = jwtValidator.validate(token);
    } catch (JwtValidationException e) {
      return unauthorized(exchange, messageFor(e));
    }

    ServerHttpRequest mutated = exchange.getRequest().mutate()
        .headers(headers -> {
          // Strip anything the client smuggled in, then set trusted values.
          headers.remove(TraceConstants.USER_ID_HEADER);
          headers.remove(TraceConstants.USER_EMAIL_HEADER);
          headers.remove(TraceConstants.USER_ROLE_HEADER);
          headers.set(TraceConstants.USER_ID_HEADER, claims.userId().toString());
          if (claims.email() != null) {
            headers.set(TraceConstants.USER_EMAIL_HEADER, claims.email());
          }
          if (claims.role() != null) {
            headers.set(TraceConstants.USER_ROLE_HEADER, claims.role());
          }
        })
        .build();
    return chain.filter(exchange.mutate().request(mutated).build());
  }

  private static boolean isPublic(String path) {
    return PUBLIC_PATHS.stream().anyMatch(pattern -> matches(pattern, path));
  }

  /** Minimal Ant-style matcher for the two public patterns (no extra dep). */
  public static boolean matches(String pattern, String path) {
    if (pattern.endsWith("/**")) {
      String prefix = pattern.substring(0, pattern.length() - 3);
      return path.equals(prefix) || path.startsWith(prefix + "/");
    }
    return pattern.equals(path);
  }

  private static String messageFor(JwtValidationException e) {
    return switch (e.reason()) {
      case EXPIRED -> "Token has expired";
      case BAD_SIGNATURE -> "Token signature is invalid";
      case NOT_AN_ACCESS_TOKEN -> "Refresh tokens cannot be used as API credentials";
      case MALFORMED -> "Token is malformed";
    };
  }

  private static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String traceId = exchange.getRequest().getHeaders()
        .getFirst(TraceConstants.REQUEST_ID_HEADER);
    ApiError error = new ApiError("UNAUTHORIZED", message, traceId);
    byte[] bytes;
    try {
      bytes = MAPPER.writeValueAsBytes(error);
    } catch (JsonProcessingException e) {
      bytes = ("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}")
          .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
  }
}
