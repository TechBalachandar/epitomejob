package com.epitomejob.resumebuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeParseServiceTest {

  private final ResumeParseService service = new ResumeParseService(new ResumeStateParser(new ObjectMapper()));

  /** Tiny PDF fixture, built in-test with PDFBox (no binary fixtures in the tree). */
  public static byte[] pdfFixture() throws Exception {
    try (PDDocument doc = new PDDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
        stream.setFont(PDType1Font.HELVETICA, 12);
        stream.beginText();
        stream.newLineAtOffset(50, 700);
        for (String line : new String[]{
            "Ada Lovelace", "ada@example.com", "SUMMARY",
            "Analytical engine pioneer.", "EXPERIENCE",
            "Senior Engineer at Analytical Engines Ltd Jan 2020 – Present",
            "• Designed the first algorithm"}) {
          stream.showText(line);
          stream.newLineAtOffset(0, -16);
        }
        stream.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  /** Tiny DOCX fixture, built in-test with POI XWPF. */
  public static byte[] docxFixture() throws Exception {
    try (XWPFDocument doc = new XWPFDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (String line : new String[]{"Ada Lovelace", "ada@example.com", "SUMMARY",
          "Analytical engine pioneer.", "SKILLS", "Java, Spring Boot"}) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(line);
      }
      doc.write(out);
      return out.toByteArray();
    }
  }

  /** Minimal legacy DOC fixture: an empty HWPF document (valid OLE2 container). */
  public static byte[] docFixture() throws Exception {
    try (HWPFDocument doc = new HWPFDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      doc.write(out);
      return out.toByteArray();
    }
  }

  @Test
  void parsesPdfIntoStructuredState() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
        "application/pdf", pdfFixture());
    ObjectNode state = (ObjectNode) service.parse(file).state();
    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace");
    assertThat(state.path("contact").path("email").asText()).isEqualTo("ada@example.com");
    assertThat(state.path("experience")).hasSize(1);
    assertThat(state.path("experience").get(0).path("company").asText())
        .isEqualTo("Analytical Engines Ltd");
    assertThat(service.parse(file).rawText()).contains("Designed the first algorithm");
  }

  @Test
  void parsesDocxIntoStructuredState() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "resume.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxFixture());
    ObjectNode state = (ObjectNode) service.parse(file).state();
    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace");
    assertThat(state.path("skills").toString()).contains("Java");
  }

  @Test
  void parsesEmptyDocWithoutThrowing() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "resume.doc",
        "application/msword", docFixture());
    assertThat(service.parse(file).state()).isNotNull();
    assertThat(service.parse(file).rawText()).isBlank();
  }

  @Test
  void rejectsEmptyFile() {
    MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
        "application/pdf", new byte[0]);
    assertThatThrownBy(() -> service.parse(file))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void rejectsUnsupportedExtension() {
    MockMultipartFile file = new MockMultipartFile("file", "resume.txt",
        "text/plain", "hello".getBytes());
    assertThatThrownBy(() -> service.parse(file))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void corruptPdfIsA400NotA500() {
    MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
        "application/pdf", "not a pdf at all".getBytes());
    assertThatThrownBy(() -> service.parse(file))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
