package com.epitomejob.notification.controller

import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.notification.dto.NotificationDto
import com.epitomejob.notification.dto.NotificationPage
import com.epitomejob.notification.service.NotificationService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(NotificationController::class)
@Import(SecurityConfig::class)
class NotificationControllerTest {

  companion object {
    private val USER_ID = UUID.randomUUID()
    private val NOTIFICATION_ID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: NotificationService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun authenticate() {
    val claims = mock<Claims>()
    whenever(claims.subject).thenReturn(USER_ID.toString())
    whenever(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    whenever(jwt.parse("test-token")).thenReturn(claims)
  }

  private fun dto(): NotificationDto {
    return NotificationDto(NOTIFICATION_ID.toString(), "TRACKER_CHANGED",
      "Application updated", "Senior Software Engineer, Backend (Java) moved to Applied.",
      "/tracker", false, false, "2026-11-13T10:00:00Z")
  }

  @Test
  fun getMeReturns200WithPageShape() {
    authenticate()
    whenever(service.listMine(0, 20)).thenReturn(
      NotificationPage(listOf(dto()), 0, 20, 1, 1))

    mockMvc.perform(get("/notifications/me")
        .header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.content[0].id").value(NOTIFICATION_ID.toString()))
      .andExpect(jsonPath("$.content[0].type").value("TRACKER_CHANGED"))
      .andExpect(jsonPath("$.content[0].derived").value(false))
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.totalElements").value(1))
      .andExpect(jsonPath("$.totalPages").value(1))
  }

  @Test
  fun getMeForwardsPageAndSize() {
    authenticate()
    whenever(service.listMine(1, 5)).thenReturn(
      NotificationPage(listOf(), 1, 5, 0, 0))

    mockMvc.perform(get("/notifications/me?page=1&size=5")
        .header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
    verify(service).listMine(1, 5)
  }

  @Test
  fun getUnreadCountReturnsCount() {
    authenticate()
    whenever(service.unreadCount()).thenReturn(2L)

    mockMvc.perform(get("/notifications/unread-count")
        .header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.unreadCount").value(2))
  }

  @Test
  fun markReadReturns200() {
    authenticate()
    whenever(service.markRead(NOTIFICATION_ID)).thenReturn(
      NotificationDto(NOTIFICATION_ID.toString(), "TRACKER_CHANGED",
        "Application updated", "Senior Software Engineer, Backend (Java) moved to Applied.",
        "/tracker", true, false, "2026-11-13T10:00:00Z"))

    mockMvc.perform(post("/notifications/$NOTIFICATION_ID/read")
        .header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.isRead").value(true))
  }

  @Test
  fun markReadUnknownIdIs404() {
    authenticate()
    whenever(service.markRead(NOTIFICATION_ID)).thenThrow(
      ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"))

    mockMvc.perform(post("/notifications/$NOTIFICATION_ID/read")
        .header("Authorization", "Bearer test-token"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun markReadDerivedIdIs404WithoutCallingService() {
    authenticate()

    mockMvc.perform(post("/notifications/clock-14/read")
        .header("Authorization", "Bearer test-token"))
      .andExpect(status().isNotFound)
    verify(service, never()).markRead(any())
  }

  @Test
  fun readAllReturnsCount() {
    authenticate()
    whenever(service.markAllRead()).thenReturn(2)

    mockMvc.perform(post("/notifications/read-all")
        .header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.readCount").value(2))
  }

  @Test
  fun getMeWithoutTokenIsForbidden() {
    mockMvc.perform(get("/notifications/me"))
      .andExpect(status().isForbidden)
  }

  @Test
  fun readAllWithoutTokenIsForbidden() {
    mockMvc.perform(post("/notifications/read-all"))
      .andExpect(status().isForbidden)
  }
}
