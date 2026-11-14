package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;

/**
 * File export from a document's state.
 * PDF: OpenHTMLToPDF renders the filled template HTML (see the PDFBox 2-vs-3
 * note in pom.xml — the export tests are the proof). DOCX: Apache POI XWPF
 * builds the document from the state, section by section.
 */
@Service
public class ExportService {

  private final TemplateService templates;

  public ExportService(TemplateService templates) {
    this.templates = templates;
  }

  public byte[] exportPdf(String templateId, JsonNode state) {
    String html = templates.renderHtml(templateId, state);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.withHtmlContent(html, null);
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          AppConstants.Errors.EXPORT_FAILED, ex);
    }
  }

  public byte[] exportDocx(String templateId, JsonNode state) {
    // templateId is accepted for API symmetry (the DOCX layout follows the
    // same section order as every template); the content comes from state.
    try (XWPFDocument doc = new XWPFDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      JsonNode contact = state.path("contact");
      addHeading(doc, contact.path("fullName").asText(""), 20, true);
      addLine(doc, joinNonEmpty(" | ", contact.path("email").asText(""),
          contact.path("phone").asText(""), contact.path("linkedin").asText(""),
          contact.path("location").asText("")), false);
      String summary = state.path("summary").asText("");
      if (!summary.isBlank()) {
        addHeading(doc, "Summary", 14, true);
        addLine(doc, summary, false);
      }
      JsonNode experience = state.path("experience");
      if (experience.isArray() && !experience.isEmpty()) {
        addHeading(doc, "Experience", 14, true);
        for (JsonNode job : experience) {
          addHeading(doc, job.path("title").asText("") + " — "
              + job.path("company").asText(""), 12, true);
          addLine(doc, joinNonEmpty(" – ", job.path("startDate").asText(""),
              job.path("endDate").asText("")), false);
          JsonNode bullets = job.path("bullets");
          if (bullets.isArray()) {
            for (JsonNode bullet : bullets) {
              addLine(doc, "• " + bullet.asText(""), false);
            }
          }
        }
      }
      JsonNode education = state.path("education");
      if (education.isArray() && !education.isEmpty()) {
        addHeading(doc, "Education", 14, true);
        for (JsonNode item : education) {
          addLine(doc, joinNonEmpty(", ", item.path("degree").asText(""),
              item.path("school").asText(""), item.path("year").asText("")), false);
        }
      }
      JsonNode skills = state.path("skills");
      if (skills.isArray() && !skills.isEmpty()) {
        addHeading(doc, "Skills", 14, true);
        StringBuilder joined = new StringBuilder();
        for (JsonNode skill : skills) {
          if (!joined.isEmpty()) {
            joined.append(", ");
          }
          joined.append(skill.asText(""));
        }
        addLine(doc, joined.toString(), false);
      }
      doc.write(out);
      return out.toByteArray();
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          AppConstants.Errors.EXPORT_FAILED, ex);
    }
  }

  private static void addHeading(XWPFDocument doc, String text, int fontSize, boolean bold) {
    XWPFParagraph paragraph = doc.createParagraph();
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setFontSize(fontSize);
    run.setBold(bold);
  }

  private static void addLine(XWPFDocument doc, String text, boolean bold) {
    XWPFParagraph paragraph = doc.createParagraph();
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(bold);
  }

  private static String joinNonEmpty(String separator, String... parts) {
    StringBuilder out = new StringBuilder();
    for (String part : parts) {
      if (part != null && !part.isBlank()) {
        if (!out.isEmpty()) {
          out.append(separator);
        }
        out.append(part.trim());
      }
    }
    return out.toString();
  }
}
