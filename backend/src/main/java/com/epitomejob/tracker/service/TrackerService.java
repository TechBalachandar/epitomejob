package com.epitomejob.tracker.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import com.epitomejob.notification.service.NotificationService;
import com.epitomejob.tracker.dto.TrackerDto;
import com.epitomejob.tracker.model.TrackerEntry;
import com.epitomejob.tracker.repository.TrackerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TrackerService {

  private static final Set<String> VALID_STATUSES = Set.of(
      AppConstants.Tracker.STATUS_SAVED,
      AppConstants.Tracker.STATUS_APPLIED,
      AppConstants.Tracker.STATUS_INTERVIEW,
      AppConstants.Tracker.STATUS_OFFER,
      AppConstants.Tracker.STATUS_REJECTED);

  private final TrackerRepository entries;
  private final JobRepository jobs;
  private final NotificationService notifications;

  public TrackerService(TrackerRepository entries, JobRepository jobs,
                        NotificationService notifications) {
    this.entries = entries;
    this.jobs = jobs;
    this.notifications = notifications;
  }

  /** Creates a SAVED entry; 409 when this user already saved this job. */
  @Transactional
  public TrackerDto create(UUID jobId, UUID resumeId) {
    UUID userId = SecurityUtil.currentUserId();
    if (jobId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.JOB_ID_REQUIRED);
    }
    Job job = jobs.findById(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.JOB_NOT_FOUND));
    if (entries.findByUserIdAndJobId(userId, jobId).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, AppConstants.Errors.TRACKER_DUPLICATE);
    }
    return TrackerDto.from(entries.save(new TrackerEntry(userId, jobId, resumeId)), job);
  }

  @Transactional(readOnly = true)
  public List<TrackerDto> listMine() {
    UUID userId = SecurityUtil.currentUserId();
    return entries.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(entry -> TrackerDto.from(entry, findJob(entry.getJobId())))
        .toList();
  }

  /**
   * Updates status and, when present, notes. An invalid status is 400;
   * a missing entry or one owned by someone else is 404. A notification
   * is created only when the status actually changes — never for a
   * notes-only update or an unchanged status.
   */
  @Transactional
  public TrackerDto update(UUID id, String status, String notes) {
    TrackerEntry entry = findOwned(id);
    if (status == null || !VALID_STATUSES.contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.INVALID_TRACKER_STATUS);
    }
    String oldStatus = entry.getStatus();
    entry.setStatus(status);
    if (notes != null) {
      entry.setNotes(notes);
    }
    entry.touch();
    TrackerEntry saved = entries.save(entry);
    Job job = findJob(saved.getJobId());
    if (!oldStatus.equals(status)) {
      notifications.create(saved.getUserId(),
          AppConstants.Notification.TYPE_TRACKER_CHANGED,
          AppConstants.Notification.TRACKER_TITLE,
          String.format(AppConstants.Notification.TRACKER_BODY_FORMAT,
              job.getTitle(), label(status)),
          AppConstants.Notification.LINK_TRACKER);
    }
    return TrackerDto.from(saved, job);
  }

  @Transactional
  public void delete(UUID id) {
    entries.delete(findOwned(id));
  }

  private TrackerEntry findOwned(UUID id) {
    UUID userId = SecurityUtil.currentUserId();
    return entries.findById(id)
        .filter(e -> e.getUserId().equals(userId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.TRACKER_NOT_FOUND));
  }

  private Job findJob(UUID jobId) {
    return jobs.findById(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.JOB_NOT_FOUND));
  }

  /** Human label for a tracker status; validated upstream, so default is unreachable. */
  private static String label(String status) {
    return switch (status) {
      case AppConstants.Tracker.STATUS_SAVED -> "Saved";
      case AppConstants.Tracker.STATUS_APPLIED -> "Applied";
      case AppConstants.Tracker.STATUS_INTERVIEW -> "Interview";
      case AppConstants.Tracker.STATUS_OFFER -> "Offer";
      case AppConstants.Tracker.STATUS_REJECTED -> "Rejected";
      default -> status;
    };
  }
}
