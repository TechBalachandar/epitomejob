package com.epitomejob.job.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.job.model.Job
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Live Adzuna job provider. Calls the Adzuna Jobs API (US) and maps each
 * result to a [Job]. Bean only when
 * `epitomejob.jobs.provider=adzuna`.
 *
 * Requires the `ADZUNA_APP_ID` and `ADZUNA_APP_KEY` env vars;
 * on first use it throws [IllegalStateException] naming any that are
 * missing. The key is never logged: error messages never include the request
 * URL (which carries the credentials as query params).
 *
 * Adzuna pages are 1-based; Spring's [Pageable] is 0-based, so the
 * Adzuna page is `pageable.getPageNumber() + 1` and
 * `results_per_page` comes from the page size (capped at 50).
 * Adzuna's `what`/`where` handle the query/location server-side.
 * The `source` filter is honoured before any call (a non-Adzuna source
 * returns an empty page), and `sponsorsH1b=true` returns an empty page
 * because Adzuna cannot confirm H-1B sponsorship. `workType` is
 * filtered client-side on the fetched page: Adzuna reports contract type
 * (full/part-time, contract), not the app's Remote/Hybrid/Onsite
 * arrangement, so the returned total is Adzuna's unfiltered count when a
 * workType filter is applied.
 */
class AdzunaJobProvider : JobProvider {

  companion object {
    const val ENV_APP_ID = "ADZUNA_APP_ID"
    const val ENV_APP_KEY = "ADZUNA_APP_KEY"

    /** Source value on every Job this provider returns. Uppercase: Job
     * validates sources against `AppConstants.Job.SOURCES`, and the
     * source filter is case-insensitive, so `source=adzuna` matches. */
    @JvmField
    val SOURCE: String = AppConstants.Job.SOURCE_ADZUNA

    private const val BASE_URL = "https://api.adzuna.com/v1/api/jobs/us/search"
    private const val MAX_RESULTS_PER_PAGE = 50

    private fun defaultFetcher(): (String) -> String? {
      val client = RestClient.create()
      return { url -> client.get().uri(url).retrieve().body(String::class.java) }
    }
  }

  private val env: (String) -> String?
  private val fetcher: (String) -> String?
  private val mapper: ObjectMapper

  constructor() : this(System::getenv, defaultFetcher(), ObjectMapper())

  /** Test seam: inject the env lookup, the HTTP fetcher (URL to response
   * body JSON), and the JSON mapper. */
  internal constructor(
    env: (String) -> String?,
    fetcher: (String) -> String?,
    mapper: ObjectMapper
  ) {
    this.env = env
    this.fetcher = fetcher
    this.mapper = mapper
  }

  override fun search(
    query: String?,
    location: String?,
    workType: String?,
    sponsorsH1b: Boolean?,
    source: String?,
    pageable: Pageable
  ): Page<Job> {
    // This provider only serves Adzuna jobs; a different source matches nothing.
    if (!source.isNullOrBlank() && !SOURCE.equals(source, ignoreCase = true)) {
      return Page.empty(pageable)
    }
    // Adzuna cannot confirm H-1B sponsorship, so a sponsorship filter
    // can never match an Adzuna job.
    if (sponsorsH1b == true) {
      return Page.empty(pageable)
    }
    val missing = ArrayList<String>()
    if (isMissing(env(ENV_APP_ID))) {
      missing.add(ENV_APP_ID)
    }
    if (isMissing(env(ENV_APP_KEY))) {
      missing.add(ENV_APP_KEY)
    }
    if (missing.isNotEmpty()) {
      throw IllegalStateException(
        "Adzuna provider selected but missing env vars: ${missing.joinToString(", ")}" +
          " — set them to use epitomejob.jobs.provider=adzuna"
      )
    }
    val page = pageable.pageNumber + 1 // Adzuna pages are 1-based.
    val size = minOf(maxOf(pageable.pageSize, 1), MAX_RESULTS_PER_PAGE)
    val url = buildUrl(env(ENV_APP_ID)!!, env(ENV_APP_KEY)!!, query, location, page, size)
    val body: String? = try {
      fetcher(url)
    } catch (e: RuntimeException) {
      // Never include the URL or the exception message: either may carry
      // the app id/key as query params. The HTTP status alone is safe.
      var detail = ""
      if (e is RestClientResponseException) {
        detail = " (HTTP ${e.statusCode.value()})"
      }
      throw IllegalStateException("Adzuna request failed$detail")
    }
    return parse(body, workType, pageable)
  }

