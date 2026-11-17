package com.epitomejob.resumebuilder.dto

import com.epitomejob.resumebuilder.model.ResumeDocument
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/** DTOs for the Resume Builder API. Records throughout; validation lives in the services. */
object ResumeBuilderDtos {

  /** One template: id, name, description, HTML+CSS template, JSON layout descriptor. */
  data class TemplateDto(
    val id: String,
    val name: String,
    val description: String,
    val html: String,
    val layout: JsonNode
  )

  /** POST /parse result: the structured state plus the raw extracted text (echoed, for debugging). */
  data class ParseResponse(val state: JsonNode, val rawText: String)

  /** POST /documents body. `state` is optional — omitted means a blank document. */
  data class CreateDocumentRequest(val title: String?, val templateId: String?, val state: JsonNode?)

  /** PUT /documents/{id} body: full state replace (state is required). */
  data class UpdateDocumentRequest(val title: String?, val templateId: String?, val state: JsonNode?)

  /** What GET/POST/PUT /documents return. `state` is the stored JSON, parsed. */
  data class DocumentResponse(
    val id: UUID,
    val title: String,
    val templateId: String,
    val state: JsonNode,
    val createdAt: Instant,
    val updatedAt: Instant
  ) {
    companion object {
      @JvmStatic
      fun from(doc: ResumeDocument, state: JsonNode): DocumentResponse {
        return DocumentResponse(doc.id, doc.title, doc.templateId,
            state, doc.createdAt, doc.updatedAt)
      }
    }
  }

  /** POST /documents/{id}/save-to-profile body. */
  data class SaveToProfileRequest(val label: String?)

  /** POST /documents/{id}/save-to-profile result: the monolith resume id. */
  data class SaveToProfileResponse(val resumeId: UUID, val label: String)
}
