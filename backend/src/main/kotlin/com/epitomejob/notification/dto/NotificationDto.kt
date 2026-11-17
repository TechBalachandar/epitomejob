package com.epitomejob.notification.dto

import com.epitomejob.notification.model.Notification
import java.time.Instant

/**
 * Notification shape, exact field names per the M13 contract §1h.
 * `id` is the UUID string for stored rows, the `clock-*` id
 * for derived warnings. `createdAt` is an ISO-8601 instant string.
 */
data class NotificationDto(
  val id: String,
  val type: String,
  val title: String,
  val body: String,
  val link: String,
  val isRead: Boolean,
  val derived: Boolean,
  val createdAt: String
) {

  companion object {
    @JvmStatic
    fun from(n: Notification): NotificationDto {
      return NotificationDto(n.id.toString(), n.type, n.title, n.body,
        n.link, n.isRead, false, n.createdAt.toString())
    }

    /** Derived (never stored) warning — always unread, created "now". */
    @JvmStatic
    fun derived(id: String, type: String, title: String, body: String,
                link: String): NotificationDto {
      return NotificationDto(id, type, title, body, link, false, true,
        Instant.now().toString())
    }
  }
}
