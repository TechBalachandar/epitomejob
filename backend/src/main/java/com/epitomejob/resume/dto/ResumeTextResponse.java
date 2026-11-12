package com.epitomejob.resume.dto;

import com.epitomejob.resume.model.Resume;

import java.util.UUID;

/** GET /resumes/{id}/text (W3 contract §3): the parsed text, owner-only. */
public record ResumeTextResponse(UUID resumeId, String text) {

  public static ResumeTextResponse from(Resume r) {
    return new ResumeTextResponse(r.getId(), r.getParsedText());
  }
}
