package com.epitomejob.tracker.service;

import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import com.epitomejob.tracker.dto.TrackerDto;
import com.epitomejob.tracker.model.TrackerEntry;
import com.epitomejob.tracker.repository.TrackerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackerServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();

  @Mock
  private TrackerRepository entries;
  @Mock
  private JobRepository jobs;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private TrackerService service() {
    return new TrackerService(entries, jobs);
  }

  private Job job() {
    return new Job("Senior Software Engineer, Backend (Java)", "Northwind Health Systems",
        "Reston, VA", "Hybrid", 145000, 175000, true,
        List.of("Java 21", "Spring Boot", "Kafka"), "Backend APIs.", "Backend",
        Instant.parse("2026-11-10T09:00:00Z"));
  }

  @Test
  void createSavesEntry() {
    authenticate();
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()));
    when(entries.findByUserIdAndJobId(USER_ID, JOB_ID)).thenReturn(Optional.empty());
    when(entries.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TrackerDto dto = service().create(JOB_ID, null);

    assertEquals(JOB_ID, dto.jobId());
    assertEquals("SAVED", dto.status());
    assertEquals("Northwind Health Systems", dto.company());
    assertNull(dto.resumeId());
  }

  @Test
  void createDuplicateIs409() {
    authenticate();
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()));
    when(entries.findByUserIdAndJobId(USER_ID, JOB_ID))
        .thenReturn(Optional.of(new TrackerEntry(USER_ID, JOB_ID, null)));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().create(JOB_ID, null));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void createMissingJobIs404() {
    authenticate();
    when(jobs.findById(JOB_ID)).thenReturn(Optional.empty());

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().create(JOB_ID, null));
    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void updateInvalidStatusIs400() {
    authenticate();
    TrackerEntry entry = new TrackerEntry(USER_ID, JOB_ID, null);
    when(entries.findById(entry.getId())).thenReturn(Optional.of(entry));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().update(entry.getId(), "HIRED", null));
    assertEquals(400, ex.getStatusCode().value());
  }

  @Test
  void updateKeepsNotesWhenAbsentAndSetsStatus() {
    authenticate();
    TrackerEntry entry = new TrackerEntry(USER_ID, JOB_ID, null);
    entry.setNotes("Spoke to recruiter");
    when(entries.findById(entry.getId())).thenReturn(Optional.of(entry));
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()));
    when(entries.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TrackerDto dto = service().update(entry.getId(), "APPLIED", null);

    assertEquals("APPLIED", dto.status());
    assertEquals("Spoke to recruiter", dto.notes());
  }

  @Test
  void updateNotOwnerIs404() {
    authenticate();
    TrackerEntry entry = new TrackerEntry(UUID.randomUUID(), JOB_ID, null);
    when(entries.findById(entry.getId())).thenReturn(Optional.of(entry));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().update(entry.getId(), "APPLIED", null));
    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void deleteNotOwnerIs404() {
    authenticate();
    TrackerEntry entry = new TrackerEntry(UUID.randomUUID(), JOB_ID, null);
    when(entries.findById(entry.getId())).thenReturn(Optional.of(entry));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().delete(entry.getId()));
    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void listMineIsNewestFirst() {
    authenticate();
    TrackerEntry entry = new TrackerEntry(USER_ID, JOB_ID, null);
    when(entries.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(entry));
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()));

    List<TrackerDto> list = service().listMine();

    assertEquals(1, list.size());
    assertEquals("Senior Software Engineer, Backend (Java)", list.get(0).title());
    verify(entries).findByUserIdOrderByCreatedAtDesc(USER_ID);
  }
}
