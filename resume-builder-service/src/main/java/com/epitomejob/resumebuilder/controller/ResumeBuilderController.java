package com.epitomejob.resumebuilder.controller;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.CreateDocumentRequest;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.DocumentResponse;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.ParseResponse;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileRequest;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileResponse;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.UpdateDocumentRequest;
import com.epitomejob.resumebuilder.model.ResumeDocument;
import com.epitomejob.resumebuilder.service.CopilotProxyService;
import com.epitomejob.resumebuilder.service.ExportService;
import com.epitomejob.resumebuilder.service.ResumeDocumentService;
import com.epitomejob.resumebuilder.service.ResumeParseService;
import com.epitomejob.resumebuilder.service.SaveToProfileService;
import com.epitomejob.resumebuilder.service.TemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * All Resume Builder endpoints. The gateway validates the JWT; this
 * controller re-checks ownership on every document through
 * ResumeDocumentService (owner-only lookups). SecurityUtil is only used
 * for the copilot user id — document access never trusts a bare id.
 */
@RestController
@RequestMapping(AppConstants.Routes.BASE)
public class ResumeBuilderController {

  private final TemplateService templates;
  private final ResumeParseService parseService;
  private final ResumeDocumentService documents;
  private final CopilotProxyService copilot;
  private final ExportService export;
  private final SaveToProfileService saveToProfile;
  private final ObjectMapper mapper;

  public ResumeBuilderController(TemplateService templates, ResumeParseService parseService,
      ResumeDocumentService documents, CopilotProxyService copilot, ExportService export,
      SaveToProfileService saveToProfile, ObjectMapper mapper) {
    this.templates = templates;
    this.parseService = parseService;
    this.documents = documents;
    this.copilot = copilot;
    this.export = export;
    this.saveToProfile = saveToProfile;
    this.mapper = mapper;
  }

  @GetMapping(AppConstants.Routes.TEMPLATES)
  public List<TemplateDto> templates() {
    return templates.listTemplates();
  }

  @PostMapping(value = AppConstants.Routes.PARSE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ParseResponse parse(@RequestParam("file") MultipartFile file) {
    return parseService.parse(file);
  }

  @PostMapping(AppConstants.Routes.DOCUMENTS)
  public ResponseEntity<DocumentResponse> createDocument(@RequestBody CreateDocumentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(documents.create(request));
  }

  @GetMapping(AppConstants.Routes.DOCUMENTS)
  public List<DocumentResponse> listDocuments() {
    return documents.listMine();
  }

  @GetMapping(AppConstants.Routes.DOCUMENTS + "/{id}")
  public DocumentResponse getDocument(@PathVariable UUID id) {
    return documents.get(id);
  }

  @PutMapping(AppConstants.Routes.DOCUMENTS + "/{id}")
  public DocumentResponse updateDocument(@PathVariable UUID id,
      @RequestBody UpdateDocumentRequest request) {
    return documents.update(id, request);
  }

  @PostMapping(AppConstants.Routes.DOCUMENTS + "/{id}/copilot")
  public ResponseEntity<String> copilot(@PathVariable UUID id,
      @RequestBody(required = false) JsonNode body,
      @RequestHeader(AppConstants.Http.AUTHORIZATION_HEADER) String authorization) {
    ResumeDocument doc = documents.ownedDocument(id);
    JsonNode state = readState(doc);
    return copilot.copilot(id, state, body, authorization);
  }

  @PostMapping(AppConstants.Routes.DOCUMENTS + "/{id}/export")
  public ResponseEntity<byte[]> export(@PathVariable UUID id,
      @RequestParam(defaultValue = AppConstants.Export.FORMAT_PDF) String format) {
    ResumeDocument doc = documents.ownedDocument(id);
    JsonNode state = readState(doc);
    byte[] bytes;
    String contentType;
    String extension;
    if (AppConstants.Export.FORMAT_PDF.equalsIgnoreCase(format)) {
      bytes = export.exportPdf(doc.getTemplateId(), state);
      contentType = AppConstants.Export.PDF_CONTENT_TYPE;
      extension = AppConstants.Export.FORMAT_PDF;
    } else if (AppConstants.Export.FORMAT_DOCX.equalsIgnoreCase(format)) {
      bytes = export.exportDocx(doc.getTemplateId(), state);
      contentType = AppConstants.Export.DOCX_CONTENT_TYPE;
      extension = AppConstants.Export.FORMAT_DOCX;
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_FORMAT);
    }
    String fileName = slug(doc.getTitle()) + "." + extension;
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .body(bytes);
  }

  @PostMapping(AppConstants.Routes.DOCUMENTS + "/{id}/save-to-profile")
  public SaveToProfileResponse saveToProfile(@PathVariable UUID id,
      @RequestBody SaveToProfileRequest request,
      @RequestHeader(AppConstants.Http.AUTHORIZATION_HEADER) String authorization) {
    ResumeDocument doc = documents.ownedDocument(id);
    JsonNode state = readState(doc);
    return saveToProfile.saveToProfile(doc, state, request.label(), authorization);
  }

  private JsonNode readState(ResumeDocument doc) {
    try {
      return mapper.readTree(doc.getState());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          AppConstants.Errors.EXPORT_FAILED, ex);
    }
  }

  private static String slug(String title) {
    String slug = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return slug.isEmpty() ? "resume" : slug;
  }
}
