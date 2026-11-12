package com.epitomejob.interview.controller;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.interview.dto.AnswerDto;
import com.epitomejob.interview.dto.AnswerRequest;
import com.epitomejob.interview.dto.CreateSessionRequest;
import com.epitomejob.interview.dto.QuestionDto;
import com.epitomejob.interview.dto.SessionDto;
import com.epitomejob.interview.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/interviews")
public class InterviewController {

  private final InterviewService service;

  public InterviewController(InterviewService service) {
    this.service = service;
  }

  @GetMapping("/questions")
  public List<QuestionDto> questions(@RequestParam(required = false) UUID jobId) {
    if (jobId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.JOB_ID_REQUIRED);
    }
    return service.questionsForJob(jobId);
  }

  @PostMapping("/sessions")
  public ResponseEntity<SessionDto> createSession(@Valid @RequestBody CreateSessionRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createSession(req.jobId()));
  }

  @PostMapping("/sessions/{id}/answers")
  public AnswerDto submitAnswer(@PathVariable UUID id, @Valid @RequestBody AnswerRequest req) {
    return service.submitAnswer(id, req.questionId(), req.answerText());
  }

  @GetMapping("/sessions/me")
  public List<SessionDto> listMine() {
    return service.listMine();
  }

  @GetMapping("/sessions/{id}")
  public SessionDto getSession(@PathVariable UUID id) {
    return service.getSession(id);
  }
}
