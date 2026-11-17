package com.epitomejob.notification.dto

/** Page shape per the M13 contract §1h. Page numbers are 0-based. */
data class NotificationPage(
  val content: List<NotificationDto>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
  val totalPages: Int
)
