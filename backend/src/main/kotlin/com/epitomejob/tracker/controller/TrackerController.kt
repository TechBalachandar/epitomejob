package com.epitomejob.tracker.controller

import com.epitomejob.tracker.dto.TrackerCreateRequest
import com.epitomejob.tracker.dto.TrackerDto
import com.epitomejob.tracker.dto.TrackerUpdateRequest
import com.epitomejob.tracker.service.TrackerService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tracker")
class TrackerController(private val service: TrackerService) {

  @PostMapping
  fun create(@Valid @RequestBody req: TrackerCreateRequest): ResponseEntity<TrackerDto> {
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(service.create(req.jobId, req.resumeId))
  }

  @GetMapping("/me")
  fun listMine(): List<TrackerDto> {
    return service.listMine()
  }

  @PutMapping("/{id}")
  fun update(@PathVariable id: UUID, @Valid @RequestBody req: TrackerUpdateRequest): TrackerDto {
    return service.update(id, req.status, req.notes)
  }

  @DeleteMapping("/{id}")
  fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
    service.delete(id)
    return ResponseEntity.noContent().build()
  }
}
