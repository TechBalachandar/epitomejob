package com.epitomejob.tracker.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.tracker.dto.TrackerDto;
import com.epitomejob.tracker.service.TrackerService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrackerController.class)
@Import(SecurityConfig.class)
class TrackerControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID ENTRY_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TrackerService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private TrackerDto dto() {
    return new TrackerDto(ENTRY_ID, JOB_ID, "Senior Software Engineer, Backend (Java)",
        "Northwind Health Systems", null, "SAVED", null, Instant.now(), Instant.now());
  }

  @Test
  void createReturns201() throws Exception {
    authenticate();
    when(service.create(eq(JOB_ID), any())).thenReturn(dto());

    mockMvc.perform(post("/tracker")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"jobId\":\"" + JOB_ID + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SAVED"));
  }

  @Test
  void createDuplicateIs409() throws Exception {
    authenticate();
    when(service.create(eq(JOB_ID), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Job already saved in tracker"));

    mockMvc.perform(post("/tracker")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"jobId\":\"" + JOB_ID + "\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void createWithoutJobIdIs400() throws Exception {
    authenticate();

    mockMvc.perform(post("/tracker")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listMineReturnsEntries() throws Exception {
    authenticate();
    when(service.listMine()).thenReturn(List.of(dto()));

    mockMvc.perform(get("/tracker/me").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].company").value("Northwind Health Systems"));
  }

  @Test
  void updateReturnsUpdatedEntry() throws Exception {
    authenticate();
    TrackerDto applied = new TrackerDto(ENTRY_ID, JOB_ID,
        "Senior Software Engineer, Backend (Java)", "Northwind Health Systems", null,
        "APPLIED", "Applied via portal", Instant.now(), Instant.now());
    when(service.update(eq(ENTRY_ID), eq("APPLIED"), eq("Applied via portal")))
        .thenReturn(applied);

    mockMvc.perform(put("/tracker/" + ENTRY_ID)
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"APPLIED\",\"notes\":\"Applied via portal\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPLIED"));
  }

  @Test
  void deleteReturns204() throws Exception {
    authenticate();

    mockMvc.perform(delete("/tracker/" + ENTRY_ID)
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isNoContent());
  }
}
