package com.epitomejob.resume.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "resumes", schema = "resume")
class Resume(
  @field:Column(nullable = false)
  var userId: UUID,

  @field:Column(nullable = false)
  var blobPath: String,

  @field:Column(nullable = false)
  var fileName: String,

  @field:Column(nullable = false)
  var fileType: String,

  @field:Column(nullable = false)
  var fileSize: Int
) {
  @field:Id
  var id: UUID = UUID.randomUUID()

  /** Human label, e.g. "Android Developer" (W6 contract §2b). Nullable in the
   * DB; the API falls back to the file name (see ResumeResponse). */
  @field:Column(length = 80)
  var label: String? = null

  @field:Column(nullable = false)
  var status: String = AppConstants.Resume.STATUS_UPLOADED

  @field:Column(columnDefinition = "TEXT")
  var parsedText: String? = null

  @field:Column(nullable = false)
  var createdAt: Instant = Instant.now()
}
