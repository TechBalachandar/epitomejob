package com.epitomejob.identity.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.identity.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Signed JWTs (HS256; Key Vault secret in production).
 * LESSON from SampAgent Slice 1: a signed token must be parsed with
 * parseSignedClaims, never parseEncryptedClaims — that bug broke every
 * authenticated call. This class is the only place tokens are parsed.
 */
@Service
public class JwtService {

  private final SecretKey key;
  private final long expirationMs;
  private final long refreshExpirationMs;

  public JwtService(@Value("${app.jwt.secret}") String secret,
                    @Value("${app.jwt.expiration-ms}") long expirationMs,
                    @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
    if (secret == null || secret.length() < AppConstants.Security.MIN_JWT_SECRET_LENGTH) {
      throw new IllegalStateException("app.jwt.secret must be at least 32 characters — set APP_JWT_SECRET");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMs = expirationMs;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  public String generateAccess(User user) {
    return Jwts.builder().subject(user.getId().toString())
        .claim(AppConstants.Security.CLAIM_EMAIL, user.getEmail())
        .claim(AppConstants.Security.CLAIM_ROLE, AppConstants.Security.ROLE_PREFIX + user.getRole().name())
        .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + expirationMs))
        .signWith(key).compact();
  }

  public String generateRefresh(User user) {
    return Jwts.builder().subject(user.getId().toString())
        .claim(AppConstants.Security.CLAIM_TYPE, AppConstants.Security.TOKEN_TYPE_REFRESH)
        .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
        .signWith(key).compact();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  public UUID extractUserId(String token) {
    return UUID.fromString(parse(token).getSubject());
  }

  /** Access-token lifetime, as configured — the value login reports to clients. */
  public long expirationMs() {
    return expirationMs;
  }

  public long refreshExpirationMs() {
    return refreshExpirationMs;
  }
}
