package com.epitomejob.notification.dto;

import com.epitomejob.notification.model.Notification;

/**
 * Notification shape, exact field names per the M13 contract §1h.
 * {@code id} is the UUID string for stored rows, the {@code clock-*} id
 * for derived warnings. {@code createdAt} is an ISO-8601 instant string.
 */
public record NotificationDto(
    String id,
    String type,
    String title,
    String body,
    String link,
    boolean isRead,
    boolean derived,
    String createdAt) {

  public static NotificationDto from(Notification n) {
    return new NotificationDto(n.getId().toString(), n.getType(), n.getTitle(), n.getBody(),
        n.getLink(), n.isRead(), false, n.getCreatedAt().toString());
  }

  /** Derived (never stored) warning — always unread, created "now". */
  public static NotificationDto derived(String id, String type, String title, String body,
                                        String link) {
    return new NotificationDto(id, type, title, body, link, false, true,
        java.time.Instant.now().toString());
  }
}
