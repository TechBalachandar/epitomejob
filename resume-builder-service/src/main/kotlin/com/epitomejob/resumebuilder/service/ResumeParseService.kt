package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.constants.AppConstants
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.ParseResponse
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream

/**
 * Extracts text from an uploaded resume and structures it.
 * PDF via PDFBox 3.0.2 — same approach as the monolith's ResumeService
 * (Loader.loadPDF + PDFTextStripper). DOCX via Apache POI XWPF, legacy DOC
 * via POI HWPF (WordExtractor). Best effort throughout: unparsed text is
 * always preserved in the state's rawText.
 */
@Service
class ResumeParseService(private val parser: ResumeStateParser) {

  fun parse(file: MultipartFile?): ParseResponse {
    if (file == null || file.isEmpty) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.NO_FILE_UPLOADED)
    }
    if (file.size > AppConstants.Parse.MAX_FILE_SIZE_BYTES) {
      throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, AppConstants.Errors.FILE_TOO_LARGE)
    }
    val name = file.originalFilename?.lowercase() ?: ""
    var text: String = try {
      val bytes = file.bytes
      when {
        name.endsWith(AppConstants.Parse.DOCX_EXTENSION) -> extractDocx(bytes)
        name.endsWith(AppConstants.Parse.DOC_EXTENSION) -> extractDoc(bytes)
        name.endsWith(AppConstants.Parse.PDF_EXTENSION) -> extractPdf(bytes)
        else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST,
            AppConstants.Errors.UNSUPPORTED_FILE_TYPE)
      }
    } catch (ex: ResponseStatusException) {
      throw ex
    } catch (ex: Exception) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.PARSE_FAILED, ex)
    }
    if (text.length > AppConstants.Parse.MAX_PARSED_TEXT_CHARS) {
      text = text.substring(0, AppConstants.Parse.MAX_PARSED_TEXT_CHARS)
    }
    val state: ObjectNode = parser.parse(text)
    return ParseResponse(state, text)
  }

  companion object {
    /** PDFBox 3: Loader.loadPDF (the 3.x API — PDDocument.load is gone). */
    @JvmStatic
    fun extractPdf(bytes: ByteArray): String {
      Loader.loadPDF(bytes).use { doc ->
        return PDFTextStripper().getText(doc)
      }
    }

    /** DOCX: paragraphs + table cells, in document order. */
    @JvmStatic
    fun extractDocx(bytes: ByteArray): String {
      val out = StringBuilder()
      ByteArrayInputStream(bytes).use { input ->
        XWPFDocument(input).use { doc ->
          for (paragraph in doc.paragraphs) {
            appendLine(out, paragraph.text)
          }
          for (table in doc.tables) {
            for (row in table.rows) {
              for (cell in row.tableCells) {
                appendLine(out, cell.text)
              }
            }
          }
        }
      }
      return out.toString()
    }

    /** Legacy .doc (OLE2): HWPF WordExtractor. */
    @JvmStatic
    fun extractDoc(bytes: ByteArray): String {
      ByteArrayInputStream(bytes).use { input ->
        HWPFDocument(input).use { doc ->
          WordExtractor(doc).use { extractor ->
            return extractor.text
          }
        }
      }
    }

    private fun appendLine(out: StringBuilder, line: String?) {
      if (line != null && line.isNotBlank()) {
        out.append(line.trim()).append('\n')
      }
    }
  }
}
