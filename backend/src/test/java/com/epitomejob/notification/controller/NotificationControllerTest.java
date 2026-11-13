package com.epitomejob.notification.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.notification.dto.NotificationDto;
import com.epitomejob.notification.dto.NotificationPage;
import com.epitomejob.notification.service.NotificationService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID NOTIFICATION_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private NotificationService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private NotificationDto dto() {
    return new NotificationDto(NOTIFICATION_ID.toString(), "TRACKER_CHANGED",
        "Application updated", "Senior Software Engineer, Backend (Java) moved to Applied.",
        "/tracker", false, false, "2026-11-13T10:00:00Z");
  }

  @Test
  void getMeReturns200WithPageShape() throws Exception {
    authenticate();
    when(service.listMine(0, 20)).thenReturn(
        new NotificationPage(List.of(dto()), 0, 20, 1, 1));

    mockMvc.perform(get("/notifications/me")
                    .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(NOTIFICATION_ID.toString()))
            .andExpect(jsonPath("$.content[0].type").value("TRACKER_CHANGED"))
            .andExpect(jsonPath("$.content[0].derived").value(false))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void getMeForwardsPageAndSize() throws Exception {
    authenticate();
    when(service.listMine(1, 5)).thenReturn(
        new NotificationPage(List.of(), 1, 5, 0, 0));

    mockMvc.perform(get("/notifications/me?page=1&size=5")
                    .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk());
    verify(service).listMine(1, 5);
  }

  @Test
  void getUnreadCountReturnsCount() throws Exception {
    authenticate();
    when(service.unreadCount()).thenReturn(2L);

    mockMvc.perform(get("/notifications/unread-count")
                    .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(2));
  }

  @Test
  void markReadReturns200() throws Exception {
    authenticate();
    when(service.markRead(NOTIFICATION_ID)).thenReturn(
        new NotificationDto(NOTIFICATION_ID.toString(), "TRACKER_CHANGED",
            "Application updated", "Senior Software Engineer, Backend (Java) moved to Applied.",
            "/tracker", true, false, "2026-11-13T10:00:00Z"));

    mockMvc.perform(post("/notifications/" + NOTIFICATION_ID + "/read")
                    .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isRead").value(true));
  }

  @Test
  void markReadUnknownIdIs404() throws Exception {
    authenticate();
    when(service.markRead(NOTIFICATION_ID)).thenThrow(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

    mockMvc.perform(post("/notifications/" + NOTIFICATION_ID + "/read")
                    .header("Authorization", "Bearer test-token"))
            .andExpect(status().isNotFound());
  }

  @Test
  void markReadDerivedIdIs404WithoutCallingService() throws Exception {
    authenticate();

    mockMvc.perform(post("/notifications/clock-14/read")
                    .header("Authorization", "Bearer test-token"))
            .andExpect(status().isNotFound());
    verify(service, never()).markRead(any());
  }

  @Test
  void readAllReturnsCount() throws Exception {
    authenticate();
    when(service.markAllRead()).thenReturn(2);

    mockMvc.perform(post("/notifications/read-all")
                    .header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readCount").value(2));
  }

  @Test
  void getMeWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(get("/notifications/me"))
            .andExpect(status().isForbidden());
  }

  @Test
  void readAllWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(post("/notifications/read-all"))
            .andExpect(status().isForbidden());
  }
}
