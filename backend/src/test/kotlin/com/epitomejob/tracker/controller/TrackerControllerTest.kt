package com.epitomejob.tracker.controller

import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.tracker.dto.TrackerDto
import com.epitomejob.tracker.service.TrackerService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(TrackerController::class)
@Import(SecurityConfig::class)
class TrackerControllerTest {

  companion object {
    private val USER_ID = UUID.randomUUID()
    private val JOB_ID = UUID.randomUUID()
    private val ENTRY_ID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: TrackerService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun authenticate() {
    val claims = mock<Claims>()
    whenever(claims.subject).thenReturn(USER_ID.toString())
    whenever(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    whenever(jwt.parse("test-token")).thenReturn(claims)
  }

  private fun dto(): TrackerDto {
    return TrackerDto(ENTRY_ID, JOB_ID, "Senior Software Engineer, Backend (Java)",
      "Northwind Health Systems", null, "SAVED", null, Instant.now(), Instant.now())
  }

  @Test
  fun createReturns201() {
    authenticate()
    whenever(service.create(eq(JOB_ID), any())).thenReturn(dto())

    mockMvc.perform(post("/tracker")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"jobId\":\"$JOB_ID\"}"))
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.status").value("SAVED"))
  }

  @Test
  fun createDuplicateIs409() {
    authenticate()
    whenever(service.create(eq(JOB_ID), any()))
      .thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "Job already saved in tracker"))

    mockMvc.perform(post("/tracker")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"jobId\":\"$JOB_ID\"}"))
      .andExpect(status().isConflict)
  }

  @Test
  fun createWithoutJobIdIs400() {
    authenticate()

    mockMvc.perform(post("/tracker")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isBadRequest)
  }

  @Test
  fun listMineReturnsEntries() {
    authenticate()
    whenever(service.listMine()).thenReturn(listOf(dto()))

    mockMvc.perform(get("/tracker/me").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$[0].company").value("Northwind Health Systems"))
  }

  @Test
  fun updateReturnsUpdatedEntry() {
    authenticate()
    val applied = TrackerDto(ENTRY_ID, JOB_ID,
      "Senior Software Engineer, Backend (Java)", "Northwind Health Systems", null,
      "APPLIED", "Applied via portal", Instant.now(), Instant.now())
    whenever(service.update(eq(ENTRY_ID), eq("APPLIED"), eq("Applied via portal")))
      .thenReturn(applied)

    mockMvc.perform(put("/tracker/$ENTRY_ID")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"APPLIED\",\"notes\":\"Applied via portal\"}"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.status").value("APPLIED"))
  }

  @Test
  fun deleteReturns204() {
    authenticate()

    mockMvc.perform(delete("/tracker/$ENTRY_ID")
        .header("Authorization", "Bearer test-token"))
      .andExpect(status().isNoContent)
  }
}
