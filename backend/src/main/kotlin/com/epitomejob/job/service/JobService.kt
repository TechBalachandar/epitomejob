package com.epitomejob.job.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.job.dto.JobDto
import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class JobService(private val jobs: JobRepository, private val provider: JobProvider) {

  private val log = LoggerFactory.getLogger(JobService::class.java)

  @Transactional(readOnly = true)
  fun search(
    query: String?,
    location: String?,
    workType: String?,
    sponsorsH1b: Boolean?,
    source: String?,
    pageable: Pageable
  ): Page<JobDto> {
    val results = try {
      provider.search(query, location, workType, sponsorsH1b, source, pageable)
    } catch (e: Exception) {
      // A live provider failure (Adzuna down, or its env vars missing) must
      // never blank the Jobs page: fall back to the seeded jobs. The
      // exception message is safe to log — AdzunaJobProvider strips the
      // request URL (which carries the app id/key) from its messages.
      log.warn("Job provider search failed; falling back to seeded jobs: {}", e.message, e)
      SeedJobProvider(jobs).search(query, location, workType, sponsorsH1b, source, pageable)
    }
    return results.map { JobDto.from(it) }
  }

  @Transactional(readOnly = true)
  fun getJob(id: UUID): JobDto {
    return JobDto.from(findJob(id))
  }

  @Transactional(readOnly = true)
  fun findJob(id: UUID): Job {
    return jobs.findById(id)
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.JOB_NOT_FOUND)
      }
  }
}
