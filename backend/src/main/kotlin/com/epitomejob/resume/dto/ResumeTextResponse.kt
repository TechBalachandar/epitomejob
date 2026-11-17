package com.epitomejob.resume.dto

import com.epitomejob.resume.model.Resume
import java.util.UUID

/** GET /resumes/{id}/text (W3 contract §3): the parsed text, owner-only. */
data class ResumeTextResponse(val resumeId: UUID, val text: String?) {
  companion object {
    fun from(r: Resume): ResumeTextResponse {
      return ResumeTextResponse(r.id, r.parsedText)
    }
  }
}
