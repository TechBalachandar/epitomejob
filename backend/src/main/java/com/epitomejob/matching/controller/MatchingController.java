package com.epitomejob.matching.controller;

import com.epitomejob.matching.dto.MatchDto;
import com.epitomejob.matching.service.MatchingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/matches")
public class MatchingController {

  private final MatchingService service;

  public MatchingController(MatchingService service) {
    this.service = service;
  }

  @GetMapping("/me")
  public List<MatchDto> matchesForMe() {
    return service.matchesForMe();
  }

  @GetMapping("/{jobId}")
  public MatchDto matchForJob(@PathVariable UUID jobId) {
    return service.matchForJob(jobId);
  }
}
