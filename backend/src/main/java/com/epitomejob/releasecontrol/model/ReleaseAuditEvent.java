package com.epitomejob.releasecontrol.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events", schema = "release_control")
public class ReleaseAuditEvent {
  @Id
  private UUID id;

  @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_type", nullable = false, length = 24)
  private String actorType;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "from_state", length = 32)
  private String fromState;

  @Column(name = "to_state", length = 32)
  private String toState;

  @Column(name = "source_sha", nullable = false, length = 64)
  private String sourceSha;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  @Column(name = "reason", length = 1000)
  private String reason;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected ReleaseAuditEvent() {}

  public ReleaseAuditEvent(UUID releaseId, UUID actorId, String actorType,
                           String eventType, String toState, String sourceSha,
                           String correlationId, String reason) {
    this.id = UUID.randomUUID();
    this.releaseId = releaseId;
    this.actorId = actorId;
    this.actorType = actorType;
    this.eventType = eventType;
    this.toState = toState;
    this.sourceSha = sourceSha;
    this.correlationId = correlationId;
    this.reason = reason;
    this.occurredAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getReleaseId() { return releaseId; }
  public String getEventType() { return eventType; }
  public String getToState() { return toState; }
  public Instant getOccurredAt() { return occurredAt; }
}
