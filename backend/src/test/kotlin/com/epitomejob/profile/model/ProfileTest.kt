package com.epitomejob.profile.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class ProfileTest {

  private fun profile(): Profile {
    return Profile(UUID.randomUUID())
  }

  @Test
  fun emptyProfileScoresZero() {
    assertEquals(0, profile().computeCompleteness())
  }

  @Test
  fun eachFieldIsWorthTwentyPoints() {
    val p = profile()
    p.headline = "Java Developer"
    assertEquals(20, p.computeCompleteness())
    p.summary = "17 years of experience"
    assertEquals(40, p.computeCompleteness())
    p.location = "Reston, VA"
    assertEquals(60, p.computeCompleteness())
    p.workType = "REMOTE"
    assertEquals(80, p.computeCompleteness())
    p.salaryMin = 150_000
    assertEquals(80, p.computeCompleteness(), "salary counts only when min AND max are set")
    p.salaryMax = 200_000
    assertEquals(100, p.computeCompleteness())
  }

  @Test
  fun blankStringsDoNotCount() {
    val p = profile()
    p.headline = "   "
    p.summary = ""
    assertEquals(0, p.computeCompleteness())
  }

  @Test
  fun linkedinUrlAndWorkAuthorizationAddNoCompletenessPoints() {
    val p = profile()
    p.linkedinUrl = "https://www.linkedin.com/in/balachandar-jeldi"
    p.workAuthorization = "H1B"
    assertEquals(0, p.computeCompleteness(), "LinkedIn URL + work authorisation alone score 0")

    p.headline = "Java Developer"
    p.summary = "17 years of experience"
    p.location = "Reston, VA"
    p.workType = "REMOTE"
    p.salaryMin = 150_000
    p.salaryMax = 200_000
    assertEquals(
      100, p.computeCompleteness(),
      "the five core fields still score exactly 100 with LinkedIn URL + work authorisation set"
    )
  }

  @Test
  fun skillsAndClockDateAddNoCompletenessPoints() {
    val p = profile()
    p.skills = mutableSetOf("Java", "LangGraph")
    p.h1bClockStartDate = LocalDate.of(2026, 10, 15)
    assertEquals(0, p.computeCompleteness(), "skills + clock date alone score 0")

    p.headline = "Java Developer"
    p.summary = "17 years of experience"
    p.location = "Reston, VA"
    p.workType = "REMOTE"
    p.salaryMin = 150_000
    p.salaryMax = 200_000
    assertEquals(
      100, p.computeCompleteness(),
      "the five core fields still score exactly 100 with skills + clock set"
    )
  }
}
