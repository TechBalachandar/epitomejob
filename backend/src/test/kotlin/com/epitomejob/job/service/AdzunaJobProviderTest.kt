package com.epitomejob.job.service

import com.epitomejob.job.model.Job
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import java.util.HashMap
import java.util.concurrent.atomic.AtomicReference

class AdzunaJobProviderTest {

  private val JSON = """
      {
        "count": 2,
        "results": [
          {
            "id": "111",
            "title": "Backend Engineer",
            "company": {"display_name": "Northwind"},
            "location": {"display_name": "Reston, VA"},
            "description": "Build APIs.",
            "redirect_url": "https://www.adzuna.com/details/111",
            "created": "2026-11-14T09:00:00Z",
            "category": {"label": "IT Jobs", "tag": "it-jobs"},
            "contract_type": "full_time",
            "salary_min": 120000,
            "salary_max": 160000
          },
          {
            "id": "222",
            "title": "Support Engineer",
            "company": {"display_name": "Acme"},
            "location": {"display_name": "Herndon, VA"},
            "description": "Help customers.",
            "redirect_url": "https://www.adzuna.com/details/222",
            "created": "2026-11-13T09:00:00Z",
            "category": {"label": "IT Jobs", "tag": "it-jobs"},
            "contract_type": "part_time"
          }
        ]
      }
      """

  private fun provider(
    env: Map<String, String>,
    fetcher: (String) -> String?
  ): AdzunaJobProvider {
    return AdzunaJobProvider(env::get, fetcher, ObjectMapper())
  }

  private fun envWithKeys(): Map<String, String> {
    val env = HashMap<String, String>()
    env[AdzunaJobProvider.ENV_APP_ID] = "test-id"
    env[AdzunaJobProvider.ENV_APP_KEY] = "test-key"
    return env
  }

  @Test
  fun missingEnvVarsNamesBoth() {
    val provider = provider(emptyMap(), { _: String -> JSON })

    val e = assertThrows(IllegalStateException::class.java) {
      provider.search(null, null, null, null, null, PageRequest.of(0, 20))
    }

    assertTrue(e.message?.contains("ADZUNA_APP_ID") ?: false, e.message)
    assertTrue(e.message?.contains("ADZUNA_APP_KEY") ?: false, e.message)
  }

  @Test
  fun missingOnlyKeyNamesOnlyKey() {
    val env = mapOf(AdzunaJobProvider.ENV_APP_ID to "test-id")
    val provider = provider(env, { _: String -> JSON })

    val e = assertThrows(IllegalStateException::class.java) {
      provider.search(null, null, null, null, null, PageRequest.of(0, 20))
    }

    assertTrue(e.message?.contains("ADZUNA_APP_KEY") ?: false, e.message)
  }

  @Test
  fun mapsAdzunaResultsToJobs() {
    val seenUrl = AtomicReference<String>()
    val provider = provider(envWithKeys(), { url ->
      seenUrl.set(url)
      JSON
    })

    val page: Page<Job> =
      provider.search("backend", "Reston", null, null, null, PageRequest.of(0, 20))

    assertEquals(2L, page.totalElements)
    assertEquals(2, page.content.size)
    val first = page.content[0]
    assertEquals("Backend Engineer", first.title)
    assertEquals("Northwind", first.company)
    assertEquals("Reston, VA", first.location)
    assertEquals("ADZUNA", first.source)
    assertEquals("https://www.adzuna.com/details/111", first.sourceUrl)
    assertEquals(120000, first.salaryMin)
    assertEquals(160000, first.salaryMax)
    assertEquals("Full-time", first.workType)
    assertEquals("IT Jobs", first.role)
    assertEquals("2026-11-14T09:00:00Z", first.postedAt.toString())
    assertEquals("Part-time", page.content[1].workType)
    assertNull(page.content[1].salaryMin, "absent salary maps to null")
    // Adzuna pages are 1-based: Spring page 0 -> Adzuna page 1.
    assertTrue(seenUrl.get()?.contains("/search/1?") ?: false, seenUrl.get())
    assertTrue(seenUrl.get()?.contains("results_per_page=20") ?: false, seenUrl.get())
    assertTrue(seenUrl.get()?.contains("what=backend") ?: false, seenUrl.get())
    assertTrue(seenUrl.get()?.contains("where=Reston") ?: false, seenUrl.get())
  }

  @Test
  fun secondPageRequestsAdzunaPageTwo() {
    val seenUrl = AtomicReference<String>()
    val provider = provider(envWithKeys(), { url ->
      seenUrl.set(url)
      JSON
    })

    provider.search(null, null, null, null, null, PageRequest.of(1, 20))

    assertTrue(seenUrl.get()?.contains("/search/2?") ?: false, seenUrl.get())
  }

  @Test
  fun nonAdzunaSourceReturnsEmptyWithoutCallingApi() {
    val seenUrl = AtomicReference<String>()
    val provider = provider(envWithKeys(), { url ->
      seenUrl.set(url)
      JSON
    })

    val page: Page<Job> =
      provider.search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20))

    assertEquals(0L, page.totalElements)
    assertNull(seenUrl.get(), "no API call for a non-Adzuna source")
  }

  @Test
  fun sponsorsH1bFilterReturnsEmptyWithoutCallingApi() {
    val seenUrl = AtomicReference<String>()
    val provider = provider(envWithKeys(), { url ->
      seenUrl.set(url)
      JSON
    })

    val page: Page<Job> =
      provider.search(null, null, null, true, null, PageRequest.of(0, 20))

    assertEquals(0L, page.totalElements)
    assertNull(seenUrl.get(), "no API call when sponsorship is required")
  }

  @Test
  fun workTypeFilterKeepsOnlyMatchingJobs() {
    val provider = provider(envWithKeys(), { _: String -> JSON })

    val page: Page<Job> =
      provider.search(null, null, "Part-time", null, null, PageRequest.of(0, 20))

    assertEquals(1, page.content.size)
    assertEquals("Support Engineer", page.content[0].title)
  }
}
