package com.epitomejob.approval.controller;

import com.epitomejob.approval.dto.ApprovalResponse;
import com.epitomejob.approval.dto.ProposeRequest;
import com.epitomejob.approval.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approvals")
public class ApprovalController {

  private final ApprovalService service;

  public ApprovalController(ApprovalService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApprovalResponse> propose(@Valid @RequestBody ProposeRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApprovalResponse.from(service.propose(req.action(), req.payload())));
  }

  @PostMapping("/{id}/approve")
  public ApprovalResponse approve(@PathVariable UUID id) {
    return ApprovalResponse.from(service.decide(id, true));
  }

  @PostMapping("/{id}/reject")
  public ApprovalResponse reject(@PathVariable UUID id) {
    return ApprovalResponse.from(service.decide(id, false));
  }

  @GetMapping("/pending")
  public List<ApprovalResponse> pending() {
    return service.pendingMine().stream().map(ApprovalResponse::from).toList();
  }
}
