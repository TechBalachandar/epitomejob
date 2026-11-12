package com.epitomejob.coach.controller;

import com.epitomejob.coach.dto.CoachChatRequest;
import com.epitomejob.coach.service.CoachService;
import com.epitomejob.common.constants.AppConstants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /coach/chat (W3 contract §3): forwards the message to the agent-brain
 * and returns its response verbatim. The incoming Bearer token is forwarded
 * so the agent-brain can validate it against GET /profiles/me. Requires
 * authentication (default security rules).
 */
@RestController
@RequestMapping("/coach")
public class CoachController {

  private final CoachService service;

  public CoachController(CoachService service) {
    this.service = service;
  }

  @PostMapping("/chat")
  public ResponseEntity<String> chat(@Valid @RequestBody CoachChatRequest request,
      @RequestHeader(AppConstants.Http.AUTHORIZATION_HEADER) String authorization) {
    return service.chat(request.message(), authorization);
  }
}
