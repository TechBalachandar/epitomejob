package com.epitomejob.matching.service

import com.epitomejob.common.exception.ResumeNotReadyException
import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import com.epitomejob.matching.dto.MatchDto
import com.epitomejob.profile.model.Profile
import com.epitomejob.profile.repository.ProfileRepository
import com.epitomejob.resume.model.Resume
import com.epitomejob.resume.repository.ResumeRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MatchingServiceTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
  }

  @Mock
  private lateinit var jobs: JobRepository

  @Mock
  private lateinit var profiles: ProfileRepository

  @Mock
  private lateinit var resumes: ResumeRepository

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticate() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, listOf())
  }

  private fun service(): MatchingService {
    return MatchingService(jobs, profiles, resumes)
  }

  private fun job(
    sponsors: Boolean,
    location: String,
    workType: String,
    min: Int?,
    max: Int?,
    skills: List<String>
  ): Job {
    return Job(
      "Senior Software Engineer, Backend (Java)", "Northwind Health Systems",
      location, workType, min, max, sponsors, skills, "Backend APIs in Java.", "Backend",
      Instant.parse("2026-11-10T09:00:00Z")
    )
  }

  private fun profile(location: String?, workType: String?, min: Int?, max: Int?): Profile {
    val p = Profile(USER_ID)
    p.location = location
    p.workType = workType
    p.salaryMin = min
    p.salaryMax = max
    return p
  }

  private fun readyResume(parsedText: String): Resume {
    val r = Resume(USER_ID, "u/r.pdf", "resume.pdf", "pdf", 100)
    r.status = "READY"
    r.parsedText = parsedText
    return r
  }

  private fun stubCandidate(p: Profile?, r: Resume?) {
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.ofNullable(p))
    Mockito.`when`(resumes.findByUserIdOrderByCreatedAtDesc(USER_ID))
      .thenReturn(if (r == null) mutableListOf<Resume>() else mutableListOf(r))
  }

  @Test
  fun fullMatchScores100FromResume() {
    authenticate()
    val j = job(
      true, "Reston, VA", "Hybrid", 145000, 175000,
      listOf("Java 21", "Spring Boot", "Kafka")
    )
    stubCandidate(
      profile("Reston, VA", "Hybrid", 140000, 180000),
      readyResume("Java 21 developer with Spring Boot and Kafka experience.")
    )
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(j))

    val matches = service().matchesForMe(null)

    assertEquals(1, matches.size)
    assertEquals(100, matches[0].score)
    assertEquals("RESUME", matches[0].source)
    assertTrue(matches[0].gaps.isEmpty())
    assertEquals("Skills: Java 21, Spring Boot, Kafka (+50)", matches[0].reasons[0].label)
  }

  @Test
  fun noResumeUsesProfileSourceAndPartialCreditForUnsetFields() {
    authenticate()
    val j = job(false, "McLean, VA", "Onsite", 120000, 150000, listOf("Java", "Oracle"))
    stubCandidate(null, null)
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(j))

    val match = service().matchesForMe(null)[0]

    assertEquals("PROFILE", match.source)
    // skills 0 + location 0 + work type 5 + salary 7 + sponsorship 0 = 12
    assertEquals(12, match.score)
    assertTrue(match.gaps.contains("Missing skill: Java"))
    assertTrue(match.gaps.contains("Missing skill: Oracle"))
    assertTrue(match.gaps.contains("Location: McLean, VA"))
    assertTrue(match.gaps.contains("No H-1B sponsorship listed"))
    assertTrue(
      match.reasons.any { it.label == "Work type not set — partial credit" && it.points == 5 }
    )
    assertTrue(
      match.reasons.any { it.label == "Salary preference not set — partial credit" && it.points == 7 }
    )
  }

  @Test
  fun remoteJobMatchesAnyLocationAndSalaryBelowMinimumIsAGap() {
    authenticate()
    val j = job(false, "Remote (US)", "Remote", 100000, 150000, listOf("Python"))
    stubCandidate(profile("Reston, VA", "Onsite", 200000, 220000), null)
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(j))

    val match = service().matchesForMe(null)[0]

    assertEquals("PROFILE", match.source)
    // skills 0 + location 10 + work type 0 + salary 0 + sponsorship 0 = 10
    assertEquals(10, match.score)
    assertTrue(match.gaps.contains("Salary below your minimum"))
    assertTrue(match.gaps.contains("Work type: Remote"))
  }

  @Test
  fun salaryOverlapGivesPartialCredit() {
    authenticate()
    val j = job(true, "Remote (US)", "Hybrid", 140000, 155000, listOf("Python"))
    stubCandidate(
      profile("Reston, VA", "Hybrid", 150000, 160000),
      readyResume("Python developer.")
    )
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(j))

    val match = service().matchesForMe(null)[0]

    // skills 50 + location 10 + work type 10 + salary 7 + sponsorship 15 = 92
    assertEquals(92, match.score)
    assertTrue(
      match.reasons.any { it.label == "Salary ranges overlap (+7)" && it.points == 7 }
    )
  }

  @Test
  fun skillMatchesOnFirstToken() {
    authenticate()
    val j = job(true, "Remote (US)", "Remote", 100000, 200000, listOf("Spring Boot"))
    // Candidate mentions only "spring", the first token of "Spring Boot".
    val svc = service()
    val candidate = MatchingService.Candidate(null, "spring developer", "PROFILE")

    val match = svc.score(j, candidate)

    assertTrue(match.reasons.any { it.points == 50 })
    assertTrue(match.gaps.isEmpty() || match.gaps.none { it.startsWith("Missing skill") })
  }

  @Test
  fun matchesAreSortedScoreDesc() {
    authenticate()
    val low = job(false, "McLean, VA", "Onsite", 120000, 150000, listOf("Cobol"))
    val high = job(true, "Remote (US)", "Remote", 100000, 200000, listOf("Java"))
    stubCandidate(
      profile("Reston, VA", "Remote", 100000, 200000),
      readyResume("Java developer.")
    )
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(low, high))

    val matches = service().matchesForMe(null)

    assertEquals(high.id, matches[0].job.id)
    assertTrue(matches[0].score > matches[1].score)
  }

  @Test
  fun profileSkillRaisesScoreForMatchingJob() {
    authenticate()
    val j = job(true, "Remote (US)", "Remote", 100000, 200000, listOf("LangGraph"))
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(j))

    val withoutSkill = profile("Reston, VA", "Remote", 100000, 200000)
    stubCandidate(withoutSkill, null)
    val scoreWithoutSkill = service().matchesForMe(null)[0].score

    val withSkill = profile("Reston, VA", "Remote", 100000, 200000)
    withSkill.skills = mutableListOf("LangGraph")
    stubCandidate(withSkill, null)
    val scoreWithSkill = service().matchesForMe(null)[0].score

    // skills 0 + location 10 + work type 10 + salary 15 + sponsorship 15 = 50
    assertEquals(50, scoreWithoutSkill)
    // skills 50 + location 10 + work type 10 + salary 15 + sponsorship 15 = 100
    assertEquals(100, scoreWithSkill)
    assertTrue(
      scoreWithSkill > scoreWithoutSkill,
      "adding a matching profile skill must raise the score, all else equal"
    )
  }

  @Test
  fun matchForJobMissingJobIs404() {
    authenticate()
    val jobId = UUID.randomUUID()
    Mockito.`when`(jobs.findById(jobId)).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().matchForJob(jobId, null)
    }
    assertEquals(404, ex.statusCode.value())
  }

  // ---- W6 contract §2c: per-resume matching ----

  private fun jobWithSkills(title: String, skills: List<String>): Job {
    return Job(
      title, "Northwind Health Systems", "Remote (US)", "Remote",
      100000, 200000, true, skills, "Remote role.", "Backend",
      Instant.parse("2026-11-10T09:00:00Z")
    )
  }

  @Test
  fun differentResumesGiveDifferentTopMatches() {
    authenticate()
    val javaJob = jobWithSkills(
      "Senior Software Engineer, Backend (Java)",
      listOf("Java", "Spring Boot")
    )
    val androidJob = jobWithSkills("Senior Android Engineer", listOf("Kotlin", "Android"))
    // Profile is skill-free and identical for both calls, so only the resume
    // text can move the scores: non-skill points are equal (50) for both jobs.
    val p = profile("Reston, VA", "Remote", 100000, 200000)
    val javaResume = readyResume("Java Spring Boot developer with Kafka experience.")
    val androidResume = readyResume("Kotlin Android developer, Jetpack Compose.")
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(p))
    Mockito.`when`(resumes.findById(javaResume.id)).thenReturn(Optional.of(javaResume))
    Mockito.`when`(resumes.findById(androidResume.id)).thenReturn(Optional.of(androidResume))
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(javaJob, androidJob))

    val javaMatches = service().matchesForMe(javaResume.id)
    val androidMatches = service().matchesForMe(androidResume.id)

    assertEquals(javaJob.id, javaMatches[0].job.id)
    assertEquals(100, javaMatches[0].score)
    assertEquals(androidJob.id, androidMatches[0].job.id)
    assertEquals(100, androidMatches[0].score)
  }

  @Test
  fun matchesForMeWithWrongOwnerResumeIs404() {
    authenticate()
    val someoneElses = Resume(UUID.randomUUID(), "u/r.pdf", "resume.pdf", "pdf", 100)
    someoneElses.status = "READY"
    someoneElses.parsedText = "Java developer."
    // Only the resume lookup is stubbed: the 404 throws before the profile
    // or the jobs are ever read, so stubbing them would be unused.
    Mockito.`when`(resumes.findById(someoneElses.id)).thenReturn(Optional.of(someoneElses))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().matchesForMe(someoneElses.id)
    }
    assertEquals(404, ex.statusCode.value())
  }

  @Test
  fun matchesForMeWithMissingResumeIs404() {
    authenticate()
    val missing = UUID.randomUUID()
    Mockito.`when`(resumes.findById(missing)).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().matchesForMe(missing)
    }
    assertEquals(404, ex.statusCode.value())
  }

  @Test
  fun matchesForMeWithNotReadyResumeIs400() {
    authenticate()
    val uploaded = Resume(USER_ID, "u/r.pdf", "resume.pdf", "pdf", 100)
    // Default status is UPLOADED, not READY.
    Mockito.`when`(resumes.findById(uploaded.id)).thenReturn(Optional.of(uploaded))

    assertThrows(ResumeNotReadyException::class.java) {
      service().matchesForMe(uploaded.id)
    }
  }

  @Test
  fun matchesForMeWithoutResumeIdUsesLatestReadyResume() {
    authenticate()
    val javaJob = jobWithSkills(
      "Senior Software Engineer, Backend (Java)",
      listOf("Java", "Spring Boot")
    )
    val androidJob = jobWithSkills("Senior Android Engineer", listOf("Kotlin", "Android"))
    val p = profile("Reston, VA", "Remote", 100000, 200000)
    // List is newest-first: the Android resume is the latest READY one.
    val latest = readyResume("Kotlin Android developer, Jetpack Compose.")
    val older = readyResume("Java Spring Boot developer.")
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(p))
    Mockito.`when`(resumes.findByUserIdOrderByCreatedAtDesc(USER_ID))
      .thenReturn(mutableListOf(latest, older))
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(javaJob, androidJob))

    val matches = service().matchesForMe(null)

    assertEquals(
      androidJob.id, matches[0].job.id,
      "no resumeId must still use the latest READY resume"
    )
    assertEquals(100, matches[0].score)
  }

  @Test
  fun matchForJobWithResumeIdUsesThatResume() {
    authenticate()
    val javaJob = jobWithSkills(
      "Senior Software Engineer, Backend (Java)",
      listOf("Java", "Spring Boot")
    )
    val androidJob = jobWithSkills("Senior Android Engineer", listOf("Kotlin", "Android"))
    val p = profile("Reston, VA", "Remote", 100000, 200000)
    val androidResume = readyResume("Kotlin Android developer, Jetpack Compose.")
    Mockito.`when`(jobs.findById(androidJob.id)).thenReturn(Optional.of(androidJob))
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(p))
    Mockito.`when`(resumes.findById(androidResume.id)).thenReturn(Optional.of(androidResume))

    val match = service().matchForJob(androidJob.id, androidResume.id)

    assertEquals(100, match.score)
    assertEquals("RESUME", match.source)
    // The Java job would score 50 with this resume; the chosen resume, not
    // the latest READY one, decides the score — prove it via the Android job.
    assertEquals(androidJob.id, match.job.id)
  }

  @Test
  fun loadCandidateWithResumeIdUsesThatResumeText() {
    authenticate()
    val androidResume = readyResume("Kotlin Android developer, Jetpack Compose.")
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty())
    Mockito.`when`(resumes.findById(androidResume.id)).thenReturn(Optional.of(androidResume))

    val candidate = service().loadCandidate(USER_ID, androidResume.id)

    assertEquals("RESUME", candidate.source)
    assertTrue(candidate.text.contains("kotlin"))
    assertTrue(candidate.text.contains("android"))
  }
}
