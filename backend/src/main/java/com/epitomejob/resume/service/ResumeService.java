package com.epitomejob.resume.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.resume.model.Resume;
import com.epitomejob.resume.repository.ResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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

  @Transactional
  public Resume upload(MultipartFile file) throws Exception {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.NO_FILE_UPLOADED);
    }
    if (file.getSize() > AppConstants.Resume.MAX_FILE_SIZE_BYTES) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, AppConstants.Errors.FILE_TOO_LARGE);
    }
    String name = file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename();
    if (!name.toLowerCase().endsWith(AppConstants.Resume.PDF_EXTENSION)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.PDF_ONLY);
    }
    UUID userId = SecurityUtil.currentUserId();
    String blobPath = userId + "/" + UUID.randomUUID() + AppConstants.Resume.PDF_EXTENSION;
    blob.put(blobPath, file.getInputStream(), file.getSize(), AppConstants.Resume.PDF_CONTENT_TYPE);
    Resume resume = resumes.save(new Resume(userId, blobPath, name, "pdf", (int) file.getSize()));
    try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
      String text = new PDFTextStripper().getText(doc);
      resume.setParsedText(text.length() > AppConstants.Resume.MAX_PARSED_TEXT_CHARS
          ? text.substring(0, AppConstants.Resume.MAX_PARSED_TEXT_CHARS) : text);
      resume.setStatus(AppConstants.Resume.STATUS_READY);
    } catch (Exception e) {
      resume.setStatus(AppConstants.Resume.STATUS_FAILED); // file is kept; the user can re-upload
    }
    return resumes.save(resume);
  }

  @Transactional(readOnly = true)
  public List<Resume> listMine() {
    return resumes.findByUserIdOrderByCreatedAtDesc(SecurityUtil.currentUserId());
  }
}
