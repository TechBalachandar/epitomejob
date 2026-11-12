package com.epitomejob.approval.controller;

import com.epitomejob.approval.dto.ApprovalResponse;
import com.epitomejob.approval.service.ApprovalService;
import com.epitomejob.common.constants.AppConstants;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * Phase 0 SSE: the approval inbox stream. Sends the current pending list once
 * and completes — clients reconnect (or poll every 5s, see SampAgent) for
 * updates. Sending happens on the request thread; no executor is created
 * per request.
 */
@RestController
@RequestMapping("/approvals")
public class ApprovalSseController {

  private final ApprovalService service;

  public ApprovalSseController(ApprovalService service) {
    this.service = service;
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(AppConstants.Approval.SSE_TIMEOUT_MS);
    try {
      List<ApprovalResponse> pending =
          service.pendingMine().stream().map(ApprovalResponse::from).toList();
      emitter.send(SseEmitter.event().name(AppConstants.Approval.SSE_EVENT_PENDING).data(pending));
      emitter.complete();
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
    return emitter;
  }
}
