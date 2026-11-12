package com.epitomejob.resume.dto;

import com.epitomejob.resume.model.Resume;

import java.time.Instant;
import java.util.UUID;

/**
 * What the resume endpoints return. The storage path and the parsed text are
 * internal — clients get the metadata and the status only.
 */
public record ResumeResponse(
    UUID id,
    String fileName,
    String fileType,
    int fileSize,
    String status,
    Instant createdAt) {

  public static ResumeResponse from(Resume r) {
    return new ResumeResponse(r.getId(), r.getFileName(), r.getFileType(),
        r.getFileSize(), r.getStatus(), r.getCreatedAt());
  }
}
