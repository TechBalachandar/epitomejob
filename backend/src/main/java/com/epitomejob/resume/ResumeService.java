package com.epitomejob.resume;
import com.epitomejob.common.SecurityUtil;
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
  public ResumeService(ResumeRepository resumes, BlobStorageService blob) { this.resumes = resumes; this.blob = blob; }
  @Transactional
  public Resume upload(MultipartFile file) throws Exception {
    if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
    if (file.getSize() > 10 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Max 10 MB");
    String name = file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename();
    if (!name.toLowerCase().endsWith(".pdf"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phase 0 accepts PDF only (DOCX comes in Phase 3)");
    UUID userId = SecurityUtil.currentUserId();
    String blobPath = userId + "/" + UUID.randomUUID() + ".pdf";
    blob.put(blobPath, file.getInputStream(), file.getSize(), "application/pdf");
    Resume resume = resumes.save(new Resume(userId, blobPath, name, "pdf", (int) file.getSize()));
    try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
      String text = new PDFTextStripper().getText(doc);
      resume.setParsedText(text.length() > 20000 ? text.substring(0, 20000) : text);
      resume.setStatus("READY");
    } catch (Exception e) {
      resume.setStatus("FAILED"); // file is kept; the user can re-upload
    }
    return resumes.save(resume);
  }
  @Transactional(readOnly = true)
  public List<Resume> listMine() { return resumes.findByUserIdOrderByCreatedAtDesc(SecurityUtil.currentUserId()); }
}
