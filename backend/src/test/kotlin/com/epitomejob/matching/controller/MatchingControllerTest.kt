package com.epitomejob.matching.controller

import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.job.dto.JobDto
import com.epitomejob.matching.dto.MatchDto
import com.epitomejob.matching.dto.MatchReasonDto
import com.epitomejob.matching.service.MatchingService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(MatchingController::class)
@Import(SecurityConfig::class)
class MatchingControllerTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private val JOB_ID: UUID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: MatchingService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun authenticate() {
    val claims = mock(Claims::class.java)
    Mockito.`when`(claims.subject).thenReturn(USER_ID.toString())
    Mockito.`when`(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    Mockito.`when`(jwt.parse("test-token")).thenReturn(claims)
  }

  private fun match(): MatchDto {
    val job = JobDto(
      JOB_ID, "Senior Software Engineer, Backend (Java)",
      "Northwind Health Systems", "Reston, VA", "Hybrid", 145000, 175000, "USD",
      true, listOf("Java 21", "Spring Boot", "Kafka"), "Backend APIs.", "Backend",
      Instant.parse("2026-11-10T09:00:00Z"), "LINKEDIN",
      "https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer"
    )
    return MatchDto(
      job, 82,
      listOf(MatchReasonDto("Skills: Java 21, Spring Boot, Kafka (+38)", 38)),
      listOf("Missing skill: Azure"), "RESUME"
    )
  }

  @Test
  fun matchesForMeReturnsList() {
    authenticate()
    Mockito.`when`(service.matchesForMe(null)).thenReturn(listOf(match()))

    mockMvc.perform(get("/matches/me").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].score").value(82))
      .andExpect(jsonPath("$[0].source").value("RESUME"))
      .andExpect(jsonPath("$[0].job.source").value("LINKEDIN"))
      .andExpect(jsonPath("$[0].job.sourceUrl").value("https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer"))
      .andExpect(jsonPath("$[0].gaps[0]").value("Missing skill: Azure"))
  }

  @Test
  fun matchesForMeWithResumeIdPassesItThrough() {
    authenticate()
    val resumeId = UUID.randomUUID()
    Mockito.`when`(service.matchesForMe(resumeId)).thenReturn(listOf(match()))

    mockMvc.perform(get("/matches/me?resumeId=" + resumeId).header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].score").value(82))
  }

  @Test
  fun matchForJobWithResumeIdPassesItThrough() {
    authenticate()
    val resumeId = UUID.randomUUID()
    Mockito.`when`(service.matchForJob(JOB_ID, resumeId)).thenReturn(match())

    mockMvc.perform(get("/matches/" + JOB_ID + "?resumeId=" + resumeId).header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.job.id").value(JOB_ID.toString()))
  }

  @Test
  fun matchForJobReturnsMatch() {
    authenticate()
    Mockito.`when`(service.matchForJob(JOB_ID, null)).thenReturn(match())

    mockMvc.perform(get("/matches/" + JOB_ID).header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.job.id").value(JOB_ID.toString()))
  }

  @Test
  fun matchForMissingJobIs404() {
    authenticate()
    Mockito.`when`(service.matchForJob(JOB_ID, null))
      .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"))

    mockMvc.perform(get("/matches/" + JOB_ID).header("Authorization", "Bearer test-token"))
      .andExpect(status().isNotFound())
  }
}
