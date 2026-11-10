package com.epitomejob.common;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;
public final class SecurityUtil {
  private SecurityUtil() {}
  /** The authenticated user's id — the JWT subject. Services check ownership against this, not just controllers. */
  public static UUID currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getName() == null) throw new IllegalStateException("Not authenticated");
    return UUID.fromString(auth.getName());
  }
}
