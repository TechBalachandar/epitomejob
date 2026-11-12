package com.epitomejob.approval;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.concurrent.Executors;
/** Phase 0 SSE: the approval inbox stream. Polling every 5s is the fallback (see SampAgent). */
@RestController @RequestMapping("/approvals")
public class ApprovalSseController {
  private final ApprovalService service;
  public ApprovalSseController(ApprovalService service) { this.service = service; }
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(60_000L);
    Executors.newSingleThreadExecutor().submit(() -> {
      try {
        emitter.send(SseEmitter.event().name("pending").data(service.pendingMine()));
        emitter.complete();
      } catch (IOException e) { emitter.completeWithError(e); }
    });
    return emitter;
  }
}
