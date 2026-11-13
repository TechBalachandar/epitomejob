package com.epitomejob.notification.controller;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.notification.dto.NotificationDto;
import com.epitomejob.notification.dto.NotificationPage;
import com.epitomejob.notification.dto.ReadAllResponse;
import com.epitomejob.notification.dto.UnreadCount;
import com.epitomejob.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * In-app notifications (M13). The owner always comes from the token —
 * no userId in any path or body. Another user's id is 404, never 403.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping("/me")
  public NotificationPage listMine(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.listMine(page, size);
  }

  @GetMapping("/unread-count")
  public UnreadCount unreadCount() {
    return new UnreadCount(service.unreadCount());
  }

  /**
   * A derived {@code clock-*} id is 404 — derived warnings cannot be
   * marked read. A non-UUID id is likewise 404, never 400.
   */
  @PostMapping("/{id}/read")
  public NotificationDto markRead(@PathVariable String id) {
    UUID uuid;
    try {
      uuid = UUID.fromString(id);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          AppConstants.Errors.NOTIFICATION_NOT_FOUND);
    }
    return service.markRead(uuid);
  }

  @PostMapping("/read-all")
  public ReadAllResponse markAllRead() {
    return new ReadAllResponse(service.markAllRead());
  }
}
