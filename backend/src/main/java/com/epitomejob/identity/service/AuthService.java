package com.epitomejob.identity.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.util.HashUtil;
import com.epitomejob.identity.model.RefreshToken;
import com.epitomejob.identity.model.User;
import com.epitomejob.identity.repository.RefreshTokenRepository;
import com.epitomejob.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/** Registration, login, refresh-token rotation, logout. */
@Service
public class AuthService {

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwords;
  private final JwtService jwt;

  public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                     PasswordEncoder passwords, JwtService jwt) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwords = passwords;
    this.jwt = jwt;
  }

  public record AuthResult(String accessToken, String refreshToken, long expiresInMs, User user) {}

  @Transactional
  public User register(String email, String fullName, String password) {
    if (users.existsByEmail(email)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, AppConstants.Errors.EMAIL_ALREADY_REGISTERED);
    }
    if (password == null || password.length() < AppConstants.User.MIN_PASSWORD_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.PASSWORD_TOO_SHORT);
    }
    return users.save(new User(email, passwords.encode(password), fullName));
  }

  @Transactional
  public AuthResult login(String email, String password) {
    // Generic message on purpose: never reveal whether the email or the password was wrong.
    User user = users.findByEmail(email)
        .filter(u -> u.getPasswordHash() != null && passwords.matches(password, u.getPasswordHash()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            AppConstants.Errors.INVALID_CREDENTIALS));
    user.setLastLoginAt(Instant.now());
    return issueTokens(user);
  }

  @Transactional
  public AuthResult refresh(String refreshToken) {
    String hash = HashUtil.sha256(refreshToken);
    RefreshToken stored = refreshTokens.findByTokenHash(hash)
        .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
        .orElseThrow(() -> {
          // Reuse of a revoked/expired token: possible theft — revoke everything for that user.
          try {
            UUID userId = jwt.extractUserId(refreshToken);
            refreshTokens.findByUserIdAndRevokedFalse(userId).forEach(t -> t.setRevoked(true));
          } catch (Exception ignored) {
            // Unparseable token: nothing to revoke, still unauthorized.
          }
          return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
              AppConstants.Errors.INVALID_REFRESH_TOKEN);
        });
    stored.setRevoked(true); // rotation: old token dies, new pair is issued
    User user = users.findById(stored.getUserId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            AppConstants.Errors.INVALID_REFRESH_TOKEN));
    return issueTokens(user);
  }

  @Transactional
  public void logout(String refreshToken) {
    refreshTokens.findByTokenHash(HashUtil.sha256(refreshToken)).ifPresent(t -> t.setRevoked(true));
  }

  private AuthResult issueTokens(User user) {
    String refresh = jwt.generateRefresh(user);
    refreshTokens.save(new RefreshToken(user.getId(), HashUtil.sha256(refresh),
        Instant.now().plusMillis(jwt.refreshExpirationMs())));
    return new AuthResult(jwt.generateAccess(user), refresh, jwt.expirationMs(), user);
  }
}
