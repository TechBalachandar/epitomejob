package com.epitomejob.interview.controller

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.interview.dto.AnswerDto
import com.epitomejob.interview.dto.AnswerRequest
import com.epitomejob.interview.dto.CreateSessionRequest
import com.epitomejob.interview.dto.QuestionDto
import com.epitomejob.interview.dto.SessionDto
import com.epitomejob.interview.service.InterviewService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/interviews")
class InterviewController(private val service: InterviewService) {

  @GetMapping("/questions")
  fun questions(@RequestParam(required = false) jobId: UUID?): List<QuestionDto> {
    if (jobId == null) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.JOB_ID_REQUIRED)
    }
    return service.questionsForJob(jobId)
  }

  @PostMapping("/sessions")
  fun createSession(@Valid @RequestBody req: CreateSessionRequest): ResponseEntity<SessionDto> {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createSession(req.jobId))
  }

  @PostMapping("/sessions/{id}/answers")
  fun submitAnswer(@PathVariable id: UUID, @Valid @RequestBody req: AnswerRequest): AnswerDto {
    return service.submitAnswer(id, req.questionId, req.answerText)
  }

  @GetMapping("/sessions/me")
  fun listMine(): List<SessionDto> {
    return service.listMine()
  }

  @GetMapping("/sessions/{id}")
  fun getSession(@PathVariable id: UUID): SessionDto {
    return service.getSession(id)
  }
}
