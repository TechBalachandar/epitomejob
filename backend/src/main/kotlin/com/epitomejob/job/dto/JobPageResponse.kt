package com.epitomejob.job.dto

import org.springframework.data.domain.Page
import java.util.List

/** Paged jobs, with the contract §2 field names (page, not Spring's number). */
data class JobPageResponse(
  val content: List<JobDto>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
  val totalPages: Int
) {
  companion object {
    fun from(page: Page<JobDto>): JobPageResponse {
      return JobPageResponse(
        page.content, page.number, page.size,
        page.totalElements, page.totalPages
      )
    }
  }
}
