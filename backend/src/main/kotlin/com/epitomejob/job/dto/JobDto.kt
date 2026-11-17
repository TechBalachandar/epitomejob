package com.epitomejob.job.dto

import com.epitomejob.job.model.Job
import java.time.Instant
import java.util.UUID

/** Job shape, exact field names per the W2 contract §1. */
data class JobDto(
  val id: UUID,
  val title: String,
  val company: String,
  val location: String,
  val workType: String,
  val salaryMin: Int?,
  val salaryMax: Int?,
  val currency: String,
  val sponsorsH1b: Boolean,
  val skills: List<String>,
  val summary: String?,
  val role: String,
  val postedAt: Instant,
  val source: String,
  val sourceUrl: String?
) {
  companion object {
    fun from(job: Job): JobDto {
      return JobDto(
        job.id, job.title, job.company, job.location,
        job.workType, job.salaryMin, job.salaryMax, job.currency,
        job.sponsorsH1b, job.skills, job.summary, job.role,
        job.postedAt, job.source, job.sourceUrl
      )
    }
  }
}
