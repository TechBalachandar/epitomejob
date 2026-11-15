package com.epitomejob.common.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class SecurityUtilTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
  }

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  @Test
  fun currentUserIdReturnsSubjectUuid() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, listOf())
    assertEquals(USER_ID, SecurityUtil.currentUserId())
  }

  @Test
  fun currentUserIdThrowsWhenUnauthenticated() {
    assertThrows(IllegalStateException::class.java, SecurityUtil::currentUserId)
  }

  @Test
  fun currentUserIdThrowsWhenPrincipalIsNotAUuid() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken("not-a-uuid", null, listOf())
    assertThrows(IllegalArgumentException::class.java, SecurityUtil::currentUserId)
  }
}
