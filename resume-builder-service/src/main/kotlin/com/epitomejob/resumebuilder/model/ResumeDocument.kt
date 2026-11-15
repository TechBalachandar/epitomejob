package com.epitomejob.resumebuilder.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One editable resume document. `state` is the full resume state as a
 * JSON string (see README §State) — the service (Jackson) is the only
 * reader/writer, so TEXT is enough and the migration stays portable.
 *
 * Regular class, not a data class — data-class `equals`/`hashCode` breaks
 * lazy JPA relations. The kotlin-jpa plugin generates the no-arg
 * constructor.
 */
@Entity
@Table(name = "documents", schema = "resume_builder")
class ResumeDocument(
  @Column(nullable = false)
  var userId: UUID,

  @Column(nullable = false, length = 200)
  var title: String,

  @Column(nullable = false, length = 50)
  var templateId: String,

  @Column(nullable = false, columnDefinition = "TEXT")
  var state: String
) {

  @Id
  var id: UUID = UUID.randomUUID()

  @Column(nullable = false)
  var createdAt: Instant = Instant.now()

  @Column(nullable = false)
  var updatedAt: Instant = Instant.now()
}
