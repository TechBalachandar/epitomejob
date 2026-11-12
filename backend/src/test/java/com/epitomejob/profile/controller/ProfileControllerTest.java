package com.epitomejob.profile.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.profile.dto.ProfileResponse;
import com.epitomejob.profile.service.ProfileService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ProfileService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private ProfileResponse response() {
    return new ProfileResponse(UUID.randomUUID(), USER_ID, "Java Developer", null,
        "Reston, VA", "REMOTE", null, null, "USD", "PRIVATE", 60,
        Instant.now(), Instant.now());
  }

  @Test
  void getMeReturnsProfile() throws Exception {
    authenticate();
    when(service.getMe()).thenReturn(response());

    mockMvc.perform(get("/profiles/me").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headline").value("Java Developer"))
        .andExpect(jsonPath("$.completeness").value(60));
  }

  @Test
  void getMeWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(get("/profiles/me"))
        .andExpect(status().isForbidden());
  }

  @Test
  void putMeReturnsUpdatedProfile() throws Exception {
    authenticate();
    when(service.upsertMe(any())).thenReturn(response());

    mockMvc.perform(put("/profiles/me")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"headline":"Java Developer","location":"Reston, VA","workType":"REMOTE"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completeness").value(60));
  }

  @Test
  void putMeWithTooLongHeadlineReturns400() throws Exception {
    authenticate();
    String longHeadline = "x".repeat(201);

    mockMvc.perform(put("/profiles/me")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"headline\":\"" + longHeadline + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
