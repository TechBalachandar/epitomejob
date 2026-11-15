package com.epitomejob.notification.dto

/** GET /notifications/unread-count response, per the M13 contract §1g. */
data class UnreadCount(val unreadCount: Long)
