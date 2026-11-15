package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.constants.AppConstants
import com.fasterxml.jackson.databind.JsonNode
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream

/**
 * File export from a document's state.
 * PDF: OpenHTMLToPDF renders the filled template HTML (see the PDFBox 2-vs-3
 * note in pom.xml — the export tests are the proof). DOCX: Apache POI XWPF
 * builds the document from the state, section by section.
 */
@Service
class ExportService(private val templates: TemplateService) {

  fun exportPdf(templateId: String, state: JsonNode): ByteArray {
    val html = templates.renderHtml(templateId, state)
    try {
      ByteArrayOutputStream().use { out ->
        val builder = PdfRendererBuilder()
        builder.withHtmlContent(html, null)
        builder.toStream(out)
        builder.run()
        return out.toByteArray()
      }
    } catch (ex: Exception) {
      throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          AppConstants.Errors.EXPORT_FAILED, ex)
    }
  }

  fun exportDocx(templateId: String, state: JsonNode): ByteArray {
    // templateId is accepted for API symmetry (the DOCX layout follows the
    // same section order as every template); the content comes from state.
    try {
      XWPFDocument().use { doc ->
        ByteArrayOutputStream().use { out ->
          val contact = state.path("contact")
          addHeading(doc, contact.path("fullName").asText(""), 20, true)
          addLine(doc, joinNonEmpty(" | ", contact.path("email").asText(""),
              contact.path("phone").asText(""), contact.path("linkedin").asText(""),
              contact.path("location").asText("")), false)
          val summary = state.path("summary").asText("")
          if (summary.isNotBlank()) {
            addHeading(doc, "Summary", 14, true)
            addLine(doc, summary, false)
          }
          val experience = state.path("experience")
          if (experience.isArray && !experience.isEmpty) {
            addHeading(doc, "Experience", 14, true)
            for (job in experience) {
              addHeading(doc, job.path("title").asText("") + " — "
                  + job.path("company").asText(""), 12, true)
              addLine(doc, joinNonEmpty(" – ", job.path("startDate").asText(""),
                  job.path("endDate").asText("")), false)
              val bullets = job.path("bullets")
              if (bullets.isArray) {
                for (bullet in bullets) {
                  addLine(doc, "• " + bullet.asText(""), false)
                }
              }
            }
          }
          val education = state.path("education")
          if (education.isArray && !education.isEmpty) {
            addHeading(doc, "Education", 14, true)
            for (item in education) {
              addLine(doc, joinNonEmpty(", ", item.path("degree").asText(""),
                  item.path("school").asText(""), item.path("year").asText("")), false)
            }
          }
          val skills = state.path("skills")
          if (skills.isArray && !skills.isEmpty) {
            addHeading(doc, "Skills", 14, true)
            val joined = StringBuilder()
            for (skill in skills) {
              if (joined.isNotEmpty()) {
                joined.append(", ")
              }
              joined.append(skill.asText(""))
            }
            addLine(doc, joined.toString(), false)
          }
          doc.write(out)
          return out.toByteArray()
        }
      }
    } catch (ex: Exception) {
      throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          AppConstants.Errors.EXPORT_FAILED, ex)
    }
  }

  private fun addHeading(doc: XWPFDocument, text: String, fontSize: Int, bold: Boolean) {
    val paragraph = doc.createParagraph()
    val run = paragraph.createRun()
    run.setText(text)
    run.setFontSize(fontSize)
    run.setBold(bold)
  }

  private fun addLine(doc: XWPFDocument, text: String, bold: Boolean) {
    val paragraph = doc.createParagraph()
    val run = paragraph.createRun()
    run.setText(text)
    run.setBold(bold)
  }

  private fun joinNonEmpty(separator: String, vararg parts: String): String {
    val out = StringBuilder()
    for (part in parts) {
      if (part.isNotBlank()) {
        if (out.isNotEmpty()) {
          out.append(separator)
        }
        out.append(part.trim())
      }
    }
    return out.toString()
  }
}
