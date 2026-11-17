package com.epitomejob.common.util

import com.epitomejob.common.constants.AppConstants
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * Reads the authenticated user from the security context.
 *
 * `object` with `@JvmStatic` so the Java callers (approval, billing, coach,
 * interview, matching, notification, tracker services) keep calling
 * `SecurityUtil.currentUserId()` unchanged.
 */
object SecurityUtil {

  /**
   * The authenticated user's id — the JWT subject. Services check ownership
   * against this, not just controllers. The JWT filter always sets the
   * subject to the user's UUID, and AppUserDetailsService does the same.
   */
  @JvmStatic
  fun currentUserId(): UUID {
    val auth = SecurityContextHolder.getContext().authentication
    if (auth == null || auth.name == null) {
      throw IllegalStateException(AppConstants.Errors.NOT_AUTHENTICATED)
    }
    return UUID.fromString(auth.name)
  }
}
