package com.epitomejob.identity.service;

import com.epitomejob.common.util.HashUtil;
import com.epitomejob.identity.model.RefreshToken;
import com.epitomejob.identity.model.User;
import com.epitomejob.identity.repository.RefreshTokenRepository;
import com.epitomejob.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock
  private UserRepository users;

  @Mock
  private RefreshTokenRepository refreshTokens;

  @Mock
  private PasswordEncoder passwords;

  @Mock
  private JwtService jwt;

  private AuthService service() {
    return new AuthService(users, refreshTokens, passwords, jwt);
  }

  private User user() {
    User user = new User("a@b.com", "hashed", "A B");
    return user;
  }

  private void stubTokenIssue(User user) {
    when(jwt.generateAccess(user)).thenReturn("access-token");
    when(jwt.generateRefresh(user)).thenReturn("refresh-token");
    when(jwt.expirationMs()).thenReturn(900_000L);
    when(jwt.refreshExpirationMs()).thenReturn(604_800_000L);
    when(refreshTokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  // ---- register ----

  @Test
  void registerSavesEncodedPassword() {
    when(users.existsByEmail("a@b.com")).thenReturn(false);
    when(passwords.encode("Passw0rd!")).thenReturn("hashed");
    when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

    User saved = service().register("a@b.com", "A B", "Passw0rd!");

    assertEquals("hashed", saved.getPasswordHash());
    verify(users).save(any(User.class));
  }

  @Test
  void registerDuplicateEmailConflicts() {
    when(users.existsByEmail("a@b.com")).thenReturn(true);
    var ex = assertThrows(ResponseStatusException.class,
        () -> service().register("a@b.com", "A B", "Passw0rd!"));
    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    verify(users, never()).save(any());
  }

  @Test
  void registerShortPasswordIsRejected() {
    when(users.existsByEmail("a@b.com")).thenReturn(false);
    var ex = assertThrows(ResponseStatusException.class,
        () -> service().register("a@b.com", "A B", "short"));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void registerNullPasswordIsRejected() {
    when(users.existsByEmail("a@b.com")).thenReturn(false);
    assertThrows(ResponseStatusException.class,
        () -> service().register("a@b.com", "A B", null));
  }

  // ---- login ----

  @Test
  void loginIssuesTokenPair() {
    User user = user();
    when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
    when(passwords.matches("Passw0rd!", "hashed")).thenReturn(true);
    stubTokenIssue(user);

    var result = service().login("a@b.com", "Passw0rd!");

    assertEquals("access-token", result.accessToken());
    assertEquals("refresh-token", result.refreshToken());
    assertEquals(900_000L, result.expiresInMs());
    verify(refreshTokens).save(any(RefreshToken.class));
  }

  @Test
  void loginWrongPasswordIsUnauthorized() {
    User user = user();
    when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
    when(passwords.matches("wrong", "hashed")).thenReturn(false);
    var ex = assertThrows(ResponseStatusException.class,
        () -> service().login("a@b.com", "wrong"));
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    assertEquals("Invalid credentials", ex.getReason());
  }

  @Test
  void loginUnknownEmailIsUnauthorizedWithSameMessage() {
    when(users.findByEmail("ghost@b.com")).thenReturn(Optional.empty());
    var ex = assertThrows(ResponseStatusException.class,
        () -> service().login("ghost@b.com", "whatever"));
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    assertEquals("Invalid credentials", ex.getReason());
  }

  // ---- refresh ----

  @Test
  void refreshRotatesOldToken() {
    User user = user();
    RefreshToken stored = new RefreshToken(USER_ID, HashUtil.sha256("refresh-token"),
        Instant.now().plusSeconds(3600));
    when(refreshTokens.findByTokenHash(HashUtil.sha256("refresh-token")))
        .thenReturn(Optional.of(stored));
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    stubTokenIssue(user);

    var result = service().refresh("refresh-token");

    assertTrue(stored.isRevoked(), "old token must die on rotation");
    assertEquals("access-token", result.accessToken());
  }

  @Test
  void refreshWithRevokedTokenRevokesAllUserTokens() {
    RefreshToken revoked = new RefreshToken(USER_ID, HashUtil.sha256("old"),
        Instant.now().plusSeconds(3600));
    revoked.setRevoked(true);
    RefreshToken live = new RefreshToken(USER_ID, HashUtil.sha256("live"),
        Instant.now().plusSeconds(3600));
    when(refreshTokens.findByTokenHash(HashUtil.sha256("old"))).thenReturn(Optional.of(revoked));
    when(jwt.extractUserId("old")).thenReturn(USER_ID);
    when(refreshTokens.findByUserIdAndRevokedFalse(USER_ID)).thenReturn(List.of(live));

    var ex = assertThrows(ResponseStatusException.class, () -> service().refresh("old"));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    assertTrue(live.isRevoked(), "reuse of a revoked token must revoke every live token");
  }

  @Test
  void refreshWithExpiredStoredTokenIsUnauthorized() {
    RefreshToken expired = new RefreshToken(USER_ID, HashUtil.sha256("stale"),
        Instant.now().minusSeconds(60));
    when(refreshTokens.findByTokenHash(HashUtil.sha256("stale"))).thenReturn(Optional.of(expired));
    when(jwt.extractUserId("stale")).thenThrow(new RuntimeException("unparseable"));

    var ex = assertThrows(ResponseStatusException.class, () -> service().refresh("stale"));

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
  }

  @Test
  void refreshWithUnknownUserIsUnauthorized() {
    RefreshToken stored = new RefreshToken(USER_ID, HashUtil.sha256("refresh-token"),
        Instant.now().plusSeconds(3600));
    when(refreshTokens.findByTokenHash(HashUtil.sha256("refresh-token")))
        .thenReturn(Optional.of(stored));
    when(users.findById(USER_ID)).thenReturn(Optional.empty());

    assertThrows(ResponseStatusException.class, () -> service().refresh("refresh-token"));
  }

  // ---- logout ----

  @Test
  void logoutRevokesToken() {
    RefreshToken stored = new RefreshToken(USER_ID, HashUtil.sha256("refresh-token"),
        Instant.now().plusSeconds(3600));
    when(refreshTokens.findByTokenHash(HashUtil.sha256("refresh-token")))
        .thenReturn(Optional.of(stored));

    service().logout("refresh-token");

    assertTrue(stored.isRevoked());
  }

  @Test
  void logoutUnknownTokenIsNoop() {
    when(refreshTokens.findByTokenHash(HashUtil.sha256("nope"))).thenReturn(Optional.empty());
    service().logout("nope"); // must not throw
  }
}
