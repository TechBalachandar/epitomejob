package com.epitomejob.identity.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.util.HashUtil
import com.epitomejob.identity.model.RefreshToken
import com.epitomejob.identity.model.User
import com.epitomejob.identity.repository.RefreshTokenRepository
import com.epitomejob.identity.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/** Registration, login, refresh-token rotation, logout. */
@Service
class AuthService(
  private val users: UserRepository,
  private val refreshTokens: RefreshTokenRepository,
  private val passwords: PasswordEncoder,
  private val jwt: JwtService
) {

  data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInMs: Long,
    val user: User
  )

  @Transactional
  fun register(email: String, fullName: String, password: String?): User {
    if (users.existsByEmail(email)) {
      throw ResponseStatusException(HttpStatus.CONFLICT, AppConstants.Errors.EMAIL_ALREADY_REGISTERED)
    }
    if (password == null || password.length < AppConstants.User.MIN_PASSWORD_LENGTH) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.PASSWORD_TOO_SHORT)
    }
    return users.save(User(email, passwords.encode(password), fullName))
  }

  @Transactional
  fun login(email: String, password: String): AuthResult {
    // Generic message on purpose: never reveal whether the email or the password was wrong.
    val user = users.findByEmail(email)
      .filter { u ->
        val hash = u.passwordHash
        hash != null && passwords.matches(password, hash)
      }
      .orElseThrow {
        ResponseStatusException(HttpStatus.UNAUTHORIZED, AppConstants.Errors.INVALID_CREDENTIALS)
      }
    user.lastLoginAt = Instant.now()
    return issueTokens(user)
  }

  @Transactional
  fun refresh(refreshToken: String): AuthResult {
    val hash = HashUtil.sha256(refreshToken)
    val stored = refreshTokens.findByTokenHash(hash)
      .filter { !it.revoked && it.expiresAt.isAfter(Instant.now()) }
      .orElseThrow {
        // Reuse of a revoked/expired token: possible theft — revoke everything for that user.
        try {
          val userId = jwt.extractUserId(refreshToken)
          refreshTokens.findByUserIdAndRevokedFalse(userId).forEach { it.revoked = true }
        } catch (_: Exception) {
          // Unparseable token: nothing to revoke, still unauthorized.
        }
        ResponseStatusException(HttpStatus.UNAUTHORIZED, AppConstants.Errors.INVALID_REFRESH_TOKEN)
      }
    stored.revoked = true // rotation: old token dies, new pair is issued
    val user = users.findById(stored.userId)
      .orElseThrow {
        ResponseStatusException(HttpStatus.UNAUTHORIZED, AppConstants.Errors.INVALID_REFRESH_TOKEN)
      }
    return issueTokens(user)
  }

  @Transactional
  fun logout(refreshToken: String) {
    refreshTokens.findByTokenHash(HashUtil.sha256(refreshToken)).ifPresent { it.revoked = true }
  }

  private fun issueTokens(user: User): AuthResult {
    val refresh = jwt.generateRefresh(user)
    refreshTokens.save(
      RefreshToken(
        user.id,
        HashUtil.sha256(refresh),
        Instant.now().plusMillis(jwt.refreshExpirationMs())
      )
    )
    return AuthResult(jwt.generateAccess(user), refresh, jwt.expirationMs(), user)
  }
}
