package com.epitomejob.resumebuilder.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One editable resume document. {@code state} is the full resume state as a
 * JSON string (see README §State) — the service (Jackson) is the only
 * reader/writer, so TEXT is enough and the migration stays portable.
 */
@Entity
@Table(name = "documents", schema = "resume_builder")
public class ResumeDocument {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 50)
  private String templateId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String state;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  protected ResumeDocument() {}

  public ResumeDocument(UUID userId, String title, String templateId, String state) {
    this.userId = userId;
    this.title = title;
    this.templateId = templateId;
    this.state = state;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getTemplateId() { return templateId; }
  public void setTemplateId(String templateId) { this.templateId = templateId; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
