package com.epitomejob.approval;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/approvals")
public class ApprovalController {
  private final ApprovalService service;
  public ApprovalController(ApprovalService service) { this.service = service; }
  public record ProposeRequest(@NotBlank String action, @NotBlank String payload) {}
  @PostMapping
  public ResponseEntity<Approval> propose(@Valid @RequestBody ProposeRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.propose(req.action(), req.payload()));
  }
  @PostMapping("/{id}/approve")
  public Approval approve(@PathVariable UUID id) { return service.decide(id, true); }
  @PostMapping("/{id}/reject")
  public Approval reject(@PathVariable UUID id) { return service.decide(id, false); }
  @GetMapping("/pending")
  public List<Approval> pending() { return service.pendingMine(); }
}
