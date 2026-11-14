package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import com.epitomejob.resumebuilder.common.util.SecurityUtil;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.CreateDocumentRequest;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.DocumentResponse;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.UpdateDocumentRequest;
import com.epitomejob.resumebuilder.model.ResumeDocument;
import com.epitomejob.resumebuilder.repository.ResumeDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Document CRUD. Ownership is enforced on every call: lookups are
 * findByIdAndUserId, so another user's document is a 404, never a 403
 * that confirms it exists.
 */
@Service
public class ResumeDocumentService {

  private final ResumeDocumentRepository documents;
  private final TemplateService templates;
  private final ResumeStateParser parser;
  private final ObjectMapper mapper;

  public ResumeDocumentService(ResumeDocumentRepository documents, TemplateService templates,
      ResumeStateParser parser, ObjectMapper mapper) {
    this.documents = documents;
    this.templates = templates;
    this.parser = parser;
    this.mapper = mapper;
  }

  @Transactional
  public DocumentResponse create(CreateDocumentRequest request) {
    if (request == null || request.templateId() == null || request.templateId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_TEMPLATE_ID);
    }
    templates.getTemplate(request.templateId());
    JsonNode state = request.state() == null ? parser.emptyState() : request.state();
    String title = request.title() == null || request.title().isBlank()
        ? "Untitled Resume" : request.title().trim();
    ResumeDocument doc = new ResumeDocument(SecurityUtil.currentUserId(), title,
        request.templateId(), toJson(state));
    ResumeDocument saved = documents.save(doc);
    return DocumentResponse.from(saved, state);
  }

  @Transactional(readOnly = true)
  public DocumentResponse get(UUID id) {
    ResumeDocument doc = owned(id);
    return DocumentResponse.from(doc, fromJson(doc.getState()));
  }

  @Transactional(readOnly = true)
  public List<DocumentResponse> listMine() {
    return documents.findByUserIdOrderByUpdatedAtDesc(SecurityUtil.currentUserId()).stream()
        .map(doc -> DocumentResponse.from(doc, fromJson(doc.getState())))
        .toList();
  }

  /**
   * Full state replace: the stored state becomes exactly the request state.
   * Title/templateId update only when provided (non-blank).
   */
  @Transactional
  public DocumentResponse update(UUID id, UpdateDocumentRequest request) {
    if (request == null || request.state() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_STATE);
    }
    ResumeDocument doc = owned(id);
    if (request.templateId() != null && !request.templateId().isBlank()) {
      templates.getTemplate(request.templateId());
      doc.setTemplateId(request.templateId());
    }
    if (request.title() != null && !request.title().isBlank()) {
      doc.setTitle(request.title().trim());
    }
    doc.setState(toJson(request.state()));
    doc.setUpdatedAt(Instant.now());
    ResumeDocument saved = documents.save(doc);
    return DocumentResponse.from(saved, request.state());
  }

  /** Owner-only lookup used by copilot/export/save-to-profile as well. */
  @Transactional(readOnly = true)
  public ResumeDocument ownedDocument(UUID id) {
    return owned(id);
  }

  private ResumeDocument owned(UUID id) {
    return documents.findByIdAndUserId(id, SecurityUtil.currentUserId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.DOCUMENT_NOT_FOUND));
  }

  private String toJson(JsonNode state) {
    try {
      return mapper.writeValueAsString(state);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(AppConstants.Errors.INVALID_STATE, ex);
    }
  }

  private JsonNode fromJson(String json) {
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Stored document state is not valid JSON", ex);
    }
  }
}
