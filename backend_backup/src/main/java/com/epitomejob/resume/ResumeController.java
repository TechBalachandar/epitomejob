package com.epitomejob.resume;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
@RestController @RequestMapping("/resumes")
public class ResumeController {
  private final ResumeService service;
  public ResumeController(ResumeService service) { this.service = service; }
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Resume> upload(@RequestParam("file") MultipartFile file) throws Exception {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file));
  }
  @GetMapping("/me")
  public List<Resume> listMine() { return service.listMine(); }
}
