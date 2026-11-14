package com.epitomejob.resume.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resumes", schema = "resume")
public class Resume {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String blobPath;

  @Column(nullable = false)
  private String fileName;

  /** Human label, e.g. "Android Developer" (W6 contract §2b). Nullable in the
   * DB; the API falls back to the file name (see ResumeResponse). */
  @Column(length = 80)
  private String label;

  @Column(nullable = false)
  private String fileType;

  @Column(nullable = false)
  private int fileSize;

  @Column(nullable = false)
  private String status = AppConstants.Resume.STATUS_UPLOADED;

  @Column(columnDefinition = "TEXT")
  private String parsedText;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  protected Resume() {}

  public Resume(UUID userId, String blobPath, String fileName, String fileType, int fileSize) {
    this.userId = userId;
    this.blobPath = blobPath;
    this.fileName = fileName;
    this.fileType = fileType;
    this.fileSize = fileSize;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getBlobPath() { return blobPath; }
  public String getFileName() { return fileName; }
  public String getLabel() { return label; }
  public void setLabel(String l) { this.label = l; }
  public String getFileType() { return fileType; }
  public int getFileSize() { return fileSize; }
  public String getStatus() { return status; }
  public void setStatus(String s) { this.status = s; }
  public String getParsedText() { return parsedText; }
  public void setParsedText(String t) { this.parsedText = t; }
  public Instant getCreatedAt() { return createdAt; }
}
