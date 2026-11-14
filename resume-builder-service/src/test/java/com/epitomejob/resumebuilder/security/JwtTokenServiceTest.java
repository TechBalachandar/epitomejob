package com.epitomejob.resumebuilder.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

  private static final String SECRET = "test-only-secret-change-me-0123456789abcdef0123456789abcdef";
  private final JwtTokenService service = new JwtTokenService(SECRET);

  private static SecretKey key(String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  private String mint(UUID userId, String type, Date expiry, String secret) {
    return Jwts.builder()
        .subject(userId.toString())
        .claim("type", type)
        .issuedAt(new Date())
        .expiration(expiry)
        .signWith(key(secret))
        .compact();
  }

  @Test
  void validTokenReturnsSubject() {
    UUID userId = UUID.randomUUID();
    String token = mint(userId, "access",
        Date.from(Instant.now().plusSeconds(900)), SECRET);
    assertThat(service.userIdFromToken(token)).isEqualTo(userId);
  }

  @Test
  void tokenWithoutTypeClaimIsAccepted() {
    UUID userId = UUID.randomUUID();
    String token = Jwts.builder().subject(userId.toString())
        .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(900)))
        .signWith(key(SECRET)).compact();
    assertThat(service.userIdFromToken(token)).isEqualTo(userId);
  }

  @Test
  void refreshTokenIsRejected() {
    String token = mint(UUID.randomUUID(), "refresh",
        Date.from(Instant.now().plusSeconds(900)), SECRET);
    assertThatThrownBy(() -> service.userIdFromToken(token))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void expiredTokenIsRejected() {
    String token = mint(UUID.randomUUID(), "access",
        Date.from(Instant.now().minusSeconds(60)), SECRET);
    assertThatThrownBy(() -> service.userIdFromToken(token))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tokenSignedWithAnotherSecretIsRejected() {
    String token = mint(UUID.randomUUID(), "access",
        Date.from(Instant.now().plusSeconds(900)),
        "a-completely-different-secret-0123456789abcdef0123456789");
    assertThatThrownBy(() -> service.userIdFromToken(token))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void garbageIsRejected() {
    assertThatThrownBy(() -> service.userIdFromToken("not.a.token"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
