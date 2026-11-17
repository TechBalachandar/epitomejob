package com.epitomejob.notification.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.util.SecurityUtil
import com.epitomejob.notification.dto.NotificationDto
import com.epitomejob.notification.dto.NotificationPage
import com.epitomejob.notification.model.Notification
import com.epitomejob.notification.repository.NotificationRepository
import com.epitomejob.profile.repository.ProfileRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

@Service
class NotificationService(
  private val notifications: NotificationRepository,
  private val profiles: ProfileRepository
) {

  companion object {
    private const val MAX_PAGE_SIZE = 50
  }

  /** Used by the hooks (§1f): stores a notification in the caller's transaction. */
  @Transactional
  fun create(userId: UUID, type: String, title: String, body: String, link: String): Notification {
    return notifications.save(Notification(userId, type, title, body, link))
  }

  /**
   * Stored page, unread-first then newest. Size is clamped to 1–50
   * (the 20 default comes from the controller's @RequestParam).
   * At most one derived clock warning (§1e) is prepended,
   * on the first page only. Page metadata counts STORED rows only — the
   * derived warning does not change totalElements/totalPages.
   */
  @Transactional(readOnly = true)
  fun listMine(page: Int, size: Int): NotificationPage {
    val clampedSize = minOf(MAX_PAGE_SIZE, maxOf(1, size))
    val clampedPage = maxOf(0, page)
    val pageable: Pageable = PageRequest.of(clampedPage, clampedSize,
      Sort.by("isRead").ascending().and(Sort.by("createdAt").descending()))
    val stored = notifications.findByUserId(SecurityUtil.currentUserId(), pageable)
    val content = stored.content.map { NotificationDto.from(it) }.toMutableList()
    if (clampedPage == 0) {
      clockWarning(SecurityUtil.currentUserId()).ifPresent { content.add(0, it) }
    }
    return NotificationPage(content, stored.number, stored.size,
      stored.totalElements, stored.totalPages)
  }

  /** Stored unread count, plus 1 when a clock warning is active. */
  @Transactional(readOnly = true)
  fun unreadCount(): Long {
    val userId = SecurityUtil.currentUserId()
    val stored = notifications.countByUserIdAndIsReadFalse(userId)
    return stored + if (clockWarning(userId).isPresent) 1 else 0
  }

  /** Marks one stored row read; a missing row or one owned by someone else is 404. */
  @Transactional
  fun markRead(id: UUID): NotificationDto {
    val userId = SecurityUtil.currentUserId()
    val notification = notifications.findById(id)
      .filter { n -> n.userId == userId }
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND,
          AppConstants.Errors.NOTIFICATION_NOT_FOUND)
      }
    notification.isRead = true
    return NotificationDto.from(notifications.save(notification))
  }

  /** Marks every stored unread row read; returns how many this call marked. */
  @Transactional
  fun markAllRead(): Int {
    val unread =
      notifications.findByUserIdAndIsReadFalse(SecurityUtil.currentUserId())
    unread.forEach { n -> n.isRead = true }
    notifications.saveAll(unread)
    return unread.size
  }

  /**
   * Derived clock warning (M13 §1e): read-time only, never stored.
   * Mirrors the W5 web math exactly (`src/lib/h1b-clock.ts`): day
   * math at UTC midnight, `daysLeft = 60 - days since start`,
   * `deadline = start + 60 days`. No start date → no warning.
   * Exactly one warning is active at a time; more than 14 days left → none.
   */
  internal fun clockWarning(userId: UUID): Optional<NotificationDto> {
    val start: LocalDate? = profiles.findByUserId(userId)
      .map { p -> p.h1bClockStartDate }
      .orElse(null)
    if (start == null) {
      return Optional.empty()
    }
    val daysLeft = AppConstants.Notification.CLOCK_TOTAL_DAYS.toLong() -
      ChronoUnit.DAYS.between(start, LocalDate.now(ZoneOffset.UTC))
    val deadline = start.plusDays(AppConstants.Notification.CLOCK_TOTAL_DAYS.toLong())
    val deadlineText = deadline.toString()
    if (daysLeft >= 8 && daysLeft <= AppConstants.Notification.CLOCK_WARN_14_DAYS.toLong()) {
      return Optional.of(NotificationDto.derived(
        AppConstants.Notification.CLOCK_ID_14,
        AppConstants.Notification.TYPE_CLOCK_WARNING,
        String.format(AppConstants.Notification.CLOCK_TITLE_FORMAT, daysLeft),
        String.format(AppConstants.Notification.CLOCK_BODY_FORMAT, deadlineText),
        AppConstants.Notification.LINK_PROFILE))
    }
    if (daysLeft >= 1 && daysLeft <= AppConstants.Notification.CLOCK_WARN_7_DAYS.toLong()) {
      val title = if (daysLeft == 1L)
        AppConstants.Notification.CLOCK_TITLE_ONE_DAY
      else
        String.format(AppConstants.Notification.CLOCK_TITLE_FORMAT, daysLeft)
      return Optional.of(NotificationDto.derived(
        AppConstants.Notification.CLOCK_ID_7,
        AppConstants.Notification.TYPE_CLOCK_WARNING,
        title,
        String.format(AppConstants.Notification.CLOCK_BODY_FORMAT, deadlineText),
        AppConstants.Notification.LINK_PROFILE))
    }
    if (daysLeft == 0L) {
      return Optional.of(NotificationDto.derived(
        AppConstants.Notification.CLOCK_ID_0,
        AppConstants.Notification.TYPE_CLOCK_WARNING,
        AppConstants.Notification.CLOCK_TITLE_LAST_DAY,
        String.format(AppConstants.Notification.CLOCK_BODY_LAST_DAY_FORMAT, deadlineText),
        AppConstants.Notification.LINK_PROFILE))
    }
    if (daysLeft < 0) {
      return Optional.of(NotificationDto.derived(
        AppConstants.Notification.CLOCK_ID_EXPIRED,
        AppConstants.Notification.TYPE_CLOCK_WARNING,
        AppConstants.Notification.CLOCK_TITLE_EXPIRED,
        String.format(AppConstants.Notification.CLOCK_BODY_EXPIRED_FORMAT, deadlineText),
        AppConstants.Notification.LINK_PROFILE))
    }
    return Optional.empty()
  }
}
