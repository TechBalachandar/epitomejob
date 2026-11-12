package com.epitomejob.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void currentUserIdReturnsSubjectUuid() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
    assertEquals(USER_ID, SecurityUtil.currentUserId());
  }

  @Test
  void currentUserIdThrowsWhenUnauthenticated() {
    assertThrows(IllegalStateException.class, SecurityUtil::currentUserId);
  }

  @Test
  void currentUserIdThrowsWhenPrincipalIsNotAUuid() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("not-a-uuid", null, List.of()));
    assertThrows(IllegalArgumentException.class, SecurityUtil::currentUserId);
  }
}
