package com.epitomejob.job.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.job.dto.JobDto
import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class JobService(private val jobs: JobRepository, private val provider: JobProvider) {

  @Transactional(readOnly = true)
  fun search(
    query: String?,
    location: String?,
    workType: String?,
    sponsorsH1b: Boolean?,
    source: String?,
    pageable: Pageable
  ): Page<JobDto> {
    return provider.search(query, location, workType, sponsorsH1b, source, pageable)
      .map { JobDto.from(it) }
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
