package com.epitomejob.common.security

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.identity.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Parses the Bearer token (if present) and sets the authentication. */
@Component
class JwtAuthFilter(private val jwt: JwtService) : OncePerRequestFilter() {

  override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
    val header = req.getHeader(AppConstants.Http.AUTHORIZATION_HEADER)
    if (header != null && header.startsWith(AppConstants.Http.BEARER_PREFIX)) {
      try {
        val claims = jwt.parse(header.substring(AppConstants.Http.BEARER_PREFIX.length))
        val role = claims.get(AppConstants.Security.CLAIM_ROLE, String::class.java)
        val auth = UsernamePasswordAuthenticationToken(
          claims.subject, null,
          if (role == null) emptyList() else listOf(SimpleGrantedAuthority(role))
        )
        SecurityContextHolder.getContext().authentication = auth
      } catch (e: Exception) {
        // Invalid token: leave the request unauthenticated; SecurityConfig returns 401.
        SecurityContextHolder.clearContext()
      }
    }
    chain.doFilter(req, res)
  }
}
