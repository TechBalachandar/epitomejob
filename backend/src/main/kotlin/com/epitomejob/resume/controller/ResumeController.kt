package com.epitomejob.resume.controller

import com.epitomejob.resume.dto.RenameResumeRequest
import com.epitomejob.resume.dto.ResumeResponse
import com.epitomejob.resume.dto.ResumeTextResponse
import com.epitomejob.resume.service.ResumeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/resumes")
class ResumeController(private val service: ResumeService) {

  @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun upload(
    @RequestParam("file") file: MultipartFile,
    @RequestParam(required = false) label: String?
  ): ResponseEntity<ResumeResponse> {
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(ResumeResponse.from(service.upload(file, label)))
  }

  @PutMapping("/{id}")
  fun renameLabel(
    @PathVariable id: UUID,
    @Valid @RequestBody body: RenameResumeRequest
  ): ResumeResponse {
    return ResumeResponse.from(service.renameLabel(id, body.label))
  }

  @GetMapping("/me")
  fun listMine(): List<ResumeResponse> {
    return service.listMine().map { ResumeResponse.from(it) }
  }

  @GetMapping("/{id}/text")
  fun getText(@PathVariable id: UUID): ResumeTextResponse {
    return ResumeTextResponse.from(service.getParsedText(id))
  }
}
