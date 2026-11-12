package com.epitomejob.common.security;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.identity.model.User;
import com.epitomejob.identity.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads application users for Spring Security. Two jobs:
 * <ol>
 *   <li>Its mere presence stops Boot from inventing an in-memory user with a
 *       generated password (the "Using generated security password" log line
 *       from the Phase 0 starter).</li>
 *   <li>It is the authentication path if anything ever authenticates by
 *       username/password instead of by JWT.</li>
 * </ol>
 * The principal name is the user's UUID, not the email — that matches the JWT
 * subject convention {@code SecurityUtil.currentUserId()} relies on.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public AppUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = users.findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    return new org.springframework.security.core.userdetails.User(
        user.getId().toString(),
        user.getPasswordHash() == null ? "" : user.getPasswordHash(),
        List.of(new SimpleGrantedAuthority(AppConstants.Security.ROLE_PREFIX + user.getRole().name())));
  }
}
