package com.epitomejob.resume.controller;

import com.epitomejob.resume.dto.ResumeResponse;
import com.epitomejob.resume.dto.ResumeTextResponse;
import com.epitomejob.resume.service.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/resumes")
public class ResumeController {

  private final ResumeService service;

  public ResumeController(ResumeService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ResumeResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {
    return ResponseEntity.status(HttpStatus.CREATED).body(ResumeResponse.from(service.upload(file)));
  }

  @GetMapping("/me")
  public List<ResumeResponse> listMine() {
    return service.listMine().stream().map(ResumeResponse::from).toList();
  }

  @GetMapping("/{id}/text")
  public ResumeTextResponse getText(@PathVariable UUID id) {
    return ResumeTextResponse.from(service.getParsedText(id));
  }
}
