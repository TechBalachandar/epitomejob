package com.epitomejob.job.controller;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.job.dto.JobDto;
import com.epitomejob.job.dto.JobPageResponse;
import com.epitomejob.job.service.JobService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/jobs")
public class JobController {

  private final JobService service;

  public JobController(JobService service) {
    this.service = service;
  }

  @GetMapping
  public JobPageResponse list(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) String workType,
      @RequestParam(required = false) Boolean sponsorsH1b,
      @RequestParam(required = false) String source,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(AppConstants.Job.MAX_PAGE_SIZE, Math.max(1, size));
    return JobPageResponse.from(service.search(query, location, workType, sponsorsH1b,
        source, PageRequest.of(safePage, safeSize)));
  }

  @GetMapping("/{id}")
  public JobDto getOne(@PathVariable UUID id) {
    return service.getJob(id);
  }
}
