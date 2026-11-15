package com.epitomejob.resumebuilder.security

import com.epitomejob.resumebuilder.common.constants.AppConstants
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Stateless JWT filter. The gateway already validated the token, but this
 * service never trusts the network: every request re-validates the Bearer
 * token and puts the user UUID in the SecurityContext, so services can
 * re-check ownership on every document. Missing/invalid token → 401 JSON.
 * /actuator/health is public (healthchecks) and skips the filter.
 */
@Component
class JwtAuthFilter(private val tokens: JwtTokenService) : OncePerRequestFilter() {

  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    // NOTE: getServletPath() is "" for Spring Boot's DispatcherServlet
    // (mapped to "/"), so the health path must come from the request URI.
    var path = request.requestURI
    val contextPath = request.contextPath
    if (contextPath != null && contextPath.isNotEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length)
    }
    return path.startsWith("/actuator/health")
  }

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    chain: FilterChain
  ) {
    val authorization = request.getHeader(AppConstants.Http.AUTHORIZATION_HEADER)
    if (authorization == null || !authorization.startsWith(AppConstants.Http.BEARER_PREFIX)) {
      unauthorized(response)
      return
    }
    val token = authorization.substring(AppConstants.Http.BEARER_PREFIX.length)
    val userId: UUID
    try {
      userId = tokens.userIdFromToken(token)
    } catch (ex: IllegalArgumentException) {
      unauthorized(response)
      return
    }
    val auth = UsernamePasswordAuthenticationToken(
        userId.toString(), null, emptyList())
    SecurityContextHolder.getContext().authentication = auth
    chain.doFilter(request, response)
  }

  private fun unauthorized(response: HttpServletResponse) {
    response.status = HttpServletResponse.SC_UNAUTHORIZED
    response.contentType = "application/json"
    response.writer.write(
        "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + AppConstants.Errors.INVALID_OR_EXPIRED_TOKEN + "\"}")
  }
}
