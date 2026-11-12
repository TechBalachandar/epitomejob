package com.epitomejob.job.service;

import com.epitomejob.job.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Job source (W2 contract §8). Search uses the same filters as GET /jobs:
 * query = substring over title/company/summary, location = substring over
 * location, workType = exact, sponsorsH1b = boolean.
 */
public interface JobProvider {

  Page<Job> search(String query, String location, String workType,
                   Boolean sponsorsH1b, Pageable pageable);
}
