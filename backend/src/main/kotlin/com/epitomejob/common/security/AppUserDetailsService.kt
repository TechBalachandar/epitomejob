package com.epitomejob.common.security

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.identity.model.User
import com.epitomejob.identity.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/**
 * Loads application users for Spring Security. Two jobs:
 * 1. Its mere presence stops Boot from inventing an in-memory user with a
 *    generated password (the "Using generated security password" log line
 *    from the Phase 0 starter).
 * 2. It is the authentication path if anything ever authenticates by
 *    username/password instead of by JWT.
 *
 * The principal name is the user's UUID, not the email — that matches the JWT
 * subject convention `SecurityUtil.currentUserId()` relies on.
 */
@Service
class AppUserDetailsService(private val users: UserRepository) : UserDetailsService {

  override fun loadUserByUsername(email: String): UserDetails {
    val user: User = users.findByEmail(email)
      .orElseThrow { UsernameNotFoundException("User not found: $email") }
    return org.springframework.security.core.userdetails.User(
      user.id.toString(),
      user.passwordHash ?: "",
      listOf(SimpleGrantedAuthority(AppConstants.Security.ROLE_PREFIX + user.role.name))
    )
  }
}
