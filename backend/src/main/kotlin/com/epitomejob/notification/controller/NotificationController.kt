package com.epitomejob.notification.controller

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.notification.dto.NotificationDto
import com.epitomejob.notification.dto.NotificationPage
import com.epitomejob.notification.dto.ReadAllResponse
import com.epitomejob.notification.dto.UnreadCount
import com.epitomejob.notification.service.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * In-app notifications (M13). The owner always comes from the token —
 * no userId in any path or body. Another user's id is 404, never 403.
 */
@RestController
@RequestMapping("/notifications")
class NotificationController(private val service: NotificationService) {

  @GetMapping("/me")
  fun listMine(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int
  ): NotificationPage {
    return service.listMine(page, size)
  }

  @GetMapping("/unread-count")
  fun unreadCount(): UnreadCount {
    return UnreadCount(service.unreadCount())
  }

  /**
   * A derived `clock-*` id is 404 — derived warnings cannot be
   * marked read. A non-UUID id is likewise 404, never 400.
   */
  @PostMapping("/{id}/read")
  fun markRead(@PathVariable id: String): NotificationDto {
    val uuid: UUID
    try {
      uuid = UUID.fromString(id)
    } catch (ex: IllegalArgumentException) {
      throw ResponseStatusException(HttpStatus.NOT_FOUND,
        AppConstants.Errors.NOTIFICATION_NOT_FOUND)
    }
    return service.markRead(uuid)
  }

  @PostMapping("/read-all")
  fun markAllRead(): ReadAllResponse {
    return ReadAllResponse(service.markAllRead())
  }
}
