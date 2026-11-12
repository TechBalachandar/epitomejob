package com.epitomejob.common.security;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.identity.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Parses the Bearer JWT (if present) and sets the authentication. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwt;

  public JwtAuthFilter(JwtService jwt) {
    this.jwt = jwt;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader(AppConstants.Http.AUTHORIZATION_HEADER);
    if (header != null && header.startsWith(AppConstants.Http.BEARER_PREFIX)) {
      try {
        var claims = jwt.parse(header.substring(AppConstants.Http.BEARER_PREFIX.length()));
        String role = claims.get(AppConstants.Security.CLAIM_ROLE, String.class);
        var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null,
            role == null ? List.of() : List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception ignored) {
        // Invalid token: leave the request unauthenticated; SecurityConfig returns 401.
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(req, res);
  }
}
