package com.epitomejob.approval.controller

import com.epitomejob.approval.dto.ApprovalResponse
import com.epitomejob.approval.dto.ProposeRequest
import com.epitomejob.approval.service.ApprovalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/approvals")
class ApprovalController(private val service: ApprovalService) {

  @PostMapping
  fun propose(@Valid @RequestBody req: ProposeRequest): ResponseEntity<ApprovalResponse> {
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(ApprovalResponse.from(service.propose(req.action, req.payload)))
  }

  @PostMapping("/{id}/approve")
  fun approve(@PathVariable id: UUID): ApprovalResponse {
    return ApprovalResponse.from(service.decide(id, true))
  }

  @PostMapping("/{id}/reject")
  fun reject(@PathVariable id: UUID): ApprovalResponse {
    return ApprovalResponse.from(service.decide(id, false))
  }

  @GetMapping("/pending")
  fun pending(): List<ApprovalResponse> {
    return service.pendingMine().map { ApprovalResponse.from(it) }
  }
}
