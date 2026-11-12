package com.epitomejob.tracker.controller;

import com.epitomejob.tracker.dto.TrackerCreateRequest;
import com.epitomejob.tracker.dto.TrackerDto;
import com.epitomejob.tracker.dto.TrackerUpdateRequest;
import com.epitomejob.tracker.service.TrackerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tracker")
public class TrackerController {

  private final TrackerService service;

  public TrackerController(TrackerService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<TrackerDto> create(@Valid @RequestBody TrackerCreateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(req.jobId(), req.resumeId()));
  }

  @GetMapping("/me")
  public List<TrackerDto> listMine() {
    return service.listMine();
  }

  @PutMapping("/{id}")
  public TrackerDto update(@PathVariable UUID id, @Valid @RequestBody TrackerUpdateRequest req) {
    return service.update(id, req.status(), req.notes());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
