package com.epitomejob.job.controller

import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.job.dto.JobDto
import com.epitomejob.job.service.JobService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(JobController::class)
@Import(SecurityConfig::class)
class JobControllerTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private val JOB_ID: UUID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: JobService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun authenticate() {
    val claims = mock(Claims::class.java)
    Mockito.`when`(claims.subject).thenReturn(USER_ID.toString())
    Mockito.`when`(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    Mockito.`when`(jwt.parse("test-token")).thenReturn(claims)
  }

  private fun jobDto(): JobDto {
    return JobDto(
      JOB_ID, "Senior Software Engineer, Backend (Java)",
      "Northwind Health Systems", "Reston, VA", "Hybrid", 145000, 175000, "USD",
      true, listOf("Java 21", "Spring Boot", "Kafka"), "Backend APIs.", "Backend",
      Instant.parse("2026-11-10T09:00:00Z"), "LINKEDIN",
      "https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer"
    )
  }

  @Test
  fun listReturnsPageShape() {
    authenticate()
    Mockito.`when`(service.search(eq("backend"), any(), any(), any(), any(), eq(PageRequest.of(0, 20))))
      .thenReturn(PageImpl(listOf(jobDto()), PageRequest.of(0, 20), 1))

    mockMvc.perform(get("/jobs?query=backend").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.totalElements").value(1))
      .andExpect(jsonPath("$.totalPages").value(1))
      .andExpect(jsonPath("$.content[0].title").value("Senior Software Engineer, Backend (Java)"))
      .andExpect(jsonPath("$.content[0].skills[0]").value("Java 21"))
      .andExpect(jsonPath("$.content[0].source").value("LINKEDIN"))
      .andExpect(jsonPath("$.content[0].sourceUrl").value("https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer"))
  }

  @Test
  fun listWithSourceFilterPassesSourceThrough() {
    authenticate()
    Mockito.`when`(service.search(any(), any(), any(), any(), eq("linkedin"), any()))
      .thenReturn(PageImpl(listOf(jobDto()), PageRequest.of(0, 20), 1))

    mockMvc.perform(get("/jobs?source=linkedin").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(1))

    verify(service).search(any(), any(), any(), any(), eq("linkedin"), any())
  }

  @Test
  fun getOneReturnsJob() {
    authenticate()
    Mockito.`when`(service.getJob(JOB_ID)).thenReturn(jobDto())

    mockMvc.perform(get("/jobs/" + JOB_ID).header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.company").value("Northwind Health Systems"))
      .andExpect(jsonPath("$.source").value("LINKEDIN"))
      .andExpect(jsonPath("$.sourceUrl").value("https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer"))
  }

  @Test
  fun getOneMissingIs404() {
    authenticate()
    Mockito.`when`(service.getJob(JOB_ID))
      .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"))

    mockMvc.perform(get("/jobs/" + JOB_ID).header("Authorization", "Bearer test-token"))
      .andExpect(status().isNotFound())
  }

  @Test
  fun listWithoutTokenIsForbidden() {
    mockMvc.perform(get("/jobs"))
      .andExpect(status().isForbidden())
  }
}
