package com.epitomejob.resume.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.exception.ResumeNotReadyException;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.resume.model.Resume;
import com.epitomejob.resume.repository.ResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class ResumeService {

  private final ResumeRepository resumes;
  private final BlobStorageService blob;

  public ResumeService(ResumeRepository resumes, BlobStorageService blob) {
    this.resumes = resumes;
    this.blob = blob;
  }

  /** Upload without a label (kept so existing callers/tests are unchanged). */
  @Transactional
  public Resume upload(MultipartFile file) throws Exception {
    return upload(file, null);
  }

  @Transactional
  public Resume upload(MultipartFile file, String label) throws Exception {
    // Label first, before any blob write: a bad label must not store a file.
    String cleanLabel = normalizeLabel(label, false);
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.NO_FILE_UPLOADED);
    }
    if (file.getSize() > AppConstants.Resume.MAX_FILE_SIZE_BYTES) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, AppConstants.Errors.FILE_TOO_LARGE);
    }
    String name = file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename();
    String lower = name.toLowerCase();
    // /resumes accepts PDF, DOC and DOCX (the Builder parse path already
    // did; this store was PDF-only). The stored blob keeps the upload's
    // own extension and content type, and the format is recorded so
    // downloads and the RAG indexer know what they are reading.
    String format;
    String extension;
    String contentType;
    if (lower.endsWith(AppConstants.Resume.PDF_EXTENSION)) {
      format = "pdf";
      extension = AppConstants.Resume.PDF_EXTENSION;
      contentType = AppConstants.Resume.PDF_CONTENT_TYPE;
    } else if (lower.endsWith(AppConstants.Resume.DOCX_EXTENSION)) {
      format = "docx";
      extension = AppConstants.Resume.DOCX_EXTENSION;
      contentType = AppConstants.Resume.DOCX_CONTENT_TYPE;
    } else if (lower.endsWith(AppConstants.Resume.DOC_EXTENSION)) {
      format = "doc";
      extension = AppConstants.Resume.DOC_EXTENSION;
      contentType = AppConstants.Resume.DOC_CONTENT_TYPE;
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.UNSUPPORTED_RESUME_TYPE);
    }
    UUID userId = SecurityUtil.currentUserId();
    String blobPath = userId + "/" + UUID.randomUUID() + extension;
    blob.put(blobPath, file.getInputStream(), file.getSize(), contentType);
    Resume resume = resumes.save(new Resume(userId, blobPath, name, format, (int) file.getSize()));
    resume.setLabel(cleanLabel);
    try {
      String text = extractText(file.getBytes(), format);
      resume.setParsedText(text.length() > AppConstants.Resume.MAX_PARSED_TEXT_CHARS
          ? text.substring(0, AppConstants.Resume.MAX_PARSED_TEXT_CHARS) : text);
      resume.setStatus(AppConstants.Resume.STATUS_READY);
    } catch (Exception e) {
      resume.setStatus(AppConstants.Resume.STATUS_FAILED); // file is kept; the user can re-upload
    }
    return resumes.save(resume);
  }

  /** Text extraction by format: PDFBox for PDF, POI XWPF for DOCX, POI HWPF for legacy DOC. */
  static String extractText(byte[] bytes, String format) throws Exception {
    switch (format) {
      case "pdf" -> {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
          return new PDFTextStripper().getText(doc);
        }
      }
      case "docx" -> {
        StringBuilder out = new StringBuilder();
        try (InputStream in = new ByteArrayInputStream(bytes);
             XWPFDocument doc = new XWPFDocument(in)) {
          for (XWPFParagraph paragraph : doc.getParagraphs()) {
            appendLine(out, paragraph.getText());
          }
          for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
              for (XWPFTableCell cell : row.getTableCells()) {
                appendLine(out, cell.getText());
              }
            }
          }
        }
        return out.toString();
      }
      case "doc" -> {
        try (InputStream in = new ByteArrayInputStream(bytes);
             HWPFDocument doc = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(doc)) {
          return extractor.getText();
        }
      }
      default -> throw new IllegalArgumentException("Unsupported resume format: " + format);
    }
  }

  private static void appendLine(StringBuilder out, String line) {
    if (line != null && !line.isBlank()) {
      out.append(line.trim()).append('\n');
    }
  }

  /**
   * Rename a resume's label only (W6 contract §2b). Owner-only: a missing
   * resume or one belonging to someone else is a 404.
   */
  @Transactional
  public Resume renameLabel(UUID id, String label) {
    String cleanLabel = normalizeLabel(label, true);
    Resume resume = resumes.findById(id)
        .filter(r -> r.getUserId().equals(SecurityUtil.currentUserId()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.RESUME_NOT_FOUND));
    resume.setLabel(cleanLabel);
    return resumes.save(resume);
  }

  /**
   * Trims a label; an empty string becomes null (upload) and a missing/blank
   * label is a 400 when {@code required} (rename). Over 80 chars is a 400.
   */
  private String normalizeLabel(String label, boolean required) {
    String clean = label == null ? null : label.trim();
    if (clean == null || clean.isEmpty()) {
      if (required) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            AppConstants.Errors.INVALID_RESUME_LABEL);
      }
      return null;
    }
    if (clean.length() > AppConstants.Resume.MAX_LABEL_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.INVALID_RESUME_LABEL);
    }
    return clean;
  }

  @Transactional(readOnly = true)
  public List<Resume> listMine() {
    return resumes.findByUserIdOrderByCreatedAtDesc(SecurityUtil.currentUserId());
  }

  /**
   * The caller's resume with its parsed text (W3 contract §3). 404 when the
   * resume is missing or belongs to someone else; 400 when the text is not
   * ready yet. Exists so the agent-brain can index/read resume text.
   */
  @Transactional(readOnly = true)
  public Resume getParsedText(UUID id) {
    Resume resume = resumes.findById(id)
        .filter(r -> r.getUserId().equals(SecurityUtil.currentUserId()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.RESUME_NOT_FOUND));
    if (resume.getParsedText() == null || resume.getParsedText().isBlank()) {
      throw new ResumeNotReadyException();
    }
    return resume;
  }
}
