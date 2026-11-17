package com.epitomejob.profile.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.profile.dto.ProfileUpdateRequest
import com.epitomejob.profile.model.Profile
import com.epitomejob.profile.repository.ProfileRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import java.util.stream.IntStream

@ExtendWith(MockitoExtension::class)
class ProfileServiceTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
  }

  @Mock
  private lateinit var profiles: ProfileRepository

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticate() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, listOf())
  }

  private fun service(): ProfileService {
    return ProfileService(profiles)
  }

  @Test
  fun upsertCreatesProfileAndScoresCompleteness() {
    authenticate()
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty())
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        "Java Developer", null, "Reston, VA", "REMOTE", null, null, null, null, null, null, null, null
      )
    )

    assertEquals("Java Developer", response.headline)
    assertEquals(emptyList<String>(), response.skills, "no skills set yet")
    assertEquals(null, response.h1bClockStartDate)
    assertEquals(60, response.completeness, "headline + location + work type = 60")
    val saved: ArgumentCaptor<Profile> = ArgumentCaptor.forClass(Profile::class.java)
    verify(profiles).save(saved.capture())
    assertEquals(60, saved.value.completeness)
  }

  @Test
  fun upsertKeepsUnchangedFields() {
    authenticate()
    val existing = Profile(USER_ID)
    existing.headline = "Old headline"
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing))
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, "Reston, VA", null, null, null, null, null, null, null, null, null
      )
    )

    assertEquals("Old headline", response.headline, "null fields must not overwrite")
    assertEquals(40, response.completeness, "headline + location = 40")
  }

  @Test
  fun getMeReturnsExistingProfile() {
    authenticate()
    val existing = Profile(USER_ID)
    existing.headline = "Java Developer"
    existing.completeness = 20
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing))

    val response = service().getMe()

    assertEquals(USER_ID, response.userId)
    assertEquals(20, response.completeness)
  }

  @Test
  fun getMeReturnsEmptyProfileWhenNoneExists() {
    authenticate()
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty())

    val response = service().getMe()

    assertEquals(USER_ID, response.userId)
    assertEquals(0, response.completeness)
    assertEquals(emptyList<String>(), response.skills, "skills are never null")
    assertEquals(null, response.h1bClockStartDate)
  }

  @Test
  fun upsertPersistsSkillsTrimmedDedupedAndSortedInResponse() {
    authenticate()
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty())
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null,
        listOf("  Spring Boot ", "Java", "Java", "", "   ", "LangGraph"),
        null, null, null, null, null
      )
    )

    assertEquals(
      listOf("Java", "LangGraph", "Spring Boot"),
      response.skills, "trimmed, deduped, sorted alphabetically"
    )
    val saved: ArgumentCaptor<Profile> = ArgumentCaptor.forClass(Profile::class.java)
    verify(profiles).save(saved.capture())
    assertEquals(
      listOf("Spring Boot", "Java", "LangGraph"),
      saved.value.skills.toList(),
      "entity keeps request order after trim/dedupe"
    )
  }

  @Test
  fun upsertPersistsSkillsAtTheCap() {
    authenticate()
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty())
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }
    val thirty = IntStream.rangeClosed(1, 30).mapToObj { i -> "Skill$i" }.toList()

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null, thirty, null, null, null, null, null
      )
    )

    assertEquals(30, response.skills.size)
  }

  @Test
  fun upsertEmptySkillsListClears() {
    authenticate()
    val existing = Profile(USER_ID)
    existing.skills = mutableSetOf("Java", "Spring Boot")
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing))
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null, listOf(), null, null, null, null, null
      )
    )

    assertEquals(emptyList<String>(), response.skills)
    val saved: ArgumentCaptor<Profile> = ArgumentCaptor.forClass(Profile::class.java)
    verify(profiles).save(saved.capture())
    assertTrue(saved.value.skills.isEmpty())
  }

  @Test
  fun upsertNullSkillsLeaveUnchanged() {
    authenticate()
    val existing = Profile(USER_ID)
    existing.skills = mutableSetOf("Java")
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing))
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null, null, null, null, null, null, null
      )
    )

    assertEquals(listOf("Java"), response.skills, "null skills must not overwrite")
  }

  @Test
  fun upsertPersistsClockDateCurrencyAndVisibility() {
    authenticate()
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty())
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null, null,
        LocalDate.of(2026, 10, 15), "EUR", "PUBLIC", null, null
      )
    )

    assertEquals(LocalDate.of(2026, 10, 15), response.h1bClockStartDate)
    assertEquals("EUR", response.currency)
    assertEquals("PUBLIC", response.visibility)
  }

  @Test
  fun upsertNullClockDateLeavesUnchanged() {
    authenticate()
    val existing = Profile(USER_ID)
    existing.h1bClockStartDate = LocalDate.of(2026, 10, 15)
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing))
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null, null, null, null, null, null, null
      )
    )

    assertEquals(
      LocalDate.of(2026, 10, 15), response.h1bClockStartDate,
      "null clock date must not overwrite"
    )
  }

  @Test
  fun upsertBadVisibilityIs400() {
    authenticate()

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().upsertMe(
        ProfileUpdateRequest(
          null, null, null, null, null, null, null, null, null, "SECRET", null, null
        )
      )
    }

    assertEquals(400, ex.statusCode.value())
  }

  @Test
  fun completenessUnchangedBySkillsAndClockDate() {
    authenticate()
    val existing = Profile(USER_ID)
    existing.headline = "Java Developer"
    existing.summary = "17 years of experience"
    existing.location = "Reston, VA"
    existing.workType = "REMOTE"
    existing.salaryMin = 120_000
    existing.salaryMax = 160_000
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing))
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null,
        listOf("Java", "LangGraph"), LocalDate.of(2026, 10, 15), null, null, null, null
      )
    )

    assertEquals(
      100, response.completeness,
      "skills + clock date add no completeness points"
    )
    assertEquals(listOf("Java", "LangGraph"), response.skills)

    // Same profile without skills/clock scores the same 100.
    val plain = Profile(USER_ID)
    plain.headline = "Java Developer"
    plain.summary = "17 years of experience"
    plain.location = "Reston, VA"
    plain.workType = "REMOTE"
    plain.salaryMin = 120_000
    plain.salaryMax = 160_000
    assertEquals(100, plain.computeCompleteness())
  }

  @Test
  fun upsertPersistsLinkedinUrlAndWorkAuthorization() {
    authenticate()
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty())
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null, null, null, null, null,
        "https://www.linkedin.com/in/balachandar-jeldi", "H1B"
      )
    )

    assertEquals("https://www.linkedin.com/in/balachandar-jeldi", response.linkedinUrl)
    assertEquals("H1B", response.workAuthorization)
    assertEquals(
      0, response.completeness,
      "LinkedIn URL + work authorisation add no completeness points"
    )
    val saved: ArgumentCaptor<Profile> = ArgumentCaptor.forClass(Profile::class.java)
    verify(profiles).save(saved.capture())
    assertEquals(
      "https://www.linkedin.com/in/balachandar-jeldi",
      saved.value.linkedinUrl
    )
    assertEquals("H1B", saved.value.workAuthorization)
  }

  @Test
  fun upsertBadLinkedinUrlIs400() {
    authenticate()
    // Validation runs before the profile is loaded: no repository stub here —
    // a stubbed findByUserId would be unused and Mockito strict stubs fail.
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().upsertMe(
        ProfileUpdateRequest(
          null, null, null, null, null, null, null, null, null, null,
          "www.linkedin.com/in/balachandar-jeldi", null
        )
      )
    }

    assertEquals(400, ex.statusCode.value())
    assertEquals(AppConstants.Errors.INVALID_LINKEDIN_URL, ex.reason)
  }

  @Test
  fun upsertBadWorkAuthorizationIs400() {
    authenticate()
    // Validation runs before the profile is loaded: no repository stub.
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().upsertMe(
        ProfileUpdateRequest(
          null, null, null, null, null, null, null, null, null, null,
          null, "CITIZEN"
        )
      )
    }

    assertEquals(400, ex.statusCode.value())
    assertEquals(AppConstants.Errors.INVALID_WORK_AUTHORIZATION, ex.reason)
  }

  @Test
  fun upsertNullLinkedinUrlAndWorkAuthorizationLeaveUnchanged() {
    authenticate()
    val existing = Profile(USER_ID)
    existing.linkedinUrl = "https://www.linkedin.com/in/balachandar-jeldi"
    existing.workAuthorization = "EAD"
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing))
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, "Reston, VA", null, null, null, null, null, null, null, null, null
      )
    )

    assertEquals(
      "https://www.linkedin.com/in/balachandar-jeldi", response.linkedinUrl,
      "null LinkedIn URL must not overwrite"
    )
    assertEquals("EAD", response.workAuthorization, "null work authorisation must not overwrite")
  }

  @Test
  fun linkedinUrlAndWorkAuthorizationAddNoCompletenessPoints() {
    authenticate()
    val existing = Profile(USER_ID)
    existing.headline = "Java Developer"
    existing.summary = "17 years of experience"
    existing.location = "Reston, VA"
    existing.workType = "REMOTE"
    existing.salaryMin = 120_000
    existing.salaryMax = 160_000
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing))
    Mockito.`when`(profiles.save(any())).thenAnswer { inv -> inv.getArgument(0) }

    val response = service().upsertMe(
      ProfileUpdateRequest(
        null, null, null, null, null, null, null, null, null, null,
        "https://www.linkedin.com/in/balachandar-jeldi", "US_CITIZEN"
      )
    )

    assertEquals(
      100, response.completeness,
      "LinkedIn URL + work authorisation add no completeness points"
    )
  }
}
