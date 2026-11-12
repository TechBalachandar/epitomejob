package com.epitomejob.identity.service;

import com.epitomejob.identity.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

  private static final String SECRET = "test-secret-that-is-at-least-32-chars-long!!";

  private JwtService service() {
    return new JwtService(SECRET, 900_000L, 604_800_000L);
  }

  @Test
  void signedTokenParsesBack() {
    var user = new User("a@b.com", "hash", "A B");
    String token = service().generateAccess(user);
    assertEquals(user.getId(), service().extractUserId(token));
  }

  @Test
  void tamperedTokenFails() {
    var user = new User("a@b.com", "hash", "A B");
    String token = service().generateAccess(user);
    assertThrows(JwtException.class, () -> service().parse(token + "x"));
  }

  @Test
  void tokenSignedWithAnotherSecretFails() {
    var user = new User("a@b.com", "hash", "A B");
    String token = service().generateAccess(user);
    var otherKey = new JwtService("a-different-secret-that-is-also-32-chars!!", 900_000L, 604_800_000L);
    assertThrows(JwtException.class, () -> otherKey.parse(token));
  }

  @Test
  void expiredTokenFails() {
    var expired = new JwtService(SECRET, -1_000L, 604_800_000L);
    var user = new User("a@b.com", "hash", "A B");
    String token = expired.generateAccess(user);
    assertThrows(ExpiredJwtException.class, () -> expired.parse(token));
  }

  @Test
  void refreshTokenCarriesRefreshTypeClaim() {
    var user = new User("a@b.com", "hash", "A B");
    String token = service().generateRefresh(user);
    assertEquals("refresh", service().parse(token).get("type", String.class));
    assertEquals(user.getId(), service().extractUserId(token));
  }

  @Test
  void shortSecretIsRejectedAtStartup() {
    assertThrows(IllegalStateException.class,
        () -> new JwtService("too-short", 900_000L, 604_800_000L));
  }

  @Test
  void expirationComesFromConfiguration() {
    assertEquals(900_000L, service().expirationMs());
    assertEquals(604_800_000L, service().refreshExpirationMs());
  }

  @Test
  void accessTokenCarriesEmailAndRoleClaims() {
    var user = new User("a@b.com", "hash", "A B");
    String token = service().generateAccess(user);
    var claims = service().parse(token);
    assertEquals("a@b.com", claims.get("email", String.class));
    assertTrue(claims.get("role", String.class).startsWith("ROLE_"));
  }
}
