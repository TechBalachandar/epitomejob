package com.epitomejob.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification")
public class Notification {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(nullable = false)
  private String link;

  @Column(nullable = false)
  private boolean isRead = false;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  protected Notification() {}

  public Notification(UUID userId, String type, String title, String body, String link) {
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.body = body;
    this.link = link;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getType() { return type; }
  public String getTitle() { return title; }
  public String getBody() { return body; }
  public String getLink() { return link; }
  public boolean isRead() { return isRead; }
  public void setRead(boolean read) { this.isRead = read; }
  public Instant getCreatedAt() { return createdAt; }
}