  private fun buildUrl(
    appId: String,
    appKey: String,
    query: String?,
    location: String?,
    page: Int,
    size: Int
  ): String {
    val url = StringBuilder(BASE_URL)
      .append('/').append(page)
      .append("?app_id=").append(encode(appId))
      .append("&app_key=").append(encode(appKey))
      .append("&results_per_page=").append(size)
    if (!query.isNullOrBlank()) {
      url.append("&what=").append(encode(query.trim()))
    }
    if (!location.isNullOrBlank()) {
      url.append("&where=").append(encode(location.trim()))
    }
    return url.toString()
  }

  private fun parse(body: String?, workType: String?, pageable: Pageable): Page<Job> {
    val root: JsonNode = try {
      mapper.readTree(body)
    } catch (e: Exception) {
      throw IllegalStateException("Adzuna response could not be parsed", e)
    }
    var total: Long = if (root.path("count").isNumber) root.path("count").asLong() else 0
    val jobs = ArrayList<Job>()
    for (item in root.path("results")) {
      jobs.add(toJob(item))
    }
    if (total == 0L) {
      total = jobs.size.toLong()
    }
    var filtered: List<Job> = jobs
    if (!workType.isNullOrBlank()) {
      filtered = jobs.filter { it.workType.equals(workType.trim(), ignoreCase = true) }
    }
    return PageImpl(filtered, pageable, total)
  }

  private fun toJob(item: JsonNode): Job {
    val title = item.path("title").asText("")
    val company = item.path("company").path("display_name").asText("")
    val location = item.path("location").path("display_name").asText("")
    val description = item.path("description").asText("")
    val workType = mapWorkType(item)
    val salaryMin = intOrNull(item, "salary_min")
    val salaryMax = intOrNull(item, "salary_max")
    var role = item.path("category").path("label").asText("")
    if (role.isBlank()) {
      role = AppConstants.Job.ROLE_GENERAL
    }
    val postedAt = parseCreated(item.path("created").asText(null))
    val job = Job(
      title, company, location, workType, salaryMin, salaryMax,
      false, listOf(), description, role, postedAt
    )
    job.source = SOURCE
    val redirectUrl = item.path("redirect_url").asText(null)
    if (!redirectUrl.isNullOrBlank()
      && (redirectUrl.startsWith("http://") || redirectUrl.startsWith("https://"))) {
      job.sourceUrl = redirectUrl
    }
    return job
  }

  /**
   * Adzuna reports contract type/time (full_time, part_time, contract,
   * permanent), not the app's Remote/Hybrid/Onsite arrangement — that is
   * not in the Adzuna response, so the contract is the honest workType.
   */
  private fun mapWorkType(item: JsonNode): String {
    var raw = item.path("contract_type").asText("")
    if (raw.isBlank()) {
      raw = item.path("contract_time").asText("")
    }
    return when (raw.trim().lowercase()) {
      "part_time" -> "Part-time"
      "contract" -> "Contract"
      "permanent" -> "Permanent"
      else -> "Full-time"
    }
  }

  private fun intOrNull(item: JsonNode, field: String): Int? {
    val value = item.get(field)
    return if (value != null && value.isNumber) value.asInt() else null
  }

  private fun parseCreated(created: String?): Instant {
    if (created.isNullOrBlank()) {
      return Instant.now()
    }
    return try {
      Instant.parse(created.trim())
    } catch (e: Exception) {
      Instant.now()
    }
  }

  private fun isMissing(value: String?): Boolean {
    return value.isNullOrBlank()
  }

  private fun encode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
  }
}
