package com.epitomejob.notification.dto;

import java.util.List;

/** Page shape per the M13 contract §1h. Page numbers are 0-based. */
public record NotificationPage(
    List<NotificationDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {
}
