package com.epitomejob.resume.dto

import com.epitomejob.resume.model.Resume
import java.time.Instant
import java.util.UUID

/**
 * What the resume endpoints return. The storage path and the parsed text are
 * internal — clients get the metadata and the status only.
 */
data class ResumeResponse(
  val id: UUID,
  val fileName: String,
  val fileType: String,
  val fileSize: Int,
  val status: String,
  val createdAt: Instant,
  val label: String
) {
  companion object {
    fun from(r: Resume): ResumeResponse {
      // The label is never null in the API: an unlabelled resume shows its file name.
      val label = r.label ?: r.fileName
      return ResumeResponse(r.id, r.fileName, r.fileType, r.fileSize, r.status, r.createdAt, label)
    }
  }
}
