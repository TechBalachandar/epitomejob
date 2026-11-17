package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.constants.AppConstants
import com.epitomejob.resumebuilder.common.exception.AgentUnavailableException
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileResponse
import com.epitomejob.resumebuilder.model.ResumeDocument
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Save-to-profile: renders the document as PDF and creates a Resume through
 * the monolith's existing upload endpoint (POST /resumes, multipart with
 * `file` + `label` — see backend/.../resume/controller/
 * ResumeController.java), so the file lands in the W6 multi-resume list.
 *
 * PDF, not DOCX: the monolith accepts PDF only in its current form
 * (AppConstants.Errors.PDF_ONLY — "DOCX comes in Phase 3"), so the upload
 * is the PDF rendering of the document. DOCX stays a download-only export
 * (POST .../export?format=docx). The monolith validates the label
 * (1–80 chars) and parses the PDF into READY status.
 */
@Service
class SaveToProfileService(
  // Constructor parameter name must stay `javaBackendRestClient`: it matches
  // the RestClient bean name, which is how Spring picks it over the
  // agentBrainRestClient bean.
  private val javaBackendRestClient: RestClient,
  private val export: ExportService,
  private val mapper: ObjectMapper
) {

  fun saveToProfile(doc: ResumeDocument, state: JsonNode,
      label: String?, authorization: String): SaveToProfileResponse {
    val cleanLabel = label?.trim()
    if (cleanLabel == null || cleanLabel.isEmpty() || cleanLabel.length > 80) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_LABEL)
    }
    val pdf = export.exportPdf(doc.templateId, state)
    val fileName = slug(doc.title) + ".pdf"
    val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
    body.add(AppConstants.Downstream.MULTIPART_FILE_FIELD, object : ByteArrayResource(pdf) {
      override fun getFilename(): String {
        return fileName
      }
    })
    body.add(AppConstants.Downstream.MULTIPART_LABEL_FIELD, cleanLabel)
    try {
      val responseBody = javaBackendRestClient.post()
          .uri(AppConstants.Downstream.RESUMES_PATH)
          .header(AppConstants.Http.AUTHORIZATION_HEADER, authorization)
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(body)
          .retrieve()
          .body(String::class.java)
      val json = mapper.readTree(responseBody)
      val resumeId = UUID.fromString(json.path("id").asText())
      return SaveToProfileResponse(resumeId, cleanLabel)
    } catch (ex: HttpStatusCodeException) {
      // The monolith's status + body (e.g. its PDF-only 400) pass through.
      throw ResponseStatusException(ex.statusCode, ex.responseBodyAsString)
    } catch (ex: ResourceAccessException) {
      throw AgentUnavailableException(AppConstants.Errors.BACKEND_UNAVAILABLE_MESSAGE, ex)
    } catch (ex: ResponseStatusException) {
      throw ex
    } catch (ex: Exception) {
      throw ResponseStatusException(HttpStatus.BAD_GATEWAY,
          AppConstants.Errors.BACKEND_UNAVAILABLE_MESSAGE, ex)
    }
  }

  private fun slug(title: String): String {
    val slug = title.lowercase().replace("[^a-z0-9]+".toRegex(), "-").replace("^-|-\$".toRegex(), "")
    return if (slug.isEmpty()) "resume" else slug
  }
}
