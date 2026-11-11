package com.epitomejob.identity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
@Service
public class AuthService {
  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwords;
  private final JwtService jwt;
  public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                     PasswordEncoder passwords, JwtService jwt) {
    this.users = users; this.refreshTokens = refreshTokens; this.passwords = passwords; this.jwt = jwt;
  }
  public record AuthResult(String accessToken, String refreshToken, long expiresInMs, User user) {}
  @Transactional
  public User register(String email, String fullName, String password) {
    if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    if (password == null || password.length() < 8)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
    return users.save(new User(email, passwords.encode(password), fullName));
  }
  @Transactional
  public AuthResult login(String email, String password) {
    // Generic message on purpose: never reveal whether the email or the password was wrong.
    User user = users.findByEmail(email)
        .filter(u -> u.getPasswordHash() != null && passwords.matches(password, u.getPasswordHash()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    user.setLastLoginAt(Instant.now());
    return issueTokens(user);
  }
  @Transactional
  public AuthResult refresh(String refreshToken) {
    String hash = sha256(refreshToken);
    RefreshToken stored = refreshTokens.findByTokenHash(hash)
        .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
        .orElseThrow(() -> {
          // Reuse of a revoked/expired token: possible theft — revoke everything for that user.
          try {
            UUID userId = jwt.extractUserId(refreshToken);
            refreshTokens.findByUserIdAndRevokedFalse(userId).forEach(t -> t.setRevoked(true));
          } catch (Exception ignored) {}
          return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        });
    stored.setRevoked(true); // rotation: old token dies, new pair is issued
    User user = users.findById(stored.getUserId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
    return issueTokens(user);
  }
  @Transactional
  public void logout(String refreshToken) {
    refreshTokens.findByTokenHash(sha256(refreshToken)).ifPresent(t -> t.setRevoked(true));
  }
  private AuthResult issueTokens(User user) {
    String refresh = jwt.generateRefresh(user);
    refreshTokens.save(new RefreshToken(user.getId(), sha256(refresh),
        Instant.now().plusMillis(jwt.refreshExpirationMs())));
    return new AuthResult(jwt.generateAccess(user), refresh, 900_000L, user);
  }
  public static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
}
