package com.epitomejob.resumebuilder.common.constants;

import java.util.List;

/**
 * Single home for every literal that more than one class needs, or that must
 * never drift between two spellings (routes, statuses, headers, messages).
 * Rule: if a string appears in two places, it lives here. Annotation values
 * may reference these constants directly — they are compile-time constants.
 * (Mirrors the monolith's com.epitomejob.common.constants.AppConstants;
 * this service is standalone, so it carries its own copy.)
 */
public final class AppConstants {

  private AppConstants() {}

  /** HTTP / tracing. */
  public static final class Http {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private Http() {}
  }

  /** Security: JWT claims, token rules. Claim names match the monolith. */
  public static final class Security {
    public static final String CLAIM_TYPE = "type";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    private Security() {}
  }

  /** Resume Builder routes (also the gateway Path predicate). */
  public static final class Routes {
    public static final String BASE = "/resume-builder";
    public static final String TEMPLATES = "/templates";
    public static final String PARSE = "/parse";
    public static final String DOCUMENTS = "/documents";
    private Routes() {}
  }

  /** Templates shipped as classpath resources under templates/. */
  public static final class Templates {
    public static final String MODERN_ID = "modern";
    public static final String CLASSIC_ID = "classic";
    public static final String MINIMAL_ID = "minimal";
    public static final List<String> ALL_IDS = List.of(MODERN_ID, CLASSIC_ID, MINIMAL_ID);
    public static final String RESOURCE_PREFIX = "templates/";
    public static final String RESOURCE_SUFFIX = ".html";
    private Templates() {}
  }

  /** Upload / parse limits (same 10 MB ceiling as the monolith's resumes). */
  public static final class Parse {
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final String PDF_EXTENSION = ".pdf";
    public static final String DOC_EXTENSION = ".doc";
    public static final String DOCX_EXTENSION = ".docx";
    public static final int MAX_PARSED_TEXT_CHARS = 20_000;
    private Parse() {}
  }

  /** Export formats (?format= on POST /documents/{id}/export). */
  public static final class Export {
    public static final String FORMAT_PDF = "pdf";
    public static final String FORMAT_DOCX = "docx";
    public static final String PDF_CONTENT_TYPE = "application/pdf";
    public static final String DOCX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private Export() {}
  }

  /** Downstream calls (agent-brain copilot proxy + monolith resume upload). */
  public static final class Downstream {
    public static final String COPILOT_PATH = "/resume-builder/copilot";
    public static final String RESUMES_PATH = "/resumes";
    public static final String MULTIPART_FILE_FIELD = "file";
    public static final String MULTIPART_LABEL_FIELD = "label";
    private Downstream() {}
  }

  /** Error messages returned to clients (never leak internals). */
  public static final class Errors {
    public static final String NOT_AUTHENTICATED = "Not authenticated";
    public static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired token";
    public static final String REFRESH_TOKEN_NOT_ALLOWED = "Refresh tokens cannot be used as API credentials";
    public static final String NO_FILE_UPLOADED = "No file uploaded";
    public static final String FILE_TOO_LARGE = "File is too large (max 10 MB)";
    public static final String UNSUPPORTED_FILE_TYPE = "Only PDF, DOC and DOCX files are accepted";
    public static final String PARSE_FAILED = "Could not read the uploaded file";
    public static final String DOCUMENT_NOT_FOUND = "Document not found";
    public static final String TEMPLATE_NOT_FOUND = "Template not found";
    public static final String INVALID_TEMPLATE_ID = "templateId is required";
    public static final String INVALID_STATE = "state is required";
    public static final String INVALID_LABEL = "label is required (1-80 chars)";
    public static final String INVALID_FORMAT = "format must be pdf or docx";
    public static final String AGENT_UNAVAILABLE_MESSAGE = "The agent-brain is temporarily unavailable. Please try again.";
    public static final String BACKEND_UNAVAILABLE_MESSAGE = "The backend is temporarily unavailable. Please try again.";
    public static final String EXPORT_FAILED = "Could not generate the export file";
    private Errors() {}
  }
}
