package com.epitomejob.job.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.job.dto.JobDto;
import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class JobService {

  private final JobRepository jobs;
  private final JobProvider provider;

  public JobService(JobRepository jobs, JobProvider provider) {
    this.jobs = jobs;
    this.provider = provider;
  }

  @Transactional(readOnly = true)
  public Page<JobDto> search(String query, String location, String workType,
                             Boolean sponsorsH1b, String source, Pageable pageable) {
    return provider.search(query, location, workType, sponsorsH1b, source, pageable)
        .map(JobDto::from);
  }

  @Transactional(readOnly = true)
  public JobDto getJob(UUID id) {
    return JobDto.from(findJob(id));
  }

  @Transactional(readOnly = true)
  public Job findJob(UUID id) {
    return jobs.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.JOB_NOT_FOUND));
  }
}
