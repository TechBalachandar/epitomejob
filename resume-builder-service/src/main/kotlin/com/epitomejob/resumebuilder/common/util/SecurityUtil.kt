package com.epitomejob.resumebuilder.common.util

import com.epitomejob.resumebuilder.common.constants.AppConstants
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * Reads the authenticated user from the security context. The JWT filter
 * always sets the subject to the user's UUID, so services check ownership
 * against this, not just controllers. (Same pattern as the monolith's
 * SecurityUtil.)
 */
object SecurityUtil {

  @JvmStatic
  fun currentUserId(): UUID {
    val auth = SecurityContextHolder.getContext().authentication
    if (auth == null || auth.name == null) {
      throw IllegalStateException(AppConstants.Errors.NOT_AUTHENTICATED)
    }
    return UUID.fromString(auth.name)
  }
}
