package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.ParseResponse;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Extracts text from an uploaded resume and structures it.
 * PDF via PDFBox 3.0.2 — same approach as the monolith's ResumeService
 * (Loader.loadPDF + PDFTextStripper). DOCX via Apache POI XWPF, legacy DOC
 * via POI HWPF (WordExtractor). Best effort throughout: unparsed text is
 * always preserved in the state's rawText.
 */
@Service
public class ResumeParseService {

  private final ResumeStateParser parser;

  public ResumeParseService(ResumeStateParser parser) {
    this.parser = parser;
  }

  public ParseResponse parse(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.NO_FILE_UPLOADED);
    }
    if (file.getSize() > AppConstants.Parse.MAX_FILE_SIZE_BYTES) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, AppConstants.Errors.FILE_TOO_LARGE);
    }
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    String text;
    try {
      byte[] bytes = file.getBytes();
      if (name.endsWith(AppConstants.Parse.DOCX_EXTENSION)) {
        text = extractDocx(bytes);
      } else if (name.endsWith(AppConstants.Parse.DOC_EXTENSION)) {
        text = extractDoc(bytes);
      } else if (name.endsWith(AppConstants.Parse.PDF_EXTENSION)) {
        text = extractPdf(bytes);
      } else {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            AppConstants.Errors.UNSUPPORTED_FILE_TYPE);
      }
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.PARSE_FAILED, ex);
    }
    if (text.length() > AppConstants.Parse.MAX_PARSED_TEXT_CHARS) {
      text = text.substring(0, AppConstants.Parse.MAX_PARSED_TEXT_CHARS);
    }
    ObjectNode state = parser.parse(text);
    return new ParseResponse(state, text);
  }

  /** PDFBox 3: Loader.loadPDF (the 3.x API — PDDocument.load is gone). */
  static String extractPdf(byte[] bytes) throws Exception {
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  /** DOCX: paragraphs + table cells, in document order. */
  static String extractDocx(byte[] bytes) throws Exception {
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

  /** Legacy .doc (OLE2): HWPF WordExtractor. */
  static String extractDoc(byte[] bytes) throws Exception {
    try (InputStream in = new ByteArrayInputStream(bytes);
         HWPFDocument doc = new HWPFDocument(in);
         WordExtractor extractor = new WordExtractor(doc)) {
      return extractor.getText();
    }
  }

  private static void appendLine(StringBuilder out, String line) {
    if (line != null && !line.isBlank()) {
      out.append(line.trim()).append('\n');
    }
  }
}
