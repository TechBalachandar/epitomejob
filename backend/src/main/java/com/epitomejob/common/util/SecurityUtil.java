package com.epitomejob.common.util;

import com.epitomejob.common.constants.AppConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** Reads the authenticated user from the security context. */
public final class SecurityUtil {

  private SecurityUtil() {}

  /**
   * The authenticated user's id — the JWT subject. Services check ownership
   * against this, not just controllers. The JWT filter always sets the
   * subject to the user's UUID, and AppUserDetailsService does the same.
   */
  public static UUID currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getName() == null) {
      throw new IllegalStateException(AppConstants.Errors.NOT_AUTHENTICATED);
    }
    return UUID.fromString(auth.getName());
  }
}
