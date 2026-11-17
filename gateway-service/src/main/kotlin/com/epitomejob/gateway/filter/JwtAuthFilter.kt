package com.epitomejob.gateway.filter

import com.epitomejob.common.ApiError
import com.epitomejob.common.JwtValidator
import com.epitomejob.common.JwtValidator.JwtValidationException
import com.epitomejob.common.TraceConstants
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

/**
 * Global JWT authentication filter. Runs before routing (order -100, so it
 * also runs before the per-route `RequestRateLimiter`, which keys on
 * the user id this filter sets).
 *
 * - Paths under `/auth/` and `/actuator/` are public — no token needed.
 * - Every other route requires a valid Bearer access token: missing
 *   header, expired token, bad signature, malformed token, or a refresh
 *   token used as an API credential all return `401` with an
 *   [ApiError] body. Nothing is forwarded downstream on failure.
 * - On success the request is mutated: any client-supplied
 *   `X-User-*` headers are stripped first (spoof protection),
 *   then `X-User-Id` / `X-User-Email` / `X-User-Role`
 *   are set from the validated claims for the downstream service.
 */
@Component
class JwtAuthFilter(private val jwtValidator: JwtValidator) : GlobalFilter, Ordered {

  companion object {
    /** Paths that do not need a token. Ant-style, matched against the path. */
    private val PUBLIC_PATHS = listOf("/auth/**", "/actuator/**")

    private val MAPPER = ObjectMapper()

    /** Minimal Ant-style matcher for the two public patterns (no extra dep). */
    @JvmStatic
    fun matches(pattern: String, path: String): Boolean {
      if (pattern.endsWith("/**")) {
        val prefix = pattern.substring(0, pattern.length - 3)
        return path == prefix || path.startsWith("$prefix/")
      }
      return pattern == path
    }

    private fun messageFor(e: JwtValidationException): String =
      when (e.reason()) {
        JwtValidator.Reason.EXPIRED -> "Token has expired"
        JwtValidator.Reason.BAD_SIGNATURE -> "Token signature is invalid"
        JwtValidator.Reason.NOT_AN_ACCESS_TOKEN -> "Refresh tokens cannot be used as API credentials"
        JwtValidator.Reason.MALFORMED -> "Token is malformed"
      }

    private fun unauthorized(exchange: ServerWebExchange, message: String): Mono<Void> {
      val response: ServerHttpResponse = exchange.response
      response.statusCode = HttpStatus.UNAUTHORIZED
      response.headers.contentType = MediaType.APPLICATION_JSON
      val traceId = exchange.request.headers.getFirst(TraceConstants.REQUEST_ID_HEADER)
      val error = ApiError("UNAUTHORIZED", message, traceId)
      val bytes: ByteArray = try {
        MAPPER.writeValueAsBytes(error)
      } catch (e: JsonProcessingException) {
        ("{\"code\":\"UNAUTHORIZED\",\"message\":\"$message\"}")
          .toByteArray(StandardCharsets.UTF_8)
      }
      return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)))
    }
  }

  override fun getOrder(): Int {
    // After TraceIdFilter (a WebFilter, runs first) and before routing and
    // before the RequestRateLimiter route filter (order 0).
    return -100
  }

  override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
    val path = exchange.request.path.value()
    if (isPublic(path)) {
      return chain.filter(exchange)
    }

    val authHeader = exchange.request.headers.getFirst(TraceConstants.AUTHORIZATION_HEADER)
    if (authHeader == null || !authHeader.startsWith(TraceConstants.BEARER_PREFIX)) {
      return unauthorized(exchange, "Missing or invalid Authorization header")
    }
    val token = authHeader.substring(TraceConstants.BEARER_PREFIX.length)
    val claims = try {
      jwtValidator.validate(token)
    } catch (e: JwtValidationException) {
      return unauthorized(exchange, messageFor(e))
    }

    val email = claims.email
    val role = claims.role
    val mutated: ServerHttpRequest = exchange.request.mutate()
      .headers { headers ->
        // Strip anything the client smuggled in, then set trusted values.
        headers.remove(TraceConstants.USER_ID_HEADER)
        headers.remove(TraceConstants.USER_EMAIL_HEADER)
        headers.remove(TraceConstants.USER_ROLE_HEADER)
        headers.set(TraceConstants.USER_ID_HEADER, claims.userId.toString())
        if (email != null) {
          headers.set(TraceConstants.USER_EMAIL_HEADER, email)
        }
        if (role != null) {
          headers.set(TraceConstants.USER_ROLE_HEADER, role)
        }
      }
      .build()
    return chain.filter(exchange.mutate().request(mutated).build())
  }

  private fun isPublic(path: String): Boolean =
    PUBLIC_PATHS.any { pattern -> matches(pattern, path) }
}
