package com.epitomejob.resume.service;

import com.epitomejob.resume.model.Resume;
import com.epitomejob.resume.repository.ResumeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock
  private ResumeRepository resumes;

  @Mock
  private BlobStorageService blob;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private ResumeService service() {
    return new ResumeService(resumes, blob);
  }

  /** Builds a minimal valid one-page PDF in memory — no fixture file needed. */
  private byte[] realPdfBytes() throws Exception {
    try (PDDocument doc = new PDDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(100, 700);
        content.showText("Balachandar Jeldi - Java Developer");
        content.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void uploadValidPdfIsReady() throws Exception {
    authenticate();
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes());

    Resume saved = service().upload(file);

    assertEquals("READY", saved.getStatus());
    assertTrue(saved.getParsedText().contains("Balachandar"));
    verify(blob).put(any(), any(), any(Long.class), any());
  }

  @Test
  void uploadUnparseablePdfIsFailedButKept() throws Exception {
    authenticate();
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf",
        "this is not a pdf at all".getBytes());

    Resume saved = service().upload(file);

    assertEquals("FAILED", saved.getStatus(), "file is kept; the user can re-upload");
  }

  @Test
  void uploadEmptyFileIsBadRequest() {
    authenticate();
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]);
    var ex = assertThrows(ResponseStatusException.class, () -> service().upload(file));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void uploadNonPdfIsBadRequest() {
    authenticate();
    var file = new MockMultipartFile("file", "resume.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "fake".getBytes());
    var ex = assertThrows(ResponseStatusException.class, () -> service().upload(file));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void uploadOversizeFileIsPayloadTooLarge() {
    authenticate();
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(11L * 1024 * 1024);
    when(file.getOriginalFilename()).thenReturn("resume.pdf");
    var ex = assertThrows(ResponseStatusException.class, () -> service().upload(file));
    assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatusCode());
  }

  @Test
  void listMineReturnsNewestFirst() {
    authenticate();
    Resume resume = new Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100);
    when(resumes.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(resume));

    assertEquals(List.of(resume), service().listMine());
  }
}
