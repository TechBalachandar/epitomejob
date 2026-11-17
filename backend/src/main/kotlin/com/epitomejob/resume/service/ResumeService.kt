package com.epitomejob.resume.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.exception.ResumeNotReadyException
import com.epitomejob.common.util.SecurityUtil
import com.epitomejob.resume.model.Resume
import com.epitomejob.resume.repository.ResumeRepository
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.util.UUID

@Service
class ResumeService(private val resumes: ResumeRepository, private val blob: BlobStorageService) {

  /** Upload without a label (kept so existing callers/tests are unchanged). */
  @Transactional
  fun upload(file: MultipartFile?): Resume {
    return upload(file, null)
  }

  @Transactional
  fun upload(file: MultipartFile?, label: String?): Resume {
    // Label first, before any blob write: a bad label must not store a file.
    val cleanLabel = normalizeLabel(label, false)
    if (file == null || file.isEmpty) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.NO_FILE_UPLOADED)
    }
    if (file.size > AppConstants.Resume.MAX_FILE_SIZE_BYTES) {
      throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, AppConstants.Errors.FILE_TOO_LARGE)
    }
    val name = file.originalFilename ?: "resume.pdf"
    val lower = name.lowercase()
    // /resumes accepts PDF, DOC and DOCX (the Builder parse path already
    // did; this store was PDF-only). The stored blob keeps the upload's
    // own extension and content type, and the format is recorded so
    // downloads and the RAG indexer know what they are reading.
    val format: String
    val extension: String
    val contentType: String
    when {
      lower.endsWith(AppConstants.Resume.PDF_EXTENSION) -> {
        format = "pdf"
        extension = AppConstants.Resume.PDF_EXTENSION
        contentType = AppConstants.Resume.PDF_CONTENT_TYPE
      }
      lower.endsWith(AppConstants.Resume.DOCX_EXTENSION) -> {
        format = "docx"
        extension = AppConstants.Resume.DOCX_EXTENSION
        contentType = AppConstants.Resume.DOCX_CONTENT_TYPE
      }
      lower.endsWith(AppConstants.Resume.DOC_EXTENSION) -> {
        format = "doc"
        extension = AppConstants.Resume.DOC_EXTENSION
        contentType = AppConstants.Resume.DOC_CONTENT_TYPE
      }
      else -> throw ResponseStatusException(
        HttpStatus.BAD_REQUEST, AppConstants.Errors.UNSUPPORTED_RESUME_TYPE
      )
    }
    val userId = SecurityUtil.currentUserId()
    val blobPath = "$userId/${UUID.randomUUID()}$extension"
    blob.put(blobPath, file.inputStream, file.size, contentType)
    val resume = resumes.save(Resume(userId, blobPath, name, format, file.size.toInt()))
    resume.label = cleanLabel
    try {
      val text = extractText(file.bytes, format)
      resume.parsedText = if (text.length > AppConstants.Resume.MAX_PARSED_TEXT_CHARS) {
        text.substring(0, AppConstants.Resume.MAX_PARSED_TEXT_CHARS)
      } else {
        text
      }
      resume.status = AppConstants.Resume.STATUS_READY
    } catch (e: Exception) {
      resume.status = AppConstants.Resume.STATUS_FAILED // file is kept; the user can re-upload
    }
    return resumes.save(resume)
  }

  /**
   * Rename a resume's label only (W6 contract §2b). Owner-only: a missing
   * resume or one belonging to someone else is a 404.
   */
  @Transactional
  fun renameLabel(id: UUID, label: String?): Resume {
    val cleanLabel = normalizeLabel(label, true)
    val resume = resumes.findById(id)
      .filter { it.userId == SecurityUtil.currentUserId() }
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.RESUME_NOT_FOUND)
      }
    resume.label = cleanLabel
    return resumes.save(resume)
  }

  /**
   * Trims a label; an empty string becomes null (upload) and a missing/blank
   * label is a 400 when [required] (rename). Over 80 chars is a 400.
   */
  private fun normalizeLabel(label: String?, required: Boolean): String? {
    val clean = label?.trim()
    if (clean.isNullOrEmpty()) {
      if (required) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_RESUME_LABEL)
      }
      return null
    }
    if (clean.length > AppConstants.Resume.MAX_LABEL_LENGTH) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_RESUME_LABEL)
    }
    return clean
  }

  @Transactional(readOnly = true)
  fun listMine(): List<Resume> {
    return resumes.findByUserIdOrderByCreatedAtDesc(SecurityUtil.currentUserId())
  }

  /**
   * The caller's resume with its parsed text (W3 contract §3). 404 when the
   * resume is missing or belongs to someone else; 400 when the text is not
   * ready yet. Exists so the agent-brain can index/read resume text.
   */
  @Transactional(readOnly = true)
  fun getParsedText(id: UUID): Resume {
    val resume = resumes.findById(id)
      .filter { it.userId == SecurityUtil.currentUserId() }
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.RESUME_NOT_FOUND)
      }
    if (resume.parsedText.isNullOrBlank()) {
      throw ResumeNotReadyException()
    }
    return resume
  }

  companion object {
    /** Text extraction by format: PDFBox for PDF, POI XWPF for DOCX, POI HWPF for legacy DOC. */
    internal fun extractText(bytes: ByteArray, format: String): String {
      return when (format) {
        "pdf" -> {
          Loader.loadPDF(bytes).use { doc ->
            PDFTextStripper().getText(doc)
          }
        }
        "docx" -> {
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
          out.toString()
        }
        "doc" -> {
          ByteArrayInputStream(bytes).use { input ->
            HWPFDocument(input).use { doc ->
              WordExtractor(doc).use { extractor ->
                extractor.text
              }
            }
          }
        }
        else -> throw IllegalArgumentException("Unsupported resume format: $format")
      }
    }

    private fun appendLine(out: StringBuilder, line: String?) {
      if (line != null && line.isNotBlank()) {
        out.append(line.trim()).append('\n')
      }
    }
  }
}
