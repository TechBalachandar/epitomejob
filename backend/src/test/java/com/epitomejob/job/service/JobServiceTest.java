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
    when(provider.search("backend", null, null, null, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(j), PageRequest.of(0, 20), 1));
    JobService service = new JobService(jobs, provider);

    Page<JobDto> page = service.search("backend", null, null, null, PageRequest.of(0, 20));

    assertEquals(1, page.getTotalElements());
    assertEquals("Backend Engineer", page.getContent().get(0).title());
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

    Page<Job> page = seed.search("backend", null, null, null, PageRequest.of(0, 20));

    assertEquals(2, page.getTotalElements());
    assertEquals(newJob.getId(), page.getContent().get(0).getId());

    Page<Job> remote = seed.search(null, "Remote", null, null, PageRequest.of(0, 20));
    assertEquals(1, remote.getTotalElements());

    Page<Job> sponsored = seed.search(null, null, null, true, PageRequest.of(0, 20));
    assertEquals(2, sponsored.getTotalElements());

    Page<Job> paged = seed.search(null, null, null, null, PageRequest.of(1, 2));
    assertEquals(3, paged.getTotalElements());
    assertEquals(1, paged.getContent().size());
    assertTrue(paged.getTotalPages() == 2);
  }

  @Test
  void adzunaProviderThrowsNamingMissingEnvVars() {
    AdzunaJobProvider missing = new AdzunaJobProvider(key -> null);
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> missing.search(null, null, null, null, PageRequest.of(0, 20)));
    assertTrue(ex.getMessage().contains("ADZUNA_APP_ID"));
    assertTrue(ex.getMessage().contains("ADZUNA_APP_KEY"));

    AdzunaJobProvider present = new AdzunaJobProvider(key -> "set");
    assertThrows(UnsupportedOperationException.class,
        () -> present.search(null, null, null, null, PageRequest.of(0, 20)));
  }
}
