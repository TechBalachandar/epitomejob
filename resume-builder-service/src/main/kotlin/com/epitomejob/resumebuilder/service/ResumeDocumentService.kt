package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.constants.AppConstants
import com.epitomejob.resumebuilder.common.util.SecurityUtil
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.CreateDocumentRequest
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.DocumentResponse
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.UpdateDocumentRequest
import com.epitomejob.resumebuilder.model.ResumeDocument
import com.epitomejob.resumebuilder.repository.ResumeDocumentRepository
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Document CRUD. Ownership is enforced on every call: lookups are
 * findByIdAndUserId, so another user's document is a 404, never a 403
 * that confirms it exists.
 */
@Service
class ResumeDocumentService(
  private val documents: ResumeDocumentRepository,
  private val templates: TemplateService,
  private val parser: ResumeStateParser,
  private val mapper: ObjectMapper
) {

  @Transactional
  fun create(request: CreateDocumentRequest?): DocumentResponse {
    if (request == null || request.templateId == null || request.templateId.isBlank()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_TEMPLATE_ID)
    }
    templates.getTemplate(request.templateId)
    val state = request.state ?: parser.emptyState()
    val title = if (request.title == null || request.title.isBlank()) {
      "Untitled Resume"
    } else {
      request.title.trim()
    }
    val doc = ResumeDocument(SecurityUtil.currentUserId(), title,
        request.templateId, toJson(state))
    val saved = documents.save(doc)
    return DocumentResponse.from(saved, state)
  }

  @Transactional(readOnly = true)
  fun get(id: UUID): DocumentResponse {
    val doc = owned(id)
    return DocumentResponse.from(doc, fromJson(doc.state))
  }

  @Transactional(readOnly = true)
  fun listMine(): List<DocumentResponse> {
    return documents.findByUserIdOrderByUpdatedAtDesc(SecurityUtil.currentUserId())
        .map { doc -> DocumentResponse.from(doc, fromJson(doc.state)) }
  }

  /**
   * Full state replace: the stored state becomes exactly the request state.
   * Title/templateId update only when provided (non-blank).
   */
  @Transactional
  fun update(id: UUID, request: UpdateDocumentRequest?): DocumentResponse {
    if (request == null || request.state == null) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_STATE)
    }
    val doc = owned(id)
    if (request.templateId != null && request.templateId.isNotBlank()) {
      templates.getTemplate(request.templateId)
      doc.templateId = request.templateId
    }
    if (request.title != null && request.title.isNotBlank()) {
      doc.title = request.title.trim()
    }
    doc.state = toJson(request.state)
    doc.updatedAt = Instant.now()
    val saved = documents.save(doc)
    return DocumentResponse.from(saved, request.state)
  }

  /** Owner-only lookup used by copilot/export/save-to-profile as well. */
  @Transactional(readOnly = true)
  fun ownedDocument(id: UUID): ResumeDocument {
    return owned(id)
  }

  private fun owned(id: UUID): ResumeDocument {
    return documents.findByIdAndUserId(id, SecurityUtil.currentUserId())
        .orElseThrow {
          ResponseStatusException(HttpStatus.NOT_FOUND,
              AppConstants.Errors.DOCUMENT_NOT_FOUND)
        }
  }

  private fun toJson(state: JsonNode): String {
    try {
      return mapper.writeValueAsString(state)
    } catch (ex: JsonProcessingException) {
      throw IllegalArgumentException(AppConstants.Errors.INVALID_STATE, ex)
    }
  }

  private fun fromJson(json: String): JsonNode {
    try {
      return mapper.readTree(json)
    } catch (ex: JsonProcessingException) {
      throw IllegalStateException("Stored document state is not valid JSON", ex)
    }
  }
}
