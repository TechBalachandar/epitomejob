package com.epitomejob.profile.controller

import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.profile.dto.ProfileResponse
import com.epitomejob.profile.dto.ProfileUpdateRequest
import com.epitomejob.profile.service.ProfileService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(ProfileController::class)
@Import(SecurityConfig::class)
class ProfileControllerTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: ProfileService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun authenticate() {
    val claims = mock(Claims::class.java)
    Mockito.`when`(claims.subject).thenReturn(USER_ID.toString())
    Mockito.`when`(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    Mockito.`when`(jwt.parse("test-token")).thenReturn(claims)
  }

  private fun response(): ProfileResponse {
    return ProfileResponse(
      UUID.randomUUID(), USER_ID, "Java Developer", null,
      "Reston, VA", "REMOTE", null, null, "USD", "PRIVATE",
      listOf("Java", "Spring Boot"), LocalDate.of(2026, 10, 15), 60,
      Instant.now(), Instant.now(), null, null
    )
  }

  @Test
  fun getMeReturnsProfile() {
    authenticate()
    Mockito.`when`(service.getMe()).thenReturn(response())

    mockMvc.perform(get("/profiles/me").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.headline").value("Java Developer"))
      .andExpect(jsonPath("$.skills[0]").value("Java"))
      .andExpect(jsonPath("$.skills[1]").value("Spring Boot"))
      .andExpect(jsonPath("$.h1bClockStartDate").value("2026-10-15"))
      .andExpect(jsonPath("$.completeness").value(60))
  }

  @Test
  fun getMeWithoutTokenIsForbidden() {
    mockMvc.perform(get("/profiles/me"))
      .andExpect(status().isForbidden())
  }

  @Test
  fun putMeReturnsUpdatedProfile() {
    authenticate()
    Mockito.`when`(service.upsertMe(any())).thenReturn(response())

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"headline":"Java Developer","location":"Reston, VA","workType":"REMOTE"}""")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.completeness").value(60))
  }

  @Test
  fun putMeRoundTripsNewFields() {
    authenticate()
    Mockito.`when`(service.upsertMe(any())).thenAnswer { inv ->
      val req: ProfileUpdateRequest = inv.getArgument(0)
      ProfileResponse(
        UUID.randomUUID(), USER_ID, "Java Developer", null,
        "Reston, VA", "REMOTE", null, null, req.currency, req.visibility,
        req.skills ?: listOf(), req.h1bClockStartDate, 60, Instant.now(), Instant.now(),
        req.linkedinUrl, req.workAuthorization
      )
    }

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
          """{"skills":["Java","Spring Boot"],"h1bClockStartDate":"2026-10-15","currency":"EUR","visibility":"PUBLIC"}"""
        )
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.skills[0]").value("Java"))
      .andExpect(jsonPath("$.skills[1]").value("Spring Boot"))
      .andExpect(jsonPath("$.h1bClockStartDate").value("2026-10-15"))
      .andExpect(jsonPath("$.currency").value("EUR"))
      .andExpect(jsonPath("$.visibility").value("PUBLIC"))

    val captor: ArgumentCaptor<ProfileUpdateRequest> =
      ArgumentCaptor.forClass(ProfileUpdateRequest::class.java)
    verify(service).upsertMe(captor.capture())
    assertEquals(listOf("Java", "Spring Boot"), captor.value.skills)
    assertEquals(LocalDate.of(2026, 10, 15), captor.value.h1bClockStartDate)
    assertEquals("EUR", captor.value.currency)
    assertEquals("PUBLIC", captor.value.visibility)
  }

  @Test
  fun putMeWithEmptySkillsListClears() {
    authenticate()
    Mockito.`when`(service.upsertMe(any())).thenReturn(response())

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"skills":[]}""")
    )
      .andExpect(status().isOk())

    val captor: ArgumentCaptor<ProfileUpdateRequest> =
      ArgumentCaptor.forClass(ProfileUpdateRequest::class.java)
    verify(service).upsertMe(captor.capture())
    assertEquals(emptyList<String>(), captor.value.skills)
  }

  @Test
  fun putMeWithBadVisibilityReturns400() {
    authenticate()
    Mockito.`when`(service.upsertMe(any())).thenThrow(
      ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid visibility")
    )

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"visibility":"SECRET"}""")
    )
      .andExpect(status().isBadRequest())
  }

  @Test
  fun putMeWithBadCurrencyReturns400() {
    authenticate()

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"currency":"usd"}""")
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
  }

  @Test
  fun putMeWithTooManySkillsReturns400() {
    authenticate()
    val skills = "[" + "\"Skill\",".repeat(30) + "\"Skill\"]"

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"skills\":" + skills + "}")
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
  }

  @Test
  fun putMeWithTooLongSkillReturns400() {
    authenticate()
    val longSkill = "x".repeat(61)

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"skills\":[\"" + longSkill + "\"]}")
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
  }

  @Test
  fun putMeRoundTripsLinkedinUrlAndWorkAuthorization() {
    authenticate()
    Mockito.`when`(service.upsertMe(any())).thenAnswer { inv ->
      val req: ProfileUpdateRequest = inv.getArgument(0)
      ProfileResponse(
        UUID.randomUUID(), USER_ID, "Java Developer", null,
        "Reston, VA", "REMOTE", null, null, req.currency, req.visibility,
        req.skills ?: listOf(), req.h1bClockStartDate, 60, Instant.now(), Instant.now(),
        req.linkedinUrl, req.workAuthorization
      )
    }

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
          """{"linkedinUrl":"https://www.linkedin.com/in/balachandar-jeldi","workAuthorization":"H1B"}"""
        )
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.linkedinUrl").value("https://www.linkedin.com/in/balachandar-jeldi"))
      .andExpect(jsonPath("$.workAuthorization").value("H1B"))

    val captor: ArgumentCaptor<ProfileUpdateRequest> =
      ArgumentCaptor.forClass(ProfileUpdateRequest::class.java)
    verify(service).upsertMe(captor.capture())
    assertEquals(
      "https://www.linkedin.com/in/balachandar-jeldi",
      captor.value.linkedinUrl
    )
    assertEquals("H1B", captor.value.workAuthorization)
  }

  @Test
  fun putMeWithBadLinkedinUrlReturns400() {
    authenticate()
    Mockito.`when`(service.upsertMe(any())).thenThrow(
      ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid LinkedIn URL")
    )

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"linkedinUrl":"www.linkedin.com/in/balachandar-jeldi"}""")
    )
      .andExpect(status().isBadRequest())
  }

  @Test
  fun putMeWithBadWorkAuthorizationReturns400() {
    authenticate()
    Mockito.`when`(service.upsertMe(any())).thenThrow(
      ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid work authorization")
    )

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"workAuthorization":"CITIZEN"}""")
    )
      .andExpect(status().isBadRequest())
  }

  @Test
  fun putMeWithTooLongLinkedinUrlReturns400() {
    authenticate()
    val longUrl = "https://" + "x".repeat(293) // 301 chars, over the 300 cap

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"linkedinUrl\":\"" + longUrl + "\"}")
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
  }

  @Test
  fun putMeWithTooLongHeadlineReturns400() {
    authenticate()
    val longHeadline = "x".repeat(201)

    mockMvc.perform(
      put("/profiles/me")
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"headline\":\"" + longHeadline + "\"}")
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
  }
}
