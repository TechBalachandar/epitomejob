package com.epitomejob.job.service

import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Transactional

/** Default provider (W2 contract §8): reads the seeded `job.jobs` table.
 * Open so Spring can create the CGLIB proxy for @Transactional: this class
 * is instantiated by a @Bean method (no stereotype annotation), so the
 * kotlin-spring all-open plugin does not open it. */
open class SeedJobProvider(private val jobs: JobRepository) : JobProvider {

  @Transactional(readOnly = true)
  override fun search(
    query: String?,
    location: String?,
    workType: String?,
    sponsorsH1b: Boolean?,
    source: String?,
    pageable: Pageable
  ): Page<Job> {
    val filtered = jobs.findAll()
      .filter { matchesQuery(it, query) }
      .filter { matchesLocation(it, location) }
      .filter { matchesWorkType(it, workType) }
      .filter { matchesSponsorship(it, sponsorsH1b) }
      .filter { matchesSource(it, source) }
      // Newest first; postedAt is non-null, so the Java nullsLast comparator
      // is a no-op here.
      .sortedByDescending { it.postedAt }
    val total = filtered.size
    val from = minOf(pageable.offset.toInt(), total)
    val to = minOf(from + pageable.pageSize, total)
    return PageImpl(filtered.subList(from, to), pageable, total.toLong())
  }

  private fun matchesQuery(job: Job, query: String?): Boolean {
    if (query.isNullOrBlank()) {
      return true
    }
    val needle = query.lowercase()
    return contains(job.title, needle)
      || contains(job.company, needle)
      || contains(job.summary, needle)
  }

  private fun matchesLocation(job: Job, location: String?): Boolean {
    return location.isNullOrBlank() || contains(job.location, location.lowercase())
  }

  private fun matchesWorkType(job: Job, workType: String?): Boolean {
    return workType.isNullOrBlank() || job.workType.equals(workType, ignoreCase = true)
  }

  private fun matchesSponsorship(job: Job, sponsorsH1b: Boolean?): Boolean {
    return sponsorsH1b == null || job.sponsorsH1b == sponsorsH1b
  }

  private fun matchesSource(job: Job, source: String?): Boolean {
    return source.isNullOrBlank() || job.source.equals(source, ignoreCase = true)
  }

  private fun contains(haystack: String?, needle: String): Boolean {
    return haystack != null && haystack.lowercase().contains(needle)
  }
}
