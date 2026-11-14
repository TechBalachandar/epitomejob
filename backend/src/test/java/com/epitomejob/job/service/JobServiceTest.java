package com.epitomejob.job.service;

import com.epitomejob.job.dto.JobDto;
import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  @Mock
  private JobRepository jobs;
  @Mock
  private JobProvider provider;

  private Job job(String title, String company, String location, String workType,
                  boolean sponsors, String postedAt) {
    return new Job(title, company, location, workType, 120000, 160000, sponsors,
        List.of("Java"), "Summary.", "Backend", Instant.parse(postedAt));
  }

  @Test
  void searchDelegatesToProviderAndMapsToDto() {
    Job j = job("Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
        "2026-11-10T09:00:00Z");
    when(provider.search("backend", null, null, null, null, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(j), PageRequest.of(0, 20), 1));
    JobService service = new JobService(jobs, provider);

    Page<JobDto> page = service.search("backend", null, null, null, null, PageRequest.of(0, 20));

    assertEquals(1, page.getTotalElements());
    assertEquals("Backend Engineer", page.getContent().get(0).title());
    assertEquals("SEED", page.getContent().get(0).source(),
        "a job built without a source defaults to SEED");
    assertEquals(null, page.getContent().get(0).sourceUrl());
  }

  @Test
  void searchMapsSourceAndSourceUrlToDto() {
    Job j = job("Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
        "2026-11-10T09:00:00Z");
    j.setSource("LINKEDIN");
    j.setSourceUrl("https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer");
    when(provider.search("android", null, null, null, null, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(j), PageRequest.of(0, 20), 1));
    JobService service = new JobService(jobs, provider);

    Page<JobDto> page = service.search("android", null, null, null, null, PageRequest.of(0, 20));

    assertEquals("LINKEDIN", page.getContent().get(0).source());
    assertEquals("https://www.linkedin.com/jobs/search/?keywords=Backend%20Engineer",
        page.getContent().get(0).sourceUrl());
  }

  @Test
  void searchPassesSourceToProvider() {
    when(provider.search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20)))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));
    JobService service = new JobService(jobs, provider);

    service.search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20));

    verify(provider).search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20));
  }

  @Test
  void jobSourceValidationRejectsUnknownSourceAndBadUrl() {
    Job j = job("Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
        "2026-11-10T09:00:00Z");
    ResponseStatusException badSource = assertThrows(ResponseStatusException.class,
        () -> j.setSource("FACEBOOK"));
    assertEquals(400, badSource.getStatusCode().value());
    ResponseStatusException badUrl = assertThrows(ResponseStatusException.class,
        () -> j.setSourceUrl("ftp://example.com/jobs"));
    assertEquals(400, badUrl.getStatusCode().value());
  }

  @Test
  void getJobMissingIs404() {
    UUID id = UUID.randomUUID();
    when(jobs.findById(id)).thenReturn(Optional.empty());
    JobService service = new JobService(jobs, provider);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.getJob(id));
    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void seedProviderFiltersAndSortsByPostedAtDesc() {
    Job oldJob = job("Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
        "2026-11-02T09:00:00Z");
    Job newJob = job("Backend Engineer", "Leidos", "Remote (US)", "Remote", false,
        "2026-11-10T09:00:00Z");
    Job other = job("Android Developer", "Northwind", "Vienna, VA", "Onsite", true,
        "2026-11-11T09:00:00Z");
    when(jobs.findAll()).thenReturn(List.of(oldJob, newJob, other));
    SeedJobProvider seed = new SeedJobProvider(jobs);

    Page<Job> page = seed.search("backend", null, null, null, null, PageRequest.of(0, 20));

    assertEquals(2, page.getTotalElements());
    assertEquals(newJob.getId(), page.getContent().get(0).getId());

    Page<Job> remote = seed.search(null, "Remote", null, null, null, PageRequest.of(0, 20));
    assertEquals(1, remote.getTotalElements());

    Page<Job> sponsored = seed.search(null, null, null, true, null, PageRequest.of(0, 20));
    assertEquals(2, sponsored.getTotalElements());

    Page<Job> paged = seed.search(null, null, null, null, null, PageRequest.of(1, 2));
    assertEquals(3, paged.getTotalElements());
    assertEquals(1, paged.getContent().size());
    assertTrue(paged.getTotalPages() == 2);
  }

  @Test
  void seedProviderFiltersBySourceCaseInsensitively() {
    Job linkedin = job("Backend Engineer", "Northwind", "Reston, VA", "Hybrid", true,
        "2026-11-10T09:00:00Z");
    linkedin.setSource("LINKEDIN");
    Job indeed = job("Backend Engineer", "Leidos", "Remote (US)", "Remote", false,
        "2026-11-09T09:00:00Z");
    indeed.setSource("INDEED");
    when(jobs.findAll()).thenReturn(List.of(linkedin, indeed));
    SeedJobProvider seed = new SeedJobProvider(jobs);

    Page<Job> exact = seed.search(null, null, null, null, "LINKEDIN", PageRequest.of(0, 20));
    assertEquals(1, exact.getTotalElements());
    assertEquals(linkedin.getId(), exact.getContent().get(0).getId());

    Page<Job> lower = seed.search(null, null, null, null, "linkedin", PageRequest.of(0, 20));
    assertEquals(1, lower.getTotalElements(), "source filter is case-insensitive");

    Page<Job> all = seed.search(null, null, null, null, null, PageRequest.of(0, 20));
    assertEquals(2, all.getTotalElements(), "no source filter returns every job");
  }

  @Test
  void adzunaProviderThrowsNamingMissingEnvVars() {
    AdzunaJobProvider missing = new AdzunaJobProvider(key -> null);
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> missing.search(null, null, null, null, null, PageRequest.of(0, 20)));
    assertTrue(ex.getMessage().contains("ADZUNA_APP_ID"));
    assertTrue(ex.getMessage().contains("ADZUNA_APP_KEY"));

    AdzunaJobProvider present = new AdzunaJobProvider(key -> "set");
    assertThrows(UnsupportedOperationException.class,
        () -> present.search(null, null, null, null, null, PageRequest.of(0, 20)));
  }
}
