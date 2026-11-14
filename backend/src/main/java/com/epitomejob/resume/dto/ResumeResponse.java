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
    Instant createdAt,
    String label) {

  public static ResumeResponse from(Resume r) {
    // The label is never null in the API: an unlabelled resume shows its file name.
    String label = r.getLabel() == null ? r.getFileName() : r.getLabel();
    return new ResumeResponse(r.getId(), r.getFileName(), r.getFileType(),
        r.getFileSize(), r.getStatus(), r.getCreatedAt(), label);
  }
}
