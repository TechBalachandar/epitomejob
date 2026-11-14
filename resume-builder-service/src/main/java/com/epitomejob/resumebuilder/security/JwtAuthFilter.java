package com.epitomejob.resumebuilder.security;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Stateless JWT filter. The gateway already validated the token, but this
 * service never trusts the network: every request re-validates the Bearer
 * token and puts the user UUID in the SecurityContext, so services can
 * re-check ownership on every document. Missing/invalid token → 401 JSON.
 * /actuator/health is public (healthchecks) and skips the filter.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtTokenService tokens;

  public JwtAuthFilter(JwtTokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // NOTE: getServletPath() is "" for Spring Boot's DispatcherServlet
    // (mapped to "/"), so the health path must come from the request URI.
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    return path.startsWith("/actuator/health");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String authorization = request.getHeader(AppConstants.Http.AUTHORIZATION_HEADER);
    if (authorization == null || !authorization.startsWith(AppConstants.Http.BEARER_PREFIX)) {
      unauthorized(response);
      return;
    }
    String token = authorization.substring(AppConstants.Http.BEARER_PREFIX.length());
    UUID userId;
    try {
      userId = tokens.userIdFromToken(token);
    } catch (IllegalArgumentException ex) {
      unauthorized(response);
      return;
    }
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        userId.toString(), null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    chain.doFilter(request, response);
  }

  private static void unauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.getWriter().write(
        "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + AppConstants.Errors.INVALID_OR_EXPIRED_TOKEN + "\"}");
  }
}
