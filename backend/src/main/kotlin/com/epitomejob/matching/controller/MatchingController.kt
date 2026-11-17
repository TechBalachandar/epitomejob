package com.epitomejob.matching.controller

import com.epitomejob.matching.dto.MatchDto
import com.epitomejob.matching.service.MatchingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/matches")
class MatchingController(private val service: MatchingService) {

  @GetMapping("/me")
  fun matchesForMe(@RequestParam(required = false) resumeId: UUID?): List<MatchDto> {
    return service.matchesForMe(resumeId)
  }

  @GetMapping("/{jobId}")
  fun matchForJob(
    @PathVariable jobId: UUID,
    @RequestParam(required = false) resumeId: UUID?
  ): MatchDto {
    return service.matchForJob(jobId, resumeId)
  }
}
