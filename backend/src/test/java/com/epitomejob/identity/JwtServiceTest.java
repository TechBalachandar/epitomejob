package com.epitomejob.identity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JwtServiceTest {
  private JwtService service() {
    return new JwtService("test-secret-that-is-at-least-32-chars-long!!", 900_000L, 604_800_000L);
  }
  @Test void signedTokenParsesBack() {
    var user = new User("a@b.com", "hash", "A B");
    String token = service().generateAccess(user);
    assertEquals(user.getId(), service().extractUserId(token));
  }
  @Test void tamperedTokenFails() {
    var user = new User("a@b.com", "hash", "A B");
    String token = service().generateAccess(user);
    assertThrows(Exception.class, () -> service().parse(token + "x"));
  }
}
