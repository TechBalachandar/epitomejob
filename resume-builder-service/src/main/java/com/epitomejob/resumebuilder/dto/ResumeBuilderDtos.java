package com.epitomejob.resumebuilder.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.epitomejob.resumebuilder.model.ResumeDocument;

import java.time.Instant;
import java.util.UUID;

/** DTOs for the Resume Builder API. Records throughout; validation lives in the services. */
public final class ResumeBuilderDtos {

  private ResumeBuilderDtos() {}

  /** One template: id, name, description, HTML+CSS template, JSON layout descriptor. */
  public record TemplateDto(String id, String name, String description,
                            String html, JsonNode layout) {}

  /** POST /parse result: the structured state plus the raw extracted text (echoed, for debugging). */
  public record ParseResponse(JsonNode state, String rawText) {}

  /** POST /documents body. {@code state} is optional — omitted means a blank document. */
  public record CreateDocumentRequest(String title, String templateId, JsonNode state) {}

  /** PUT /documents/{id} body: full state replace (state is required). */
  public record UpdateDocumentRequest(String title, String templateId, JsonNode state) {}

  /** What GET/POST/PUT /documents return. {@code state} is the stored JSON, parsed. */
  public record DocumentResponse(UUID id, String title, String templateId,
                                 JsonNode state, Instant createdAt, Instant updatedAt) {
    public static DocumentResponse from(ResumeDocument doc, JsonNode state) {
      return new DocumentResponse(doc.getId(), doc.getTitle(), doc.getTemplateId(),
          state, doc.getCreatedAt(), doc.getUpdatedAt());
    }
  }

  /** POST /documents/{id}/save-to-profile body. */
  public record SaveToProfileRequest(String label) {}

  /** POST /documents/{id}/save-to-profile result: the monolith resume id. */
  public record SaveToProfileResponse(UUID resumeId, String label) {}
}
