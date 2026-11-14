package com.epitomejob.resumebuilder.security;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Validates the access JWTs the gateway already checked. The subject is the
 * user's UUID; refresh tokens (claim {@code type=refresh}) are rejected as
 * API credentials — same rule as the gateway's JwtAuthFilter.
 *
 * <p>KEY DERIVATION — must match the monolith/gateway: the raw UTF-8 bytes
 * of {@code app.jwt.secret} (NOT Base64-decoded). The monolith's JwtService
 * was fixed (Nov 5) to use {@code parseSignedClaims} on a signed token;
 * this uses the same call. Mac gate: a gateway-minted token must validate
 * here; if the gateway derives its key differently, this is the one line
 * that changes.
 */
@Service
public class JwtTokenService {

  private final SecretKey signingKey;

  public JwtTokenService(@Value("${app.jwt.secret}") String secret) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /** Returns the user id (JWT subject) or throws IllegalArgumentException. */
  public UUID userIdFromToken(String token) {
    Claims claims = parse(token);
    if (AppConstants.Security.TOKEN_TYPE_REFRESH.equals(claims.get(AppConstants.Security.CLAIM_TYPE))) {
      throw new IllegalArgumentException(AppConstants.Errors.REFRESH_TOKEN_NOT_ALLOWED);
    }
    try {
      return UUID.fromString(claims.getSubject());
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new IllegalArgumentException(AppConstants.Errors.INVALID_OR_EXPIRED_TOKEN, ex);
    }
  }

  private Claims parse(String token) {
    try {
      return Jwts.parser()
          .verifyWith(signingKey)
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (JwtException | IllegalArgumentException ex) {
      // Expired, badly signed, malformed — all one 401, no detail leaked.
      throw new IllegalArgumentException(AppConstants.Errors.INVALID_OR_EXPIRED_TOKEN, ex);
    }
  }
}
