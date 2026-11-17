package com.epitomejob.resumebuilder.common.constants

/**
 * Single home for every literal that more than one class needs, or that must
 * never drift between two spellings (routes, statuses, headers, messages).
 * Rule: if a string appears in two places, it lives here. Annotation values
 * may reference these constants directly — they are compile-time constants.
 * (Mirrors the monolith's com.epitomejob.common.constants.AppConstants;
 * this service is standalone, so it carries its own copy.)
 */
object AppConstants {

  /** HTTP / tracing. */
  object Http {
    const val AUTHORIZATION_HEADER = "Authorization"
    const val BEARER_PREFIX = "Bearer "
    const val CONTENT_DISPOSITION_HEADER = "Content-Disposition"
  }

  /** Security: JWT claims, token rules. Claim names match the monolith. */
  object Security {
    const val CLAIM_TYPE = "type"
    const val TOKEN_TYPE_REFRESH = "refresh"
  }

  /** Resume Builder routes (also the gateway Path predicate). */
  object Routes {
    const val BASE = "/resume-builder"
    const val TEMPLATES = "/templates"
    const val PARSE = "/parse"
    const val DOCUMENTS = "/documents"
  }

  /** Templates shipped as classpath resources under templates/. */
  object Templates {
    const val MODERN_ID = "modern"
    const val CLASSIC_ID = "classic"
    const val MINIMAL_ID = "minimal"
    val ALL_IDS = listOf(MODERN_ID, CLASSIC_ID, MINIMAL_ID)
    const val RESOURCE_PREFIX = "templates/"
    const val RESOURCE_SUFFIX = ".html"
  }

  /** Upload / parse limits (same 10 MB ceiling as the monolith's resumes). */
  object Parse {
    const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
    const val PDF_EXTENSION = ".pdf"
    const val DOC_EXTENSION = ".doc"
    const val DOCX_EXTENSION = ".docx"
    const val MAX_PARSED_TEXT_CHARS = 20_000
  }

  /** Export formats (?format= on POST /documents/{id}/export). */
  object Export {
    const val FORMAT_PDF = "pdf"
    const val FORMAT_DOCX = "docx"
    const val PDF_CONTENT_TYPE = "application/pdf"
    const val DOCX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  }

  /** Downstream calls (agent-brain copilot proxy + monolith resume upload). */
  object Downstream {
    const val COPILOT_PATH = "/resume-builder/copilot"
    const val RESUMES_PATH = "/resumes"
    const val MULTIPART_FILE_FIELD = "file"
    const val MULTIPART_LABEL_FIELD = "label"
  }

  /** Error messages returned to clients (never leak internals). */
  object Errors {
    const val NOT_AUTHENTICATED = "Not authenticated"
    const val INVALID_OR_EXPIRED_TOKEN = "Invalid or expired token"
    const val REFRESH_TOKEN_NOT_ALLOWED = "Refresh tokens cannot be used as API credentials"
    const val NO_FILE_UPLOADED = "No file uploaded"
    const val FILE_TOO_LARGE = "File is too large (max 10 MB)"
    const val UNSUPPORTED_FILE_TYPE = "Only PDF, DOC and DOCX files are accepted"
    const val PARSE_FAILED = "Could not read the uploaded file"
    const val DOCUMENT_NOT_FOUND = "Document not found"
    const val TEMPLATE_NOT_FOUND = "Template not found"
    const val INVALID_TEMPLATE_ID = "templateId is required"
    const val INVALID_STATE = "state is required"
    const val INVALID_LABEL = "label is required (1-80 chars)"
    const val INVALID_FORMAT = "format must be pdf or docx"
    const val AGENT_UNAVAILABLE_MESSAGE = "The agent-brain is temporarily unavailable. Please try again."
    const val BACKEND_UNAVAILABLE_MESSAGE = "The backend is temporarily unavailable. Please try again."
    const val EXPORT_FAILED = "Could not generate the export file"
  }
}
