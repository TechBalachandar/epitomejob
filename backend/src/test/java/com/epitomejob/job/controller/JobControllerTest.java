package com.epitomejob.job.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.job.dto.JobDto;
import com.epitomejob.job.service.JobService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
@Import(SecurityConfig.class)
class JobControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JobService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private JobDto jobDto() {
    return new JobDto(JOB_ID, "Senior Software Engineer, Backend (Java)",
        "Northwind Health Systems", "Reston, VA", "Hybrid", 145000, 175000, "USD",
        true, List.of("Java 21", "Spring Boot", "Kafka"), "Backend APIs.", "Backend",
        Instant.parse("2026-11-10T09:00:00Z"), "LINKEDIN", "https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer");
  }

  @Test
  void listReturnsPageShape() throws Exception {
    authenticate();
    when(service.search(eq("backend"), any(), any(), any(), any(), eq(PageRequest.of(0, 20))))
        .thenReturn(new PageImpl<>(List.of(jobDto()), PageRequest.of(0, 20), 1));

    mockMvc.perform(get("/jobs?query=backend").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.content[0].title").value("Senior Software Engineer, Backend (Java)"))
        .andExpect(jsonPath("$.content[0].skills[0]").value("Java 21"))
        .andExpect(jsonPath("$.content[0].source").value("LINKEDIN"))
        .andExpect(jsonPath("$.content[0].sourceUrl")
            .value("https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer"));
  }

  @Test
  void listWithSourceFilterPassesSourceThrough() throws Exception {
    authenticate();
    when(service.search(any(), any(), any(), any(), eq("linkedin"), any()))
        .thenReturn(new PageImpl<>(List.of(jobDto()), PageRequest.of(0, 20), 1));

    mockMvc.perform(get("/jobs?source=linkedin").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    verify(service).search(any(), any(), any(), any(), eq("linkedin"), any());
  }

  @Test
  void getOneReturnsJob() throws Exception {
    authenticate();
    when(service.getJob(JOB_ID)).thenReturn(jobDto());

    mockMvc.perform(get("/jobs/" + JOB_ID).header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.company").value("Northwind Health Systems"))
        .andExpect(jsonPath("$.source").value("LINKEDIN"))
        .andExpect(jsonPath("$.sourceUrl")
            .value("https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer"));
  }

  @Test
  void getOneMissingIs404() throws Exception {
    authenticate();
    when(service.getJob(JOB_ID))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

    mockMvc.perform(get("/jobs/" + JOB_ID).header("Authorization", "Bearer test-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void listWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(get("/jobs"))
        .andExpect(status().isForbidden());
  }
}
