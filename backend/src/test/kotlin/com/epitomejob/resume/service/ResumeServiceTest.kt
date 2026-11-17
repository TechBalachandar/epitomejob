package com.epitomejob.resume.service

import com.epitomejob.common.exception.ResumeNotReadyException
import com.epitomejob.resume.dto.ResumeResponse
import com.epitomejob.resume.model.Resume
import com.epitomejob.resume.repository.ResumeRepository
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.util.LittleEndian
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

@ExtendWith(MockitoExtension::class)
class ResumeServiceTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
  }

  @Mock
  private lateinit var resumes: ResumeRepository

  @Mock
  private lateinit var blob: BlobStorageService

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticate() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, listOf())
  }

  private fun service(): ResumeService {
    return ResumeService(resumes, blob)
  }

  /** Builds a minimal valid one-page PDF in memory — no fixture file needed. */
  private fun realPdfBytes(): ByteArray {
    PDDocument().use { doc ->
      ByteArrayOutputStream().use { out ->
        val page = PDPage()
        doc.addPage(page)
        PDPageContentStream(doc, page).use { content ->
          content.beginText()
          content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
          content.newLineAtOffset(100f, 700f)
          content.showText("Balachandar Jeldi - Java Developer")
          content.endText()
        }
        doc.save(out)
        return out.toByteArray()
      }
    }
  }

  /** Builds a minimal valid DOCX in memory with POI XWPF — no fixture file needed. */
  private fun realDocxBytes(): ByteArray {
    XWPFDocument().use { doc ->
      ByteArrayOutputStream().use { out ->
        for (line in arrayOf("Ada Lovelace", "ada@example.com", "SUMMARY", "Analytical engine pioneer.")) {
          val paragraph: XWPFParagraph = doc.createParagraph()
          val run: XWPFRun = paragraph.createRun()
          run.setText(line)
        }
        doc.write(out)
        return out.toByteArray()
      }
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
  private fun emptyDocBytes(): ByteArray {
    val fib = ByteArray(900)
    LittleEndian.putShort(fib, 0, 0xA5EC.toShort())   // wIdent
    LittleEndian.putShort(fib, 2, 0x00C1.toShort())   // nFib (Word 97)
    LittleEndian.putShort(fib, 6, 0x0409.toShort())   // lid (en-US)
    LittleEndian.putShort(fib, 12, 0x00C1.toShort())  // nFibBack
    LittleEndian.putShort(fib, 32, 14.toShort())      // csw
    LittleEndian.putShort(fib, 62, 22.toShort())      // cslw
    LittleEndian.putInt(fib, 64, 900)                // fcMin (FibRgLw97[0])
    LittleEndian.putShort(fib, 152, 93.toShort())     // cbRgFcLcb (0x5D)
    LittleEndian.putShort(fib, 898, 0.toShort())      // cswNew
    // FibRgFcLcb fields (field N: fc at 154 + N*8, lcb at +4).
    fibField(fib, 31, 0, 500)    // DOP (DocumentProperties, 500 bytes)
    fibField(fib, 1, 500, 20)    // STSHF (stylesheet)
    fibField(fib, 15, 520, 2)    // STTBFFFN (font table: 0 fonts)
    fibField(fib, 33, 522, 21)   // CLX (piece table)
    // Table stream: DOP (500 zero bytes) + stylesheet + font table + Clx.
    val table = ByteArray(543)
    LittleEndian.putShort(table, 500, 18.toShort()) // cbStshi = 18 (Stshi size, cstd = 0)
    // Clx (Pcdt): clxt=0x02, lcb=16, PlcPcd: CP[0]=0, CP[1]=0, Pcd{fc=900, prm=0}.
    table[522] = 0x02
    LittleEndian.putInt(table, 523, 16)
    LittleEndian.putInt(table, 527, 0)   // CP[0]
    LittleEndian.putInt(table, 531, 0)   // CP[1]
    LittleEndian.putInt(table, 535, 900) // Pcd fc (fcMin)
    LittleEndian.putInt(table, 539, 0)   // Pcd prm (istd 0, not complex)
    POIFSFileSystem().use { fs ->
      ByteArrayOutputStream().use { out ->
        fs.root.createDocument("WordDocument", ByteArrayInputStream(fib))
        fs.root.createDocument("0Table", ByteArrayInputStream(table))
        fs.writeFilesystem(out)
        return out.toByteArray()
      }
    }
  }

  private fun fibField(fib: ByteArray, field: Int, fc: Int, lcb: Int) {
    LittleEndian.putInt(fib, 154 + field * 8, fc)
    LittleEndian.putInt(fib, 158 + field * 8, lcb)
  }

  @Test
  fun uploadValidPdfIsReady() {
    authenticate()
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes())

    val saved = service().upload(file)

    assertEquals("READY", saved.status)
    assertTrue(saved.parsedText?.contains("Balachandar") ?: false)
    verify(blob).put(any(), any(), any(), any())
  }

  @Test
  fun uploadUnparseablePdfIsFailedButKept() {
    authenticate()
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }
    val file = MockMultipartFile(
      "file", "resume.pdf", "application/pdf",
      "this is not a pdf at all".toByteArray()
    )

    val saved = service().upload(file)

    assertEquals("FAILED", saved.status, "file is kept; the user can re-upload")
  }

  @Test
  fun uploadEmptyFileIsBadRequest() {
    authenticate()
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", ByteArray(0))
    val ex = assertThrows(ResponseStatusException::class.java) { service().upload(file) }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun uploadValidDocxIsReady() {
    authenticate()
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }
    val file = MockMultipartFile(
      "file", "resume.docx",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      realDocxBytes()
    )

    val saved = service().upload(file)

    assertEquals("READY", saved.status)
    assertEquals("docx", saved.fileType)
    assertTrue(saved.blobPath.endsWith(".docx"), "blob keeps the .docx extension")
    assertTrue(saved.parsedText?.contains("Ada Lovelace") ?: false)
    verify(blob).put(any(), any(), any(), any())
  }

  @Test
  fun uploadValidDocIsReady() {
    authenticate()
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }
    val file = MockMultipartFile("file", "resume.doc", "application/msword", emptyDocBytes())

    val saved = service().upload(file)

    assertEquals("READY", saved.status, "a legacy .doc is accepted, not rejected")
    assertEquals("doc", saved.fileType)
    assertTrue(saved.blobPath.endsWith(".doc"), "blob keeps the .doc extension")
    verify(blob).put(any(), any(), any(), any())
  }

  @Test
  fun uploadUnsupportedTypeIsBadRequest() {
    authenticate()
    val file = MockMultipartFile("file", "resume.txt", "text/plain", "hello".toByteArray())
    val ex = assertThrows(ResponseStatusException::class.java) { service().upload(file) }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun uploadOversizeFileIsPayloadTooLarge() {
    authenticate()
    val file = mock<MultipartFile>()
    whenever(file.isEmpty).thenReturn(false)
    whenever(file.size).thenReturn(11L * 1024 * 1024)
    // No getOriginalFilename() stub: the size check throws before the name is ever read,
    // and Mockito strict stubs fail the test on an unused stub.
    val ex = assertThrows(ResponseStatusException::class.java) { service().upload(file) }
    assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.statusCode)
  }

  @Test
  fun listMineReturnsNewestFirst() {
    authenticate()
    val resume = Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100)
    whenever(resumes.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(listOf(resume))

    assertEquals(listOf(resume), service().listMine())
  }

  /**
   * P0 #16: the list is scoped to the authenticated user. The service must
   * ask the repository for the CALLER's id — never another user's id — so
   * user A can never be handed user B's rows.
   */
  @Test
  fun listMineScopesQueryToAuthenticatedUser() {
    authenticate()
    val mine = Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100)
    whenever(resumes.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(listOf(mine))

    val result = service().listMine()

    verify(resumes).findByUserIdOrderByCreatedAtDesc(USER_ID)
    assertEquals(listOf(mine), result, "only the caller's own resumes are listed")
    assertTrue(
      result.all { it.userId == USER_ID },
      "no resume belonging to another user is ever returned"
    )
  }

  /**
   * P0 #16: an upload is stored under the authenticated user's id. A resume
   * uploaded while logged in as user A must never be filed under user B.
   */
  @Test
  fun uploadStoresResumeUnderAuthenticatedUserId() {
    authenticate()
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes())

    val saved = service().upload(file)

    assertEquals(USER_ID, saved.userId, "upload is filed under the caller, not anyone else")
    assertTrue(
      saved.blobPath.startsWith("$USER_ID/"),
      "the blob path is namespaced by the caller's id"
    )
  }

  @Test
  fun getParsedTextReturnsOwnedResumeWithText() {
    authenticate()
    val resume = Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100)
    resume.parsedText = "Java Developer with Spring Boot"
    whenever(resumes.findById(resume.id)).thenReturn(Optional.of(resume))

    assertEquals(resume, service().getParsedText(resume.id))
  }

  @Test
  fun getParsedTextMissingIs404() {
    authenticate()
    whenever(resumes.findById(any())).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().getParsedText(UUID.randomUUID())
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun getParsedTextNotOwnerIs404() {
    authenticate()
    val resume = Resume(UUID.randomUUID(), "u1/f.pdf", "resume.pdf", "pdf", 100)
    resume.parsedText = "someone else's text"
    whenever(resumes.findById(any())).thenReturn(Optional.of(resume))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().getParsedText(resume.id)
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun getParsedTextNullIsNotReady() {
    authenticate()
    val resume = Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100)
    whenever(resumes.findById(any())).thenReturn(Optional.of(resume))

    assertThrows(ResumeNotReadyException::class.java) { service().getParsedText(resume.id) }
  }

  @Test
  fun getParsedTextBlankIsNotReady() {
    authenticate()
    val resume = Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100)
    resume.parsedText = "   "
    whenever(resumes.findById(any())).thenReturn(Optional.of(resume))

    assertThrows(ResumeNotReadyException::class.java) { service().getParsedText(resume.id) }
  }

  @Test
  fun uploadWithLabelPersistsTrimmedLabel() {
    authenticate()
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes())

    val saved = service().upload(file, "  Android Developer  ")

    assertEquals("READY", saved.status)
    assertEquals("Android Developer", saved.label, "label is trimmed on upload")
  }

  @Test
  fun uploadWithoutLabelStoresNull() {
    authenticate()
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes())

    val saved = service().upload(file)

    assertEquals(null, saved.label, "no label given means a null label")
  }

  @Test
  fun uploadWithEmptyLabelStoresNull() {
    authenticate()
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", realPdfBytes())

    val saved = service().upload(file, "   ")

    assertEquals(null, saved.label, "an empty label is stored as null")
  }

  @Test
  fun uploadWithTooLongLabelIsBadRequest() {
    authenticate()
    // Label validation runs before any blob write or save: no stubs here —
    // a stubbed blob or save would be unused and Mockito strict stubs fail.
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", "fake".toByteArray())
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().upload(file, "x".repeat(81))
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun resumeResponseFallsBackToFileNameWhenLabelNull() {
    val resume = Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100)
    assertEquals(
      "resume.pdf",
      ResumeResponse.from(resume).label,
      "the label is never null in the API"
    )

    resume.label = "Android Developer"
    assertEquals("Android Developer", ResumeResponse.from(resume).label)
  }

  @Test
  fun renameLabelUpdatesLabel() {
    authenticate()
    val resume = Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100)
    resume.status = "READY"
    whenever(resumes.findById(resume.id)).thenReturn(Optional.of(resume))
    whenever(resumes.save(argThat { true })).thenAnswer { inv -> inv.getArgument<Resume>(0) }

    val renamed = service().renameLabel(resume.id, "  Java Backend  ")

    assertEquals("Java Backend", renamed.label, "label is trimmed on rename")
    verify(resumes).save(resume)
  }

  @Test
  fun renameLabelMissingIs404() {
    authenticate()
    whenever(resumes.findById(any())).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().renameLabel(UUID.randomUUID(), "Android Developer")
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun renameLabelNotOwnerIs404() {
    authenticate()
    val resume = Resume(UUID.randomUUID(), "u1/f.pdf", "resume.pdf", "pdf", 100)
    whenever(resumes.findById(any())).thenReturn(Optional.of(resume))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().renameLabel(resume.id, "Android Developer")
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun renameLabelBlankIs400() {
    authenticate()
    // Label validation runs before the resume is loaded: no findById stub.
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().renameLabel(UUID.randomUUID(), "   ")
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun renameLabelTooLongIs400() {
    authenticate()
    // Label validation runs before the resume is loaded: no findById stub.
    val ex = assertThrows(ResponseStatusException::class.java) {
      service().renameLabel(UUID.randomUUID(), "x".repeat(81))
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }
}
