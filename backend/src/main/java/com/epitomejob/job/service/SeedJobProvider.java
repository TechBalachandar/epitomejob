package com.epitomejob.job.service;

import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Default provider (W2 contract §8): reads the seeded {@code job.jobs} table. */
public class SeedJobProvider implements JobProvider {

  private final JobRepository jobs;

  public SeedJobProvider(JobRepository jobs) {
    this.jobs = jobs;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<Job> search(String query, String location, String workType,
                          Boolean sponsorsH1b, Pageable pageable) {
    List<Job> filtered = jobs.findAll().stream()
        .filter(job -> matchesQuery(job, query))
        .filter(job -> matchesLocation(job, location))
        .filter(job -> matchesWorkType(job, workType))
        .filter(job -> matchesSponsorship(job, sponsorsH1b))
        .sorted(Comparator.comparing(Job::getPostedAt,
            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
        .toList();
    int total = filtered.size();
    int from = Math.min((int) pageable.getOffset(), total);
    int to = Math.min(from + pageable.getPageSize(), total);
    return new PageImpl<>(filtered.subList(from, to), pageable, total);
  }

  private boolean matchesQuery(Job job, String query) {
    if (query == null || query.isBlank()) {
      return true;
    }
    String needle = query.toLowerCase();
    return contains(job.getTitle(), needle)
        || contains(job.getCompany(), needle)
        || contains(job.getSummary(), needle);
  }

  private boolean matchesLocation(Job job, String location) {
    return location == null || location.isBlank() || contains(job.getLocation(), location.toLowerCase());
  }

  private boolean matchesWorkType(Job job, String workType) {
    return workType == null || workType.isBlank()
        || (job.getWorkType() != null && job.getWorkType().equalsIgnoreCase(workType));
  }

  private boolean matchesSponsorship(Job job, Boolean sponsorsH1b) {
    return sponsorsH1b == null || job.isSponsorsH1b() == sponsorsH1b;
  }

  private boolean contains(String haystack, String needle) {
    return haystack != null && haystack.toLowerCase().contains(needle);
  }
}
