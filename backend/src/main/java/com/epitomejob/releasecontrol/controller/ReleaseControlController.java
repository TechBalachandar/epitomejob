package com.epitomejob.releasecontrol.controller;

import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.releasecontrol.dto.CreateReleaseRequest;
import com.epitomejob.releasecontrol.dto.ReleaseResponse;
import com.epitomejob.releasecontrol.model.ReleaseAuditEvent;
import com.epitomejob.releasecontrol.service.ReleaseControlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/release-control/releases")
public class ReleaseControlController {
  private final ReleaseControlService service;

  public ReleaseControlController(ReleaseControlService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReleaseResponse create(@Valid @RequestBody CreateReleaseRequest request,
                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    if (!idempotencyKey.equals(request.idempotencyKey())) {
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Idempotency-Key header must match request idempotencyKey");
    }
    return ReleaseResponse.from(service.create(request, SecurityUtil.currentUserId(), correlationId));
  }

  @GetMapping("/{id}")
  public ReleaseResponse get(@PathVariable UUID id) {
    return ReleaseResponse.from(service.get(id, SecurityUtil.currentUserId()));
  }

  @GetMapping("/{id}/events")
  public List<ReleaseEventResponse> timeline(@PathVariable UUID id) {
    return service.timeline(id, SecurityUtil.currentUserId()).stream()
        .map(ReleaseEventResponse::from).toList();
  }

  public record ReleaseEventResponse(UUID id, String eventType, String state, java.time.Instant occurredAt) {
    static ReleaseEventResponse from(ReleaseAuditEvent event) {
      return new ReleaseEventResponse(event.getId(), event.getEventType(), event.getToState(), event.getOccurredAt());
    }
  }
}
