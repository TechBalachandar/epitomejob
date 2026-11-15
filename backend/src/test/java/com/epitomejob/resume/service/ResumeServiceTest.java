package com.epitomejob.resume.service;

import com.epitomejob.common.exception.ResumeNotReadyException;
import com.epitomejob.resume.dto.ResumeResponse;
import com.epitomejob.resume.model.Resume;
import com.epitomejob.resume.repository.ResumeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
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

  /** Builds a minimal valid DOCX in memory with POI XWPF — no fixture file needed. */
  private byte[] realDocxBytes() throws Exception {
    try (XWPFDocument doc = new XWPFDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (String line : new String[]{"Ada Lovelace", "ada@example.com", "SUMMARY",
          "Analytical engine pioneer."}) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(line);
      }
      doc.write(out);
      return out.toByteArray();
    }
  }

  /**
   * Minimal legacy DOC fixture: a valid, empty Word 97 document, built
   * in-test (no binary fixtures in the tree). Same hand-built FIB/DOP/Clx
   * layout as the resume-builder TestFixtures.docFixture: a 900-byte FIB
   * (nFib 0x00C1) and a 0Table stream holding a 500-byte DocumentProperties
   * (DOP), a minimal 20-byte stylesheet, a 2-byte empty font table, and a
   * 21-byte Clx with one empty text piece. HWPF reads it as an empty
   * document, so the parsed text is blank.
   */
  private byte[] emptyDocBytes() throws Exception {
    byte[] fib = new byte[900];
    LittleEndian.putShort(fib, 0, (short) 0xA5EC);   // wIdent
    LittleEndian.putShort(fib, 2, (short) 0x00C1);   // nFib (Word 97)
    LittleEndian.putShort(fib, 6, (short) 0x0409);   // lid (en-US)
    LittleEndian.putShort(fib, 12, (short) 0x00C1);  // nFibBack
    LittleEndian.putShort(fib, 32, (short) 14);       // csw
    LittleEndian.putShort(fib, 62, (short) 22);       // cslw
    LittleEndian.putInt(fib, 64, 900);                // fcMin (FibRgLw97[0])
    LittleEndian.putShort(fib, 152, (short) 93);      // cbRgFcLcb (0x5D)
    LittleEndian.putShort(fib, 898, (short) 0);       // cswNew
    // FibRgFcLcb fields (field N: fc at 154 + N*8, lcb at +4).
    fibField(fib, 31, 0, 500);    // DOP (DocumentProperties, 500 bytes)
    fibField(fib, 1, 500, 20);    // STSHF (stylesheet)
    fibField(fib, 15, 520, 2);    // STTBFFFN (font table: 0 fonts)
    fibField(fib, 33, 522, 21);   // CLX (piece table)
    // Table stream: DOP (500 zero bytes) + stylesheet + font table + Clx.
    byte[] table = new byte[543];
    LittleEndian.putShort(table, 500, (short) 18); // cbStshi = 18 (Stshi size, cstd = 0)
    // Clx (Pcdt): clxt=0x02, lcb=16, PlcPcd: CP[0]=0, CP[1]=0, Pcd{fc=900, prm=0}.
    table[522] = 0x02;
    LittleEndian.putInt(table, 523, 16);
    LittleEndian.putInt(table, 527, 0);   // CP[0]
    LittleEndian.putInt(table, 531, 0);   // CP[1]
    LittleEndian.putInt(table, 535, 900); // Pcd fc (fcMin)
    LittleEndian.putInt(table, 539, 0);   // Pcd prm (istd 0, not complex)
    try (POIFSFileSystem fs = new POIFSFileSystem();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      fs.getRoot().createDocument("WordDocument", new ByteArrayInputStream(fib));
      fs.getRoot().createDocument("0Table", new ByteArrayInputStream(table));
      fs.writeFilesystem(out);
      return out.toByteArray();
    }
  }

  private static void fibField(byte[] fib, int field, int fc, int lcb) {
    LittleEndian.putInt(fib, 154 + field * 8, fc);
    LittleEndian.putInt(fib, 158 + field * 8, lcb);
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
  void uploadValidDocxIsReady() throws Exception {
    authenticate();
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "resume.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        realDocxBytes());

    Resume saved = service().upload(file);

    assertEquals("READY", saved.getStatus());
    assertEquals("docx", saved.getFileType());
    assertTrue(saved.getBlobPath().endsWith(".docx"), "blob keeps the .docx extension");
    assertTrue(saved.getParsedText().contains("Ada Lovelace"));
    verify(blob).put(any(), any(), any(Long.class), any());
  }

  @Test
  void uploadValidDocIsReady() throws Exception {
    authenticate();
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "resume.doc",
        "application/msword", emptyDocBytes());

    Resume saved = service().upload(file);

    assertEquals("READY", saved.getStatus(), "a legacy .doc is accepted, not rejected");
    assertEquals("doc", saved.getFileType());
    assertTrue(saved.getBlobPath().endsWith(".doc"), "blob keeps the .doc extension");
    verify(blob).put(any(), any(), any(Long.class), any());
  }

  @Test
  void uploadUnsupportedTypeIsBadRequest() {
    authenticate();
    var file = new MockMultipartFile("file", "resume.txt",
        "text/plain", "hello".getBytes());
    var ex = assertThrows(ResponseStatusException.class, () -> service().upload(file));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void uploadOversizeFileIsPayloadTooLarge() {
    authenticate();
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(11L * 1024 * 1024);
    // No getOriginalFilename() stub: the size check throws before the name is ever read,
    // and Mockito strict stubs fail the test on an unused stub.
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

  /**
   * P0 #16: the list is scoped to the authenticated user. The service must
   * ask the repository for the CALLER's id — never another user's id — so
   * user A can never be handed user B's rows.
   */
  @Test
  void listMineScopesQueryToAuthenticatedUser() {
    authenticate();
    Resume mine = new Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100);
    when(resumes.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(mine));

    List<Resume> result = service().listMine();

    verify(resumes).findByUserIdOrderByCreatedAtDesc(USER_ID);
    assertEquals(List.of(mine), result, "only the caller's own resumes are listed");
    assertTrue(result.stream().allMatch(r -> r.getUserId().equals(USER_ID)),
        "no resume belonging to another user is ever returned");
  }

  /**
   * P0 #16: an upload is stored under the authenticated user's id. A resume
   * uploaded while logged in as user A must never be filed under user B.
   */
  @Test
  void uploadStoresResumeUnderAuthenticatedUserId() throws Exception {
    authenticate();
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes());

    Resume saved = service().upload(file);

    assertEquals(USER_ID, saved.getUserId(), "upload is filed under the caller, not anyone else");
    assertTrue(saved.getBlobPath().startsWith(USER_ID.toString() + "/"),
        "the blob path is namespaced by the caller's id");
  }

  @Test
  void getParsedTextReturnsOwnedResumeWithText() {
    authenticate();
    Resume resume = new Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100);
    resume.setParsedText("Java Developer with Spring Boot");
    when(resumes.findById(resume.getId())).thenReturn(Optional.of(resume));

    assertEquals(resume, service().getParsedText(resume.getId()));
  }

  @Test
  void getParsedTextMissingIs404() {
    authenticate();
    when(resumes.findById(any())).thenReturn(Optional.empty());

    var ex = assertThrows(ResponseStatusException.class,
        () -> service().getParsedText(UUID.randomUUID()));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void getParsedTextNotOwnerIs404() {
    authenticate();
    Resume resume = new Resume(UUID.randomUUID(), "u1/f.pdf", "resume.pdf", "pdf", 100);
    resume.setParsedText("someone else's text");
    when(resumes.findById(any())).thenReturn(Optional.of(resume));

    var ex = assertThrows(ResponseStatusException.class,
        () -> service().getParsedText(resume.getId()));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void getParsedTextNullIsNotReady() {
    authenticate();
    Resume resume = new Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100);
    when(resumes.findById(any())).thenReturn(Optional.of(resume));

    assertThrows(ResumeNotReadyException.class, () -> service().getParsedText(resume.getId()));
  }

  @Test
  void getParsedTextBlankIsNotReady() {
    authenticate();
    Resume resume = new Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100);
    resume.setParsedText("   ");
    when(resumes.findById(any())).thenReturn(Optional.of(resume));

    assertThrows(ResumeNotReadyException.class, () -> service().getParsedText(resume.getId()));
  }

  @Test
  void uploadWithLabelPersistsTrimmedLabel() throws Exception {
    authenticate();
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes());

    Resume saved = service().upload(file, "  Android Developer  ");

    assertEquals("READY", saved.getStatus());
    assertEquals("Android Developer", saved.getLabel(), "label is trimmed on upload");
  }

  @Test
  void uploadWithoutLabelStoresNull() throws Exception {
    authenticate();
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes());

    Resume saved = service().upload(file);

    assertEquals(null, saved.getLabel(), "no label given means a null label");
  }

  @Test
  void uploadWithEmptyLabelStoresNull() throws Exception {
    authenticate();
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes());

    Resume saved = service().upload(file, "   ");

    assertEquals(null, saved.getLabel(), "an empty label is stored as null");
  }

  @Test
  void uploadWithTooLongLabelIsBadRequest() {
    authenticate();
    // Label validation runs before any blob write or save: no stubs here —
    // a stubbed blob or save would be unused and Mockito strict stubs fail.
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "fake".getBytes());
    var ex = assertThrows(ResponseStatusException.class,
        () -> service().upload(file, "x".repeat(81)));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void resumeResponseFallsBackToFileNameWhenLabelNull() {
    Resume resume = new Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100);
    assertEquals("resume.pdf",
        ResumeResponse.from(resume).label(),
        "the label is never null in the API");

    resume.setLabel("Android Developer");
    assertEquals("Android Developer",
        ResumeResponse.from(resume).label());
  }

  @Test
  void renameLabelUpdatesLabel() {
    authenticate();
    Resume resume = new Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100);
    resume.setStatus("READY");
    when(resumes.findById(resume.getId())).thenReturn(Optional.of(resume));
    when(resumes.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Resume renamed = service().renameLabel(resume.getId(), "  Java Backend  ");

    assertEquals("Java Backend", renamed.getLabel(), "label is trimmed on rename");
    verify(resumes).save(resume);
  }

  @Test
  void renameLabelMissingIs404() {
    authenticate();
    when(resumes.findById(any())).thenReturn(Optional.empty());

    var ex = assertThrows(ResponseStatusException.class,
        () -> service().renameLabel(UUID.randomUUID(), "Android Developer"));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void renameLabelNotOwnerIs404() {
    authenticate();
    Resume resume = new Resume(UUID.randomUUID(), "u1/f.pdf", "resume.pdf", "pdf", 100);
    when(resumes.findById(any())).thenReturn(Optional.of(resume));

    var ex = assertThrows(ResponseStatusException.class,
        () -> service().renameLabel(resume.getId(), "Android Developer"));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void renameLabelBlankIs400() {
    authenticate();
    // Label validation runs before the resume is loaded: no findById stub.
    var ex = assertThrows(ResponseStatusException.class,
        () -> service().renameLabel(UUID.randomUUID(), "   "));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void renameLabelTooLongIs400() {
    authenticate();
    // Label validation runs before the resume is loaded: no findById stub.
    var ex = assertThrows(ResponseStatusException.class,
        () -> service().renameLabel(UUID.randomUUID(), "x".repeat(81)));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }
}
