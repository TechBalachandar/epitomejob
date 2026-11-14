package com.epitomejob.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Validates the access JWTs the monolith issues. Mirrors the monolith's
 * {@code JwtService} (HS256, {@code parseSignedClaims} — never
 * {@code parseEncryptedClaims}; that was the SampAgent Slice 1 bug that broke
 * every authenticated call) so the gateway accepts exactly the tokens the
 * monolith mints and rejects everything else.
 *
 * <p>Construction fails fast when the secret is shorter than 32 characters,
 * same rule as the monolith ({@code AppConstants.Security.MIN_JWT_SECRET_LENGTH}).
 * The secret comes from the {@code APP_JWT_SECRET} env var; the dev default
 * is the same 42-char value the monolith's compose file uses and must be
 * replaced in any shared deployment.
 */
public class JwtValidator {

  /** Why a token was rejected. The gateway maps every reason to HTTP 401. */
  public enum Reason {
    EXPIRED,
    BAD_SIGNATURE,
    MALFORMED,
    NOT_AN_ACCESS_TOKEN
  }

  public static class JwtValidationException extends RuntimeException {
    private final Reason reason;

    public JwtValidationException(Reason reason, String message) {
      super(message);
      this.reason = reason;
    }

    public JwtValidationException(Reason reason, String message, Throwable cause) {
      super(message, cause);
      this.reason = reason;
    }

    public Reason reason() {
      return reason;
    }
  }

  /** The claims the gateway needs from a valid access token. */
  public record JwtClaims(UUID userId, String email, String role) {
  }

  /** Claim names, matching the monolith's {@code AppConstants.Security}. */
  public static final String CLAIM_EMAIL = "email";
  public static final String CLAIM_ROLE = "role";
  public static final String CLAIM_TYPE = "type";
  public static final String TOKEN_TYPE_REFRESH = "refresh";

  public static final int MIN_JWT_SECRET_LENGTH = 32;

  private final SecretKey key;

  public JwtValidator(String secret) {
    if (secret == null || secret.length() < MIN_JWT_SECRET_LENGTH) {
      throw new IllegalStateException(
          "JWT secret must be at least 32 characters — set APP_JWT_SECRET");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Parses and verifies a token. Returns the claims on success.
   *
   * @throws JwtValidationException with {@link Reason#EXPIRED} for an expired
   *         token, {@link Reason#BAD_SIGNATURE} for a bad signature,
   *         {@link Reason#MALFORMED} for anything else unparseable, and
   *         {@link Reason#NOT_AN_ACCESS_TOKEN} for a refresh token (refresh
   *         tokens are only valid at {@code POST /auth/refresh}, never as
   *         API credentials).
   */
  public JwtClaims validate(String token) {
    final Claims claims;
    try {
      claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    } catch (ExpiredJwtException e) {
      throw new JwtValidationException(Reason.EXPIRED, "Token has expired", e);
    } catch (SignatureException e) {
      throw new JwtValidationException(Reason.BAD_SIGNATURE, "Token signature is invalid", e);
    } catch (JwtException | IllegalArgumentException e) {
      throw new JwtValidationException(Reason.MALFORMED, "Token is malformed", e);
    }
    if (TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
      throw new JwtValidationException(Reason.NOT_AN_ACCESS_TOKEN,
          "Refresh tokens cannot be used as API credentials");
    }
    final UUID userId;
    try {
      userId = UUID.fromString(claims.getSubject());
    } catch (IllegalArgumentException e) {
      throw new JwtValidationException(Reason.MALFORMED, "Token subject is not a user id", e);
    }
    return new JwtClaims(userId, claims.get(CLAIM_EMAIL, String.class),
        claims.get(CLAIM_ROLE, String.class));
  }
}
