package com.epitomejob.tracker.service

import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import com.epitomejob.notification.service.NotificationService
import com.epitomejob.tracker.dto.TrackerDto
import com.epitomejob.tracker.model.TrackerEntry
import com.epitomejob.tracker.repository.TrackerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows

@ExtendWith(MockitoExtension::class)
class TrackerServiceTest {

  companion object {
    private val USER_ID = UUID.randomUUID()
    private val JOB_ID = UUID.randomUUID()
  }

  @Mock
  private lateinit var entries: TrackerRepository

  @Mock
  private lateinit var jobs: JobRepository

  @Mock
  private lateinit var notifications: NotificationService

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, listOf()))
  }

  private fun service(): TrackerService {
    return TrackerService(entries, jobs, notifications)
  }

  private fun job(): Job {
    return Job("Senior Software Engineer, Backend (Java)", "Northwind Health Systems",
      "Reston, VA", "Hybrid", 145000, 175000, true,
      listOf("Java 21", "Spring Boot", "Kafka"), "Backend APIs.", "Backend",
      Instant.parse("2026-11-10T09:00:00Z"))
  }

  @Test
  fun createSavesEntry() {
    authenticate()
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))
    Mockito.`when`(entries.findByUserIdAndJobId(USER_ID, JOB_ID)).thenReturn(Optional.empty())
    Mockito.`when`(entries.save(any())).thenAnswer { inv -> inv.getArgument<TrackerEntry>(0) }

    val dto: TrackerDto = service().create(JOB_ID, null)

    assertEquals(JOB_ID, dto.jobId)
    assertEquals("SAVED", dto.status)
    assertEquals("Northwind Health Systems", dto.company)
    assertNull(dto.resumeId)
  }

  @Test
  fun createDuplicateIs409() {
    authenticate()
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))
    Mockito.`when`(entries.findByUserIdAndJobId(USER_ID, JOB_ID))
      .thenReturn(Optional.of(TrackerEntry(USER_ID, JOB_ID, null)))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().create(JOB_ID, null)
    }
    assertEquals(409, ex.statusCode.value())
  }

  @Test
  fun createMissingJobIs404() {
    authenticate()
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().create(JOB_ID, null)
    }
    assertEquals(404, ex.statusCode.value())
  }

  @Test
  fun updateInvalidStatusIs400() {
    authenticate()
    val entry = TrackerEntry(USER_ID, JOB_ID, null)
    Mockito.`when`(entries.findById(entry.id)).thenReturn(Optional.of(entry))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().update(entry.id, "HIRED", null)
    }
    assertEquals(400, ex.statusCode.value())
  }

  @Test
  fun updateKeepsNotesWhenAbsentAndSetsStatus() {
    authenticate()
    val entry = TrackerEntry(USER_ID, JOB_ID, null)
    entry.notes = "Spoke to recruiter"
    Mockito.`when`(entries.findById(entry.id)).thenReturn(Optional.of(entry))
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))
    Mockito.`when`(entries.save(any())).thenAnswer { inv -> inv.getArgument<TrackerEntry>(0) }

    val dto = service().update(entry.id, "APPLIED", null)

    assertEquals("APPLIED", dto.status)
    assertEquals("Spoke to recruiter", dto.notes)
  }

  @Test
  fun updateStatusChangeCreatesTrackerChangedNotification() {
    authenticate()
    val entry = TrackerEntry(USER_ID, JOB_ID, null)
    Mockito.`when`(entries.findById(entry.id)).thenReturn(Optional.of(entry))
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))
    Mockito.`when`(entries.save(any())).thenAnswer { inv -> inv.getArgument<TrackerEntry>(0) }

    val dto = service().update(entry.id, "APPLIED", null)

    assertEquals("APPLIED", dto.status)
    verify(notifications).create(eq(USER_ID), eq("TRACKER_CHANGED"),
      eq("Application updated"),
      eq("Senior Software Engineer, Backend (Java) moved to Applied."),
      eq("/tracker"))
  }

  @Test
  fun updateUnchangedStatusCreatesNoNotification() {
    authenticate()
    val entry = TrackerEntry(USER_ID, JOB_ID, null)
    entry.status = "APPLIED"
    Mockito.`when`(entries.findById(entry.id)).thenReturn(Optional.of(entry))
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))
    Mockito.`when`(entries.save(any())).thenAnswer { inv -> inv.getArgument<TrackerEntry>(0) }

    service().update(entry.id, "APPLIED", null)

    verify(notifications, never()).create(any(), any(), any(), any(), any())
  }

  @Test
  fun updateNotesOnlyCreatesNoNotification() {
    authenticate()
    val entry = TrackerEntry(USER_ID, JOB_ID, null)
    Mockito.`when`(entries.findById(entry.id)).thenReturn(Optional.of(entry))
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))
    Mockito.`when`(entries.save(any())).thenAnswer { inv -> inv.getArgument<TrackerEntry>(0) }

    val dto = service().update(entry.id, "SAVED", "Spoke to recruiter")

    assertEquals("Spoke to recruiter", dto.notes)
    verify(notifications, never()).create(any(), any(), any(), any(), any())
  }

  @Test
  fun updateNotOwnerIs404() {
    authenticate()
    val entry = TrackerEntry(UUID.randomUUID(), JOB_ID, null)
    Mockito.`when`(entries.findById(entry.id)).thenReturn(Optional.of(entry))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().update(entry.id, "APPLIED", null)
    }
    assertEquals(404, ex.statusCode.value())
  }

  @Test
  fun deleteNotOwnerIs404() {
    authenticate()
    val entry = TrackerEntry(UUID.randomUUID(), JOB_ID, null)
    Mockito.`when`(entries.findById(entry.id)).thenReturn(Optional.of(entry))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().delete(entry.id)
    }
    assertEquals(404, ex.statusCode.value())
  }

  @Test
  fun listMineIsNewestFirst() {
    authenticate()
    val entry = TrackerEntry(USER_ID, JOB_ID, null)
    Mockito.`when`(entries.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(listOf(entry))
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))

    val list: List<TrackerDto> = service().listMine()

    assertEquals(1, list.size)
    assertEquals("Senior Software Engineer, Backend (Java)", list[0].title)
    verify(entries).findByUserIdOrderByCreatedAtDesc(USER_ID)
  }
}
