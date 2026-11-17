package com.epitomejob.job.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * A seeded (or provider-supplied) job posting. Skills are stored as one TEXT
 * column, comma-separated (see [AppConstants.Job.SKILLS_SEPARATOR]);
 * no skill in the seed data contains a comma.
 */
@Entity
@Table(name = "jobs", schema = "job")
class Job(
  @field:Column(nullable = false)
  var title: String,

  @field:Column(nullable = false)
  var company: String,

  @field:Column(nullable = false)
  var location: String,

  @field:Column(nullable = false)
  var workType: String,

  var salaryMin: Int?,

  var salaryMax: Int?,

  // Explicit column name: Spring's naming rule inserts '_' before an
  // uppercase letter only when the next char is lowercase. In
  // "sponsorsH1b" the 'H' is followed by the digit '1', so Hibernate
  // expects "sponsorsh1b", but the V3 migration created "sponsors_h1b".
  @field:Column(name = "sponsors_h1b", nullable = false)
  var sponsorsH1b: Boolean,

  skills: List<String>?,

  @field:Column(columnDefinition = "TEXT")
  var summary: String?,

  @field:Column(nullable = false)
  var role: String,

  @field:Column(nullable = false)
  var postedAt: Instant
) {
  @field:Id
  var id: UUID = UUID.randomUUID()

  @field:Column(nullable = false)
  var currency: String = AppConstants.Job.DEFAULT_CURRENCY

  /** Comma-separated skills, stored as one TEXT column. */
  @field:Column(nullable = false, columnDefinition = "TEXT")
  private var skillsCsv: String = ""

  /** Where the posting was aggregated from (W6 contract §2d). Defaults to
   * SEED for rows created before V8; the setter validates the value. */
  @field:Column(nullable = false, length = 30)
  var source: String = AppConstants.Job.SOURCE_SEED
    set(value) {
      if (!AppConstants.Job.SOURCES.contains(value)) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_JOB_SOURCE)
      }
      field = value
    }

  /** Apply link back to the source portal (W6 contract §2d). Nullable; when
   * present it must start with http:// or https:// (see setter). */
  @field:Column(length = 500)
  var sourceUrl: String? = null
    set(value) {
      if (value != null && !value.startsWith("http://") && !value.startsWith("https://")) {
        throw ResponseStatusException(
          HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_JOB_SOURCE_URL
        )
      }
      field = value
    }

  @field:Column(nullable = false)
  var createdAt: Instant = Instant.now()

  /** Skills as a list; use this (not the stored comma-separated string). */
  var skills: List<String>
    get() {
      if (skillsCsv.isBlank()) {
        return emptyList()
      }
      return skillsCsv.split(AppConstants.Job.SKILLS_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    }
    set(value) {
      skillsCsv = value.joinToString(AppConstants.Job.SKILLS_SEPARATOR)
    }

  init {
    this.skills = skills ?: emptyList()
  }
}
