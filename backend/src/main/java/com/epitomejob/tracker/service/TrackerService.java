package com.epitomejob.tracker.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
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

  public TrackerService(TrackerRepository entries, JobRepository jobs) {
    this.entries = entries;
    this.jobs = jobs;
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
   * a missing entry or one owned by someone else is 404.
   */
  @Transactional
  public TrackerDto update(UUID id, String status, String notes) {
    TrackerEntry entry = findOwned(id);
    if (status == null || !VALID_STATUSES.contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.INVALID_TRACKER_STATUS);
    }
    entry.setStatus(status);
    if (notes != null) {
      entry.setNotes(notes);
    }
    entry.touch();
    return TrackerDto.from(entries.save(entry), findJob(entry.getJobId()));
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
}
