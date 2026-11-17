package com.epitomejob.resumebuilder.repository

import com.epitomejob.resumebuilder.model.ResumeDocument
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/**
 * Ownership is enforced in the query itself: every lookup is by
 * (id, userId), so a document belonging to someone else is simply not
 * found (404), never leaked.
 */
interface ResumeDocumentRepository : JpaRepository<ResumeDocument, UUID> {

  fun findByIdAndUserId(id: UUID, userId: UUID): Optional<ResumeDocument>

  fun findByUserIdOrderByUpdatedAtDesc(userId: UUID): List<ResumeDocument>
}
