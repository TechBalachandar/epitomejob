package com.epitomejob.resumebuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ExportService service = new ExportService(new TemplateService(mapper));

  private ObjectNode state() {
    ObjectNode state = mapper.createObjectNode();
    ObjectNode contact = mapper.createObjectNode();
    contact.put("fullName", "Ada Lovelace");
    contact.put("email", "ada@example.com");
    contact.put("phone", "555-1234");
    contact.put("linkedin", "");
    contact.put("location", "London");
    state.set("contact", contact);
    state.put("summary", "Analytical engine pioneer.");
    ObjectNode job = mapper.createObjectNode();
    job.put("title", "Senior Engineer");
    job.put("company", "Analytical Engines Ltd");
    job.put("startDate", "Jan 2020");
    job.put("endDate", "Present");
    job.set("bullets", mapper.createArrayNode().add("Designed the first algorithm"));
    state.set("experience", mapper.createArrayNode().add(job));
    ObjectNode edu = mapper.createObjectNode();
    edu.put("degree", "BSc Mathematics");
    edu.put("school", "University of London");
    edu.put("year", "2015");
    state.set("education", mapper.createArrayNode().add(edu));
    state.set("skills", mapper.createArrayNode().add("Java").add("Spring Boot"));
    state.put("rawText", "");
    return state;
  }

  @Test
  void pdfExportStartsWithPdfMagic() {
    byte[] pdf = service.exportPdf("modern", state());
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    assertThat(pdf.length).isGreaterThan(500);
  }

  @Test
  void pdfExportWorksForEveryTemplate() {
    for (String templateId : new String[]{"modern", "classic", "minimal"}) {
      byte[] pdf = service.exportPdf(templateId, state());
      assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
  }

  @Test
  void docxExportIsAReadableWordDocument() throws Exception {
    byte[] docx = service.exportDocx("modern", state());
    assertThat(new String(docx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
      String text = doc.getParagraphs().stream()
          .map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
      assertThat(text).contains("Ada Lovelace", "Senior Engineer",
          "Analytical Engines Ltd", "Designed the first algorithm", "BSc Mathematics");
    }
  }
}
