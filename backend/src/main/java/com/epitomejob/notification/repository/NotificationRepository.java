package com.epitomejob.notification.repository;

import com.epitomejob.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  Page<Notification> findByUserId(UUID userId, Pageable pageable);
  long countByUserIdAndIsReadFalse(UUID userId);
  List<Notification> findByUserIdAndIsReadFalse(UUID userId);
}
