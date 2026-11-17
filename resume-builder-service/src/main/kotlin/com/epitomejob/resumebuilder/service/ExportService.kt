package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.constants.AppConstants
import com.fasterxml.jackson.databind.JsonNode
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream

/**
 * File export from a document's state.
 * PDF: rendered natively with PDFBox 3 (no HTML engine). OpenHTMLToPDF 1.0.10
 * was compiled against PDFBox 2 and throws NoSuchFieldError on PDFBox 3
 * (PDType1Font.COURIER_BOLD_OBLIQUE was removed in 3.x), so the HTML
 * template is not used for PDF — the content is drawn section by section,
 * mirroring exportDocx, and the templateId only validates the template
 * exists (same section order for every template).
 * DOCX: Apache POI XWPF builds the document from the state, section by section.
 */
@Service
class ExportService(private val templates: TemplateService) {

  fun exportPdf(templateId: String, state: JsonNode): ByteArray {
    // Validates the template exists (404 for an unknown id), same as before.
    templates.getTemplate(templateId)
    try {
      PDDocument().use { doc ->
        val renderer = PdfRenderer(doc)
        renderer.render(state)
        ByteArrayOutputStream().use { out ->
          doc.save(out)
          return out.toByteArray()
        }
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

  /**
   * Draws the resume state as PDF text, section by section (same order as
   * exportDocx). ASCII separators only — the standard-14 Helvetica font
   * has no em-dash / bullet glyphs in WinAnsiEncoding.
   */
  private class PdfRenderer(private val doc: PDDocument) {
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val plain = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val margin = 50f
    private val pageWidth = PDRectangle.A4.width
    private val textWidth = pageWidth - 2 * margin
    private var page = PDPage(PDRectangle.A4)
    private var content = PDPageContentStream(doc, page)
    private var y = 780f

    init {
      doc.addPage(page)
    }

    fun render(state: JsonNode) {
      val contact = state.path("contact")
      heading(contact.path("fullName").asText(""), 20f, 26f)
      y -= 4f
      line(joinNonEmpty(" | ", contact.path("email").asText(""),
          contact.path("phone").asText(""), contact.path("linkedin").asText(""),
          contact.path("location").asText("")), plain, 11f, 15f)
      y -= 8f
      val summary = state.path("summary").asText("")
      if (summary.isNotBlank()) {
        heading("Summary", 14f, 19f)
        line(summary, plain, 11f, 15f)
        y -= 8f
      }
      val experience = state.path("experience")
      if (experience.isArray && !experience.isEmpty) {
        heading("Experience", 14f, 19f)
        for (job in experience) {
          heading(job.path("title").asText("") + " - "
              + job.path("company").asText(""), 12f, 16f)
          line(joinNonEmpty(" - ", job.path("startDate").asText(""),
              job.path("endDate").asText("")), plain, 11f, 15f)
          val bullets = job.path("bullets")
          if (bullets.isArray) {
            for (bullet in bullets) {
              line("- " + bullet.asText(""), plain, 11f, 15f)
            }
          }
        }
        y -= 8f
      }
      val education = state.path("education")
      if (education.isArray && !education.isEmpty) {
        heading("Education", 14f, 19f)
        for (item in education) {
          line(joinNonEmpty(", ", item.path("degree").asText(""),
              item.path("school").asText(""), item.path("year").asText("")),
              plain, 11f, 15f)
        }
        y -= 8f
      }
      val skills = state.path("skills")
      if (skills.isArray && !skills.isEmpty) {
        heading("Skills", 14f, 19f)
        val joined = StringBuilder()
        for (skill in skills) {
          if (joined.isNotEmpty()) {
            joined.append(", ")
          }
          joined.append(skill.asText(""))
        }
        line(joined.toString(), plain, 11f, 15f)
      }
      content.close()
    }

    private fun heading(text: String, size: Float, leading: Float) {
      if (text.isNotBlank()) {
        line(text, bold, size, leading)
      }
    }

    private fun line(text: String, font: PDType1Font, size: Float, leading: Float) {
      if (text.isBlank()) {
        return
      }
      for (wrapped in wrap(text, font, size)) {
        ensureSpace(leading)
        content.beginText()
        content.setFont(font, size)
        content.newLineAtOffset(margin, y)
        content.showText(wrapped)
        content.endText()
        y -= leading
      }
    }

    private fun wrap(text: String, font: PDType1Font, size: Float): List<String> {
      val lines = mutableListOf<String>()
      var current = StringBuilder()
      for (word in text.split(" ")) {
        val trial = if (current.isEmpty()) word else "$current $word"
        val width = font.getStringWidth(trial) / 1000f * size
        if (width > textWidth && current.isNotEmpty()) {
          lines.add(current.toString())
          current = StringBuilder(word)
        } else {
          current = StringBuilder(trial)
        }
      }
      if (current.isNotEmpty()) {
        lines.add(current.toString())
      }
      return lines
    }

    private fun ensureSpace(needed: Float) {
      if (y - needed < 50f) {
        content.close()
        page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        content = PDPageContentStream(doc, page)
        y = 780f
      }
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
}
