package com.epitomejob.resumebuilder.controller

import com.epitomejob.resumebuilder.common.constants.AppConstants
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.CreateDocumentRequest
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.DocumentResponse
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.ParseResponse
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileRequest
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileResponse
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.UpdateDocumentRequest
import com.epitomejob.resumebuilder.model.ResumeDocument
import com.epitomejob.resumebuilder.service.CopilotProxyService
import com.epitomejob.resumebuilder.service.ExportService
import com.epitomejob.resumebuilder.service.ResumeDocumentService
import com.epitomejob.resumebuilder.service.ResumeParseService
import com.epitomejob.resumebuilder.service.SaveToProfileService
import com.epitomejob.resumebuilder.service.TemplateService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * All Resume Builder endpoints. The gateway validates the JWT; this
 * controller re-checks ownership on every document through
 * ResumeDocumentService (owner-only lookups). SecurityUtil is only used
 * for the copilot user id — document access never trusts a bare id.
 */
@RestController
@RequestMapping(AppConstants.Routes.BASE)
class ResumeBuilderController(
  private val templates: TemplateService,
  private val parseService: ResumeParseService,
  private val documents: ResumeDocumentService,
  private val copilot: CopilotProxyService,
  private val export: ExportService,
  private val saveToProfile: SaveToProfileService,
  private val mapper: ObjectMapper
) {

  @GetMapping(AppConstants.Routes.TEMPLATES)
  fun templates(): List<TemplateDto> {
    return templates.listTemplates()
  }

  @PostMapping(value = [AppConstants.Routes.PARSE], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun parse(@RequestParam("file") file: MultipartFile): ParseResponse {
    return parseService.parse(file)
  }

  @PostMapping(AppConstants.Routes.DOCUMENTS)
  fun createDocument(@RequestBody request: CreateDocumentRequest): ResponseEntity<DocumentResponse> {
    return ResponseEntity.status(HttpStatus.CREATED).body(documents.create(request))
  }

  @GetMapping(AppConstants.Routes.DOCUMENTS)
  fun listDocuments(): List<DocumentResponse> {
    return documents.listMine()
  }

  @GetMapping(AppConstants.Routes.DOCUMENTS + "/{id}")
  fun getDocument(@PathVariable id: UUID): DocumentResponse {
    return documents.get(id)
  }

  @PutMapping(AppConstants.Routes.DOCUMENTS + "/{id}")
  fun updateDocument(@PathVariable id: UUID,
      @RequestBody request: UpdateDocumentRequest): DocumentResponse {
    return documents.update(id, request)
  }

  @PostMapping(AppConstants.Routes.DOCUMENTS + "/{id}/copilot")
  fun copilot(@PathVariable id: UUID,
      @RequestBody(required = false) body: JsonNode?,
      @RequestHeader(AppConstants.Http.AUTHORIZATION_HEADER) authorization: String): ResponseEntity<String> {
    val doc = documents.ownedDocument(id)
    val state = readState(doc)
    return copilot.copilot(id, state, body, authorization)
  }

  @PostMapping(AppConstants.Routes.DOCUMENTS + "/{id}/export")
  fun export(@PathVariable id: UUID,
      @RequestParam(defaultValue = AppConstants.Export.FORMAT_PDF) format: String): ResponseEntity<ByteArray> {
    val doc = documents.ownedDocument(id)
    val state = readState(doc)
    val bytes: ByteArray
    val contentType: String
    val extension: String
    if (AppConstants.Export.FORMAT_PDF.equals(format, ignoreCase = true)) {
      bytes = export.exportPdf(doc.templateId, state)
      contentType = AppConstants.Export.PDF_CONTENT_TYPE
      extension = AppConstants.Export.FORMAT_PDF
    } else if (AppConstants.Export.FORMAT_DOCX.equals(format, ignoreCase = true)) {
      bytes = export.exportDocx(doc.templateId, state)
      contentType = AppConstants.Export.DOCX_CONTENT_TYPE
      extension = AppConstants.Export.FORMAT_DOCX
    } else {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_FORMAT)
    }
    val fileName = slug(doc.title) + "." + extension
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
        .body(bytes)
  }

  @PostMapping(AppConstants.Routes.DOCUMENTS + "/{id}/save-to-profile")
  fun saveToProfile(@PathVariable id: UUID,
      @RequestBody request: SaveToProfileRequest,
      @RequestHeader(AppConstants.Http.AUTHORIZATION_HEADER) authorization: String): SaveToProfileResponse {
    val doc = documents.ownedDocument(id)
    val state = readState(doc)
    return saveToProfile.saveToProfile(doc, state, request.label, authorization)
  }

  private fun readState(doc: ResumeDocument): JsonNode {
    try {
      return mapper.readTree(doc.state)
    } catch (ex: Exception) {
      throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          AppConstants.Errors.EXPORT_FAILED, ex)
    }
  }

  private fun slug(title: String): String {
    val slug = title.lowercase().replace("[^a-z0-9]+".toRegex(), "-").replace("^-|-\$".toRegex(), "")
    return if (slug.isEmpty()) "resume" else slug
  }
}
