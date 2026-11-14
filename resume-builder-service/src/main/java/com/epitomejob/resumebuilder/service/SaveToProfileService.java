package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import com.epitomejob.resumebuilder.common.exception.AgentUnavailableException;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileResponse;
import com.epitomejob.resumebuilder.model.ResumeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Save-to-profile: renders the document as PDF and creates a Resume through
 * the monolith's existing upload endpoint (POST /resumes, multipart with
 * {@code file} + {@code label} — see backend/.../resume/controller/
 * ResumeController.java), so the file lands in the W6 multi-resume list.
 *
 * <p>PDF, not DOCX: the monolith accepts PDF only in its current form
 * (AppConstants.Errors.PDF_ONLY — "DOCX comes in Phase 3"), so the upload
 * is the PDF rendering of the document. DOCX stays a download-only export
 * (POST .../export?format=docx). The monolith validates the label
 * (1–80 chars) and parses the PDF into READY status.
 */
@Service
public class SaveToProfileService {

  private final RestClient javaBackend;
  private final ExportService export;
  private final ObjectMapper mapper;

  public SaveToProfileService(RestClient javaBackendRestClient, ExportService export,
      ObjectMapper mapper) {
    this.javaBackend = javaBackendRestClient;
    this.export = export;
    this.mapper = mapper;
  }

  public SaveToProfileResponse saveToProfile(ResumeDocument doc, JsonNode state,
      String label, String authorization) {
    String cleanLabel = label == null ? null : label.trim();
    if (cleanLabel == null || cleanLabel.isEmpty() || cleanLabel.length() > 80) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_LABEL);
    }
    byte[] pdf = export.exportPdf(doc.getTemplateId(), state);
    String fileName = slug(doc.getTitle()) + ".pdf";
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(AppConstants.Downstream.MULTIPART_FILE_FIELD, new ByteArrayResource(pdf) {
      @Override
      public String getFilename() {
        return fileName;
      }
    });
    body.add(AppConstants.Downstream.MULTIPART_LABEL_FIELD, cleanLabel);
    try {
      String responseBody = javaBackend.post()
          .uri(AppConstants.Downstream.RESUMES_PATH)
          .header(AppConstants.Http.AUTHORIZATION_HEADER, authorization)
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(body)
          .retrieve()
          .body(String.class);
      JsonNode json = mapper.readTree(responseBody);
      UUID resumeId = UUID.fromString(json.path("id").asText());
      return new SaveToProfileResponse(resumeId, cleanLabel);
    } catch (HttpStatusCodeException ex) {
      // The monolith's status + body (e.g. its PDF-only 400) pass through.
      throw new ResponseStatusException(ex.getStatusCode(), ex.getResponseBodyAsString());
    } catch (ResourceAccessException ex) {
      throw new AgentUnavailableException(AppConstants.Errors.BACKEND_UNAVAILABLE_MESSAGE, ex);
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          AppConstants.Errors.BACKEND_UNAVAILABLE_MESSAGE, ex);
    }
  }

  private static String slug(String title) {
    String slug = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return slug.isEmpty() ? "resume" : slug;
  }
}
