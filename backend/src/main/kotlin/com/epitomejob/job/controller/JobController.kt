package com.epitomejob.job.controller

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.job.dto.JobDto
import com.epitomejob.job.dto.JobPageResponse
import com.epitomejob.job.service.JobService
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/jobs")
class JobController(private val service: JobService) {

  @GetMapping
  fun list(
    @RequestParam(required = false) query: String?,
    @RequestParam(required = false) location: String?,
    @RequestParam(required = false) workType: String?,
    @RequestParam(required = false) sponsorsH1b: Boolean?,
    @RequestParam(required = false) source: String?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int
  ): JobPageResponse {
    val safePage = maxOf(0, page)
    val safeSize = minOf(AppConstants.Job.MAX_PAGE_SIZE, maxOf(1, size))
    return JobPageResponse.from(
      service.search(
        query, location, workType, sponsorsH1b, source, PageRequest.of(safePage, safeSize)
      )
    )
  }

  @GetMapping("/{id}")
  fun getOne(@PathVariable id: UUID): JobDto {
    return service.getJob(id)
  }
}
