package com.epitomejob.matching.dto

import com.epitomejob.job.dto.JobDto

/** Match shape, exact field names per the W2 contract §3. */
data class MatchDto(
  val job: JobDto,
  val score: Int,
  val reasons: List<MatchReasonDto>,
  val gaps: List<String>,
  val source: String
)
