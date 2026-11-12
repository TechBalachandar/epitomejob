package com.epitomejob.matching.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.job.dto.JobDto;
import com.epitomejob.matching.dto.MatchDto;
import com.epitomejob.matching.dto.MatchReasonDto;
import com.epitomejob.matching.service.MatchingService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchingController.class)
@Import(SecurityConfig.class)
class MatchingControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MatchingService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private MatchDto match() {
    JobDto job = new JobDto(JOB_ID, "Senior Software Engineer, Backend (Java)",
        "Northwind Health Systems", "Reston, VA", "Hybrid", 145000, 175000, "USD",
        true, List.of("Java 21", "Spring Boot", "Kafka"), "Backend APIs.", "Backend",
        Instant.parse("2026-11-10T09:00:00Z"));
    return new MatchDto(job, 82,
        List.of(new MatchReasonDto("Skills: Java 21, Spring Boot, Kafka (+38)", 38)),
        List.of("Missing skill: Azure"), "RESUME");
  }

  @Test
  void matchesForMeReturnsList() throws Exception {
    authenticate();
    when(service.matchesForMe()).thenReturn(List.of(match()));

    mockMvc.perform(get("/matches/me").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].score").value(82))
        .andExpect(jsonPath("$[0].source").value("RESUME"))
        .andExpect(jsonPath("$[0].gaps[0]").value("Missing skill: Azure"));
  }

  @Test
  void matchForJobReturnsMatch() throws Exception {
    authenticate();
    when(service.matchForJob(JOB_ID)).thenReturn(match());

    mockMvc.perform(get("/matches/" + JOB_ID).header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.job.id").value(JOB_ID.toString()));
  }

  @Test
  void matchForMissingJobIs404() throws Exception {
    authenticate();
    when(service.matchForJob(JOB_ID))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

    mockMvc.perform(get("/matches/" + JOB_ID).header("Authorization", "Bearer test-token"))
        .andExpect(status().isNotFound());
  }
}
