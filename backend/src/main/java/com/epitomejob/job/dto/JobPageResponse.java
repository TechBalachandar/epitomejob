package com.epitomejob.job.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Paged jobs, with the contract §2 field names (page, not Spring's number). */
public record JobPageResponse(
    List<JobDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public static JobPageResponse from(Page<JobDto> page) {
    return new JobPageResponse(page.getContent(), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages());
  }
}
