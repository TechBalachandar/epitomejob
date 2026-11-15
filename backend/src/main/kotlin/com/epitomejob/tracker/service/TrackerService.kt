package com.epitomejob.tracker.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.util.SecurityUtil
import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import com.epitomejob.notification.service.NotificationService
import com.epitomejob.tracker.dto.TrackerDto
import com.epitomejob.tracker.model.TrackerEntry
import com.epitomejob.tracker.repository.TrackerRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class TrackerService(
  private val entries: TrackerRepository,
  private val jobs: JobRepository,
  private val notifications: NotificationService
) {

  companion object {
    private val VALID_STATUSES = setOf(
      AppConstants.Tracker.STATUS_SAVED,
      AppConstants.Tracker.STATUS_APPLIED,
      AppConstants.Tracker.STATUS_INTERVIEW,
      AppConstants.Tracker.STATUS_OFFER,
      AppConstants.Tracker.STATUS_REJECTED
    )

    /** Human label for a tracker status; validated upstream, so default is unreachable. */
    private fun label(status: String): String {
      return when (status) {
        AppConstants.Tracker.STATUS_SAVED -> "Saved"
        AppConstants.Tracker.STATUS_APPLIED -> "Applied"
        AppConstants.Tracker.STATUS_INTERVIEW -> "Interview"
        AppConstants.Tracker.STATUS_OFFER -> "Offer"
        AppConstants.Tracker.STATUS_REJECTED -> "Rejected"
        else -> status
      }
    }
  }

  /** Creates a SAVED entry; 409 when this user already saved this job. */
  @Transactional
  fun create(jobId: UUID?, resumeId: UUID?): TrackerDto {
    val userId = SecurityUtil.currentUserId()
    if (jobId == null) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.JOB_ID_REQUIRED)
    }
    val job = jobs.findById(jobId)
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.JOB_NOT_FOUND)
      }
    if (entries.findByUserIdAndJobId(userId, jobId).isPresent) {
      throw ResponseStatusException(HttpStatus.CONFLICT, AppConstants.Errors.TRACKER_DUPLICATE)
    }
    return TrackerDto.from(entries.save(TrackerEntry(userId, jobId, resumeId)), job)
  }

  @Transactional(readOnly = true)
  fun listMine(): List<TrackerDto> {
    val userId = SecurityUtil.currentUserId()
    return entries.findByUserIdOrderByCreatedAtDesc(userId)
      .map { entry -> TrackerDto.from(entry, findJob(entry.jobId)) }
  }

  /**
   * Updates status and, when present, notes. An invalid status is 400;
   * a missing entry or one owned by someone else is 404. A notification
   * is created only when the status actually changes — never for a
   * notes-only update or an unchanged status.
   */
  @Transactional
  fun update(id: UUID, status: String?, notes: String?): TrackerDto {
    val entry = findOwned(id)
    if (status == null || !VALID_STATUSES.contains(status)) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.INVALID_TRACKER_STATUS)
    }
    val oldStatus = entry.status
    entry.status = status
    if (notes != null) {
      entry.notes = notes
    }
    entry.touch()
    val saved = entries.save(entry)
    val job = findJob(saved.jobId)
    if (oldStatus != status) {
      notifications.create(saved.userId,
        AppConstants.Notification.TYPE_TRACKER_CHANGED,
        AppConstants.Notification.TRACKER_TITLE,
        String.format(AppConstants.Notification.TRACKER_BODY_FORMAT,
          job.title, label(status)),
        AppConstants.Notification.LINK_TRACKER)
    }
    return TrackerDto.from(saved, job)
  }

  @Transactional
  fun delete(id: UUID) {
    entries.delete(findOwned(id))
  }

  private fun findOwned(id: UUID): TrackerEntry {
    val userId = SecurityUtil.currentUserId()
    return entries.findById(id)
      .filter { e -> e.userId == userId }
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.TRACKER_NOT_FOUND)
      }
  }

  private fun findJob(jobId: UUID): Job {
    return jobs.findById(jobId)
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.JOB_NOT_FOUND)
      }
  }
}
