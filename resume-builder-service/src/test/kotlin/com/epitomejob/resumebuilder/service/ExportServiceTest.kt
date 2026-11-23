package com.epitomejob.resumebuilder.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat

class ExportServiceTest {

  private val mapper = ObjectMapper()
  private val service = ExportService(TemplateService(mapper))

  private fun state(): ObjectNode {
    val state = mapper.createObjectNode()
    val contact = mapper.createObjectNode()
    contact.put("fullName", "Ada Lovelace")
    contact.put("email", "ada@example.com")
    contact.put("phone", "555-1234")
    contact.put("linkedin", "")
    contact.put("location", "London")
    state.set<JsonNode>("contact", contact)
    state.put("summary", "Analytical engine pioneer.")
    val job = mapper.createObjectNode()
    job.put("title", "Senior Engineer")
    job.put("company", "Analytical Engines Ltd")
    job.put("startDate", "Jan 2020")
    job.put("endDate", "Present")
    job.set<JsonNode>("bullets", mapper.createArrayNode().add("Designed the first algorithm"))
    state.set<JsonNode>("experience", mapper.createArrayNode().add(job))
    val edu = mapper.createObjectNode()
    edu.put("degree", "BSc Mathematics")
    edu.put("school", "University of London")
    edu.put("year", "2015")
    state.set<JsonNode>("education", mapper.createArrayNode().add(edu))
    state.set<JsonNode>("skills", mapper.createArrayNode().add("Java").add("Spring Boot"))
    state.put("rawText", "")
    return state
  }

  @Test
  fun pdfExportStartsWithPdfMagic() {
    val pdf = service.exportPdf("modern", state())
    assertThat(String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-")
    assertThat(pdf.size).isGreaterThan(500)
  }

  @Test
  fun pdfExportWorksForEveryTemplate() {
    for (templateId in arrayOf("modern", "classic", "minimal")) {
      val pdf = service.exportPdf(templateId, state())
      assertThat(String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-")
    }
  }

  @Test
  fun pdfExportHandlesEmptyState() {
    // Blank name/summary and empty sections — exercises the renderer's
    // empty-branch paths (blank heading/line, missing sections).
    val empty = mapper.createObjectNode()
    val contact = mapper.createObjectNode()
    contact.put("fullName", "")
    contact.put("email", "")
    contact.put("phone", "")
    contact.put("linkedin", "")
    contact.put("location", "")
    empty.set<JsonNode>("contact", contact)
    empty.put("summary", "")
    empty.set<JsonNode>("experience", mapper.createArrayNode())
    empty.set<JsonNode>("education", mapper.createArrayNode())
    empty.set<JsonNode>("skills", mapper.createArrayNode())
    empty.put("rawText", "")
    val pdf = service.exportPdf("modern", empty)
    assertThat(String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-")
    assertThat(pdf.size).isGreaterThan(500)
  }

  @Test
  fun pdfExportWrapsLongTextAndPaginates() {
    // A very long summary forces word wrap; many jobs force a second page;
    // one job has no bullets array. Exercises the wrap + page-break paths.
    val big = state()
    val longSummary = ("Analytical engine pioneer with a long career in computing. "
        .repeat(20)).trim()
    big.put("summary", longSummary)
    val jobs = mapper.createArrayNode()
    for (i in 1..15) {
      val job = mapper.createObjectNode()
      job.put("title", "Engineer $i")
      job.put("company", "Company $i")
      job.put("startDate", "Jan 2020")
      job.put("endDate", "Present")
      if (i != 3) {
        job.set<JsonNode>("bullets", mapper.createArrayNode()
            .add("Did important work number $i")
            .add("Also did more work number $i"))
      }
      jobs.add(job)
    }
    big.set<JsonNode>("experience", jobs)
    val pdf = service.exportPdf("modern", big)
    assertThat(String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-")
    assertThat(pdf.size).isGreaterThan(500)
  }

  @Test
  fun docxExportKeepsCoverLetterParagraphsSeparate() {
    // B1: a cover letter stored in the summary slot (paragraphs joined
    // with "\n\n") must export as separate paragraphs — a literal "\n"
    // in a single run is not a line break in Word.
    val letter = state()
    letter.put("summary", "Dear Hiring Manager,\n\nI am excited to apply for the Senior Engineer role.\nI bring ten years of experience.\n\nSincerely,\nAda Lovelace")
    val docx = service.exportDocx("modern", letter)
    XWPFDocument(ByteArrayInputStream(docx)).use { doc ->
      val texts = doc.paragraphs.map { it.text }
      assertThat(texts).contains("Dear Hiring Manager,")
      assertThat(texts.any { it.startsWith("I am excited to apply") }).isTrue()
      assertThat(texts.any { it.startsWith("Sincerely,") }).isTrue()
      assertThat(texts.none { it.contains("\n") }).isTrue()
      val greeting = texts.indexOf("Dear Hiring Manager,")
      val body = texts.indexOfFirst { it.startsWith("I am excited to apply") }
      val closing = texts.indexOfFirst { it.startsWith("Sincerely,") }
      assertThat(greeting).isLessThan(body)
      assertThat(body).isLessThan(closing)
    }
  }

  @Test
  fun pdfExportKeepsCoverLetterParagraphsAndDoesNotFail() {
    // B1: the PDF path used to feed "\n" to WinAnsi Helvetica (missing
    // glyph at best, a thrown exception and a failed export at worst).
    // A multi-paragraph summary must export cleanly with its
    // paragraphs intact.
    val letter = state()
    letter.put("summary", "Dear Hiring Manager,\n\nI am excited to apply for the Senior Engineer role.\n\nSincerely,\nAda Lovelace")
    val pdf = service.exportPdf("modern", letter)
    assertThat(String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-")
    Loader.loadPDF(pdf).use { doc ->
      val extracted = PDFTextStripper().getText(doc)
      assertThat(extracted).contains("Dear Hiring Manager,", "Sincerely,")
      assertThat(extracted).contains("I am excited to apply for the Senior Engineer role.")
    }
  }

  @Test
  fun docxExportIsAReadableWordDocument() {
    val docx = service.exportDocx("modern", state())
    assertThat(String(docx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK")
    XWPFDocument(ByteArrayInputStream(docx)).use { doc ->
      val text = doc.paragraphs.joinToString(separator = "\n", prefix = "\n") { it.text }
      assertThat(text).contains(
        "Ada Lovelace", "Senior Engineer",
        "Analytical Engines Ltd", "Designed the first algorithm", "BSc Mathematics"
      )
    }
  }

  @Test
  fun docxExportHandlesEmptyState() {
    // Same empty state as the PDF test — exercises the DOCX export's
    // empty-section branches.
    val empty = mapper.createObjectNode()
    val contact = mapper.createObjectNode()
    contact.put("fullName", "")
    contact.put("email", "")
    contact.put("phone", "")
    contact.put("linkedin", "")
    contact.put("location", "")
    empty.set<JsonNode>("contact", contact)
    empty.put("summary", "")
    empty.set<JsonNode>("experience", mapper.createArrayNode())
    empty.set<JsonNode>("education", mapper.createArrayNode())
    empty.set<JsonNode>("skills", mapper.createArrayNode())
    empty.put("rawText", "")
    val docx = service.exportDocx("modern", empty)
    assertThat(String(docx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK")
  }
}
