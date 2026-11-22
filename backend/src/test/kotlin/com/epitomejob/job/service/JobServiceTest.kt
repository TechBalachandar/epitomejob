package com.epitomejob.job.service

import com.epitomejob.job.dto.JobDto
import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class JobServiceTest {

  @Mock
  private lateinit var jobs: JobRepository

  @Mock
  private lateinit var provider: JobProvider

  private fun job(
    title: String,
    company: String,
    location: String,
    workType: String,
    sponsors: Boolean,
    postedAt: String
  ): Job {
    return Job(
      title, company, location, workType, 120000, 160000, sponsors,
      listOf("Java"), "Summary.", "Backend", Instant.parse(postedAt)
    )
  }

  @Test
  fun searchDelegatesToProviderAndMapsToDto() {
    val j = job(
      "Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
      "2026-11-10T09:00:00Z"
    )
    Mockito.`when`(provider.search("backend", null, null, null, null, PageRequest.of(0, 20)))
      .thenReturn(PageImpl(listOf(j), PageRequest.of(0, 20), 1))
    val service = JobService(jobs, provider)

    val page: Page<JobDto> = service.search("backend", null, null, null, null, PageRequest.of(0, 20))

    assertEquals(1L, page.totalElements)
    assertEquals("Backend Engineer", page.content[0].title)
    assertEquals("SEED", page.content[0].source, "a job built without a source defaults to SEED")
    assertEquals(null, page.content[0].sourceUrl)
  }

  @Test
  fun searchMapsSourceAndSourceUrlToDto() {
    val j = job(
      "Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
      "2026-11-10T09:00:00Z"
    )
    j.source = "LINKEDIN"
    j.sourceUrl = "https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer"
    Mockito.`when`(provider.search("android", null, null, null, null, PageRequest.of(0, 20)))
      .thenReturn(PageImpl(listOf(j), PageRequest.of(0, 20), 1))
    val service = JobService(jobs, provider)

    val page: Page<JobDto> = service.search("android", null, null, null, null, PageRequest.of(0, 20))

    assertEquals("LINKEDIN", page.content[0].source)
    assertEquals(
      "https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer",
      page.content[0].sourceUrl
    )
  }

  @Test
  fun searchPassesSourceToProvider() {
    Mockito.`when`(provider.search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20)))
      .thenReturn(Page.empty(PageRequest.of(0, 20)))
    val service = JobService(jobs, provider)

    service.search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20))

    verify(provider).search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20))
  }

  @Test
  fun searchFallsBackToSeededJobsWhenProviderThrows() {
    val seeded = job(
      "Android Developer", "Northwind", "Vienna, VA", "Onsite", true,
      "2026-11-11T09:00:00Z"
    )
    Mockito.`when`(provider.search("android", null, null, null, null, PageRequest.of(0, 20)))
      .thenThrow(IllegalStateException("Adzuna request failed"))
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(seeded))
    val service = JobService(jobs, provider)

    val page: Page<JobDto> = service.search("android", null, null, null, null, PageRequest.of(0, 20))

    assertEquals(1L, page.totalElements)
    assertEquals("Android Developer", page.content[0].title)
  }

  @Test
  fun jobSourceValidationRejectsUnknownSourceAndBadUrl() {
    val j = job(
      "Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
      "2026-11-10T09:00:00Z"
    )
    val badSource = assertThrows(ResponseStatusException::class.java) { j.source = "FACEBOOK" }
    assertEquals(400, badSource.statusCode.value())
    val badUrl = assertThrows(ResponseStatusException::class.java) {
      j.sourceUrl = "ftp://example.com/jobs"
    }
    assertEquals(400, badUrl.statusCode.value())
  }

  @Test
  fun getJobMissingIs404() {
    val id = UUID.randomUUID()
    Mockito.`when`(jobs.findById(id)).thenReturn(Optional.empty())
    val service = JobService(jobs, provider)

    val ex = assertThrows(ResponseStatusException::class.java) { service.getJob(id) }
    assertEquals(404, ex.statusCode.value())
  }

  @Test
  fun seedProviderFiltersAndSortsByPostedAtDesc() {
    val oldJob = job(
      "Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
      "2026-11-02T09:00:00Z"
    )
    val newJob = job(
      "Backend Engineer", "Leidos", "Remote (US)", "Remote", false,
      "2026-11-10T09:00:00Z"
    )
    val other = job(
      "Android Developer", "Northwind", "Vienna, VA", "Onsite", true,
      "2026-11-11T09:00:00Z"
    )
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(oldJob, newJob, other))
    val seed = SeedJobProvider(jobs)

    val page: Page<Job> = seed.search("backend", null, null, null, null, PageRequest.of(0, 20))

    assertEquals(2L, page.totalElements)
    assertEquals(newJob.id, page.content[0].id)

    val remote = seed.search(null, "Remote", null, null, null, PageRequest.of(0, 20))
    assertEquals(1L, remote.totalElements)

    val sponsored = seed.search(null, null, null, true, null, PageRequest.of(0, 20))
    assertEquals(2L, sponsored.totalElements)

    val paged = seed.search(null, null, null, null, null, PageRequest.of(1, 2))
    assertEquals(3L, paged.totalElements)
    assertEquals(1, paged.content.size)
    assertTrue(paged.totalPages == 2)
  }

  @Test
  fun seedProviderFiltersBySourceCaseInsensitively() {
    val linkedin = job(
      "Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
      "2026-11-10T09:00:00Z"
    )
    linkedin.source = "LINKEDIN"
    val indeed = job(
      "Backend Engineer", "Leidos", "Remote (US)", "Remote", false,
      "2026-11-09T09:00:00Z"
    )
    indeed.source = "INDEED"
    Mockito.`when`(jobs.findAll()).thenReturn(listOf(linkedin, indeed))
    val seed = SeedJobProvider(jobs)

    val exact = seed.search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20))
    assertEquals(1L, exact.totalElements)
    assertEquals(linkedin.id, exact.content[0].id)

    val lower = seed.search(null, null, null, null, "linkedin", PageRequest.of(0, 20))
    assertEquals(1L, lower.totalElements, "source filter is case-insensitive")

    val all = seed.search(null, null, null, null, null, PageRequest.of(0, 20))
    assertEquals(2L, all.totalElements, "no source filter returns every job")
  }

  @Test
  fun adzunaProviderThrowsNamingMissingEnvVars() {
    val missing = AdzunaJobProvider({ _: String -> null }, { _: String -> "{}" }, ObjectMapper())
    val ex = assertThrows(IllegalStateException::class.java) {
      missing.search(null, null, null, null, null, PageRequest.of(0, 20))
    }
    assertTrue(ex.message?.contains("ADZUNA_APP_ID") ?: false)
    assertTrue(ex.message?.contains("ADZUNA_APP_KEY") ?: false)

    // The W2 stub is gone: with env vars present the live provider calls the
    // fetcher instead of throwing UnsupportedOperationException.
    val present = AdzunaJobProvider(
      { _: String -> "set" },
      { _: String -> "{\"count\":0,\"results\":[]}" },
      ObjectMapper()
    )
    val page = present.search(null, null, null, null, null, PageRequest.of(0, 20))
    assertEquals(0L, page.totalElements)
  }
}
