package com.epitomejob.common;

import com.epitomejob.common.JwtValidator.JwtClaims;
import com.epitomejob.common.JwtValidator.JwtValidationException;
import com.epitomejob.common.JwtValidator.Reason;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JwtValidator}. Every token here is minted with real
 * JJWT (no mocks of the validator itself), so the tests prove the same
 * parse path the gateway uses at runtime.
 */
class JwtValidatorTest {

  // >= 32 chars, as required. Never the real dev secret — tests mint their own.
  private static final String SECRET = "test-secret-that-is-long-enough-0123456789";
  private static final String WRONG_SECRET = "a-different-test-secret-long-enough-0123456789";

  private final JwtValidator validator = new JwtValidator(SECRET);

  private String mint(String secret, String subject, String email, String role,
                      String type, long ttlMs) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    var builder = Jwts.builder()
        .subject(subject)
        .claim(JwtValidator.CLAIM_EMAIL, email)
        .claim(JwtValidator.CLAIM_ROLE, role)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + ttlMs))
        .signWith(key);
    if (type != null) {
      builder.claim(JwtValidator.CLAIM_TYPE, type);
    }
    return builder.compact();
  }

  @Test
  void validAccessTokenReturnsClaims() {
    UUID userId = UUID.randomUUID();
    String token = mint(SECRET, userId.toString(), "a@example.com", "ROLE_USER", null, 900_000);

    JwtClaims claims = validator.validate(token);

    assertEquals(userId, claims.userId());
    assertEquals("a@example.com", claims.email());
    assertEquals("ROLE_USER", claims.role());
  }

  @Test
  void expiredTokenIsRejectedAsExpired() {
    String token = mint(SECRET, UUID.randomUUID().toString(), "a@example.com",
        "ROLE_USER", null, -1_000);

    JwtValidationException ex = assertThrows(JwtValidationException.class,
        () -> validator.validate(token));
    assertEquals(Reason.EXPIRED, ex.reason());
  }

  @Test
  void tokenSignedWithAnotherSecretIsRejectedAsBadSignature() {
    String token = mint(WRONG_SECRET, UUID.randomUUID().toString(), "a@example.com",
        "ROLE_USER", null, 900_000);

    JwtValidationException ex = assertThrows(JwtValidationException.class,
        () -> validator.validate(token));
    assertEquals(Reason.BAD_SIGNATURE, ex.reason());
  }

  @Test
  void refreshTokenIsRejectedAsNotAnAccessToken() {
    String token = mint(SECRET, UUID.randomUUID().toString(), "a@example.com",
        "ROLE_USER", JwtValidator.TOKEN_TYPE_REFRESH, 900_000);

    JwtValidationException ex = assertThrows(JwtValidationException.class,
        () -> validator.validate(token));
    assertEquals(Reason.NOT_AN_ACCESS_TOKEN, ex.reason());
  }

  @Test
  void garbageTokenIsRejectedAsMalformed() {
    JwtValidationException ex = assertThrows(JwtValidationException.class,
        () -> validator.validate("not-a-jwt"));
    assertEquals(Reason.MALFORMED, ex.reason());
  }

  @Test
  void nullTokenIsRejectedAsMalformed() {
    // No stub involved: validate(null) must fail closed, not throw NPE.
    JwtValidationException ex = assertThrows(JwtValidationException.class,
        () -> validator.validate(null));
    assertEquals(Reason.MALFORMED, ex.reason());
  }

  @Test
  void shortSecretFailsFastAtConstruction() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> new JwtValidator("too-short"));
    assertTrue(ex.getMessage().contains("APP_JWT_SECRET"));
  }
}
