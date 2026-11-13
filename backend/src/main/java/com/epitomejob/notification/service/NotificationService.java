package com.epitomejob.notification.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.notification.dto.NotificationDto;
import com.epitomejob.notification.dto.NotificationPage;
import com.epitomejob.notification.model.Notification;
import com.epitomejob.notification.repository.NotificationRepository;
import com.epitomejob.profile.repository.ProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

  private static final int MAX_PAGE_SIZE = 50;

  private final NotificationRepository notifications;
  private final ProfileRepository profiles;

  public NotificationService(NotificationRepository notifications, ProfileRepository profiles) {
    this.notifications = notifications;
    this.profiles = profiles;
  }

  /** Used by the hooks (§1f): stores a notification in the caller's transaction. */
  @Transactional
  public Notification create(UUID userId, String type, String title, String body, String link) {
    return notifications.save(new Notification(userId, type, title, body, link));
  }

  /**
   * Stored page, unread-first then newest. Size is clamped to 1–50
   * (the 20 default comes from the controller's @RequestParam).
   * At most one derived clock warning (§1e) is prepended,
   * on the first page only. Page metadata counts STORED rows only — the
   * derived warning does not change totalElements/totalPages.
   */
  @Transactional(readOnly = true)
  public NotificationPage listMine(int page, int size) {
    int clampedSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    int clampedPage = Math.max(0, page);
    Pageable pageable = PageRequest.of(clampedPage, clampedSize,
        Sort.by("isRead").ascending().and(Sort.by("createdAt").descending()));
    Page<Notification> stored = notifications.findByUserId(SecurityUtil.currentUserId(), pageable);
    List<NotificationDto> content = new ArrayList<>(
        stored.getContent().stream().map(NotificationDto::from).toList());
    if (clampedPage == 0) {
      clockWarning(SecurityUtil.currentUserId()).ifPresent(content::addFirst);
    }
    return new NotificationPage(content, stored.getNumber(), stored.getSize(),
        stored.getTotalElements(), stored.getTotalPages());
  }

  /** Stored unread count, plus 1 when a clock warning is active. */
  @Transactional(readOnly = true)
  public long unreadCount() {
    UUID userId = SecurityUtil.currentUserId();
    long stored = notifications.countByUserIdAndIsReadFalse(userId);
    return stored + (clockWarning(userId).isPresent() ? 1 : 0);
  }

  /** Marks one stored row read; a missing row or one owned by someone else is 404. */
  @Transactional
  public NotificationDto markRead(UUID id) {
    UUID userId = SecurityUtil.currentUserId();
    Notification notification = notifications.findById(id)
        .filter(n -> n.getUserId().equals(userId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.NOTIFICATION_NOT_FOUND));
    notification.setRead(true);
    return NotificationDto.from(notifications.save(notification));
  }

  /** Marks every stored unread row read; returns how many this call marked. */
  @Transactional
  public int markAllRead() {
    List<Notification> unread =
        notifications.findByUserIdAndIsReadFalse(SecurityUtil.currentUserId());
    unread.forEach(n -> n.setRead(true));
    notifications.saveAll(unread);
    return unread.size();
  }

  /**
   * Derived clock warning (M13 §1e): read-time only, never stored.
   * Mirrors the W5 web math exactly ({@code src/lib/h1b-clock.ts}): day
   * math at UTC midnight, {@code daysLeft = 60 - days since start},
   * {@code deadline = start + 60 days}. No start date → no warning.
   * Exactly one warning is active at a time; more than 14 days left → none.
   */
  Optional<NotificationDto> clockWarning(UUID userId) {
    LocalDate start = profiles.findByUserId(userId)
        .map(p -> p.getH1bClockStartDate())
        .orElse(null);
    if (start == null) {
      return Optional.empty();
    }
    long daysLeft = AppConstants.Notification.CLOCK_TOTAL_DAYS
        - ChronoUnit.DAYS.between(start, LocalDate.now(ZoneOffset.UTC));
    LocalDate deadline = start.plusDays(AppConstants.Notification.CLOCK_TOTAL_DAYS);
    String deadlineText = deadline.toString();
    if (daysLeft >= 8 && daysLeft <= AppConstants.Notification.CLOCK_WARN_14_DAYS) {
      return Optional.of(NotificationDto.derived(
          AppConstants.Notification.CLOCK_ID_14,
          AppConstants.Notification.TYPE_CLOCK_WARNING,
          String.format(AppConstants.Notification.CLOCK_TITLE_FORMAT, daysLeft),
          String.format(AppConstants.Notification.CLOCK_BODY_FORMAT, deadlineText),
          AppConstants.Notification.LINK_PROFILE));
    }
    if (daysLeft >= 1 && daysLeft <= AppConstants.Notification.CLOCK_WARN_7_DAYS) {
      String title = daysLeft == 1
          ? AppConstants.Notification.CLOCK_TITLE_ONE_DAY
          : String.format(AppConstants.Notification.CLOCK_TITLE_FORMAT, daysLeft);
      return Optional.of(NotificationDto.derived(
          AppConstants.Notification.CLOCK_ID_7,
          AppConstants.Notification.TYPE_CLOCK_WARNING,
          title,
          String.format(AppConstants.Notification.CLOCK_BODY_FORMAT, deadlineText),
          AppConstants.Notification.LINK_PROFILE));
    }
    if (daysLeft == 0) {
      return Optional.of(NotificationDto.derived(
          AppConstants.Notification.CLOCK_ID_0,
          AppConstants.Notification.TYPE_CLOCK_WARNING,
          AppConstants.Notification.CLOCK_TITLE_LAST_DAY,
          String.format(AppConstants.Notification.CLOCK_BODY_LAST_DAY_FORMAT, deadlineText),
          AppConstants.Notification.LINK_PROFILE));
    }
    if (daysLeft < 0) {
      return Optional.of(NotificationDto.derived(
          AppConstants.Notification.CLOCK_ID_EXPIRED,
          AppConstants.Notification.TYPE_CLOCK_WARNING,
          AppConstants.Notification.CLOCK_TITLE_EXPIRED,
          String.format(AppConstants.Notification.CLOCK_BODY_EXPIRED_FORMAT, deadlineText),
          AppConstants.Notification.LINK_PROFILE));
    }
    return Optional.empty();
  }
}
