package com.epitomejob.profile.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.profile.dto.ProfileResponse;
import com.epitomejob.profile.dto.ProfileUpdateRequest;
import com.epitomejob.profile.service.ProfileService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        "Reston, VA", "REMOTE", null, null, "USD", "PRIVATE",
        List.of("Java", "Spring Boot"), LocalDate.of(2026, 10, 15), 60,
        Instant.now(), Instant.now());
  }

  @Test
  void getMeReturnsProfile() throws Exception {
    authenticate();
    when(service.getMe()).thenReturn(response());

    mockMvc.perform(get("/profiles/me").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headline").value("Java Developer"))
        .andExpect(jsonPath("$.skills[0]").value("Java"))
        .andExpect(jsonPath("$.skills[1]").value("Spring Boot"))
        .andExpect(jsonPath("$.h1bClockStartDate").value("2026-10-15"))
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
  void putMeRoundTripsNewFields() throws Exception {
    authenticate();
    when(service.upsertMe(any())).thenReturn(response());

    mockMvc.perform(put("/profiles/me")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"skills":["Java","Spring Boot"],"h1bClockStartDate":"2026-10-15","currency":"EUR","visibility":"PUBLIC"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skills[0]").value("Java"))
        .andExpect(jsonPath("$.skills[1]").value("Spring Boot"))
        .andExpect(jsonPath("$.h1bClockStartDate").value("2026-10-15"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.visibility").value("PUBLIC"));

    ArgumentCaptor<ProfileUpdateRequest> captor =
        ArgumentCaptor.forClass(ProfileUpdateRequest.class);
    verify(service).upsertMe(captor.capture());
    assertEquals(List.of("Java", "Spring Boot"), captor.getValue().skills());
    assertEquals(LocalDate.of(2026, 10, 15), captor.getValue().h1bClockStartDate());
    assertEquals("EUR", captor.getValue().currency());
    assertEquals("PUBLIC", captor.getValue().visibility());
  }

  @Test
  void putMeWithEmptySkillsListClears() throws Exception {
    authenticate();
    when(service.upsertMe(any())).thenReturn(response());

    mockMvc.perform(put("/profiles/me")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"skills":[]}"""))
        .andExpect(status().isOk());

    ArgumentCaptor<ProfileUpdateRequest> captor =
        ArgumentCaptor.forClass(ProfileUpdateRequest.class);
    verify(service).upsertMe(captor.capture());
    assertEquals(List.of(), captor.getValue().skills());
  }

  @Test
  void putMeWithBadVisibilityReturns400() throws Exception {
    authenticate();
    when(service.upsertMe(any())).thenThrow(
        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid visibility"));

    mockMvc.perform(put("/profiles/me")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"visibility":"SECRET"}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void putMeWithBadCurrencyReturns400() throws Exception {
    authenticate();

    mockMvc.perform(put("/profiles/me")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"currency":"usd"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void putMeWithTooManySkillsReturns400() throws Exception {
    authenticate();
    String skills = "[" + "\"Skill\",".repeat(30) + "\"Skill\"]";

    mockMvc.perform(put("/profiles/me")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"skills\":" + skills + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void putMeWithTooLongSkillReturns400() throws Exception {
    authenticate();
    String longSkill = "x".repeat(61);

    mockMvc.perform(put("/profiles/me")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"skills\":[\"" + longSkill + "\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
