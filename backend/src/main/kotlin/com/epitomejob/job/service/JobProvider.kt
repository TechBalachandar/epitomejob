package com.epitomejob.job.service

import com.epitomejob.job.model.Job
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Job source (W2 contract §8). Search uses the same filters as GET /jobs:
 * query = substring over title/company/summary, location = substring over
 * location, workType = exact, sponsorsH1b = boolean, source = exact match
 * against the job's source, case-insensitive (W6 contract §2d).
 */
interface JobProvider {
  fun search(
    query: String?,
    location: String?,
    workType: String?,
    sponsorsH1b: Boolean?,
    source: String?,
    pageable: Pageable
  ): Page<Job>
}
