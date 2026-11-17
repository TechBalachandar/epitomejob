package com.epitomejob.notification.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notifications", schema = "notification")
class Notification(
  @Id
  var id: UUID = UUID.randomUUID(),

  @Column(nullable = false)
  var userId: UUID,

  @Column(nullable = false)
  var type: String,

  @Column(nullable = false)
  var title: String,

  @Column(nullable = false, columnDefinition = "TEXT")
  var body: String,

  @Column(nullable = false)
  var link: String,

  @Column(nullable = false)
  var isRead: Boolean = false,

  @Column(nullable = false)
  var createdAt: Instant = Instant.now()
) {

  constructor(userId: UUID, type: String, title: String, body: String, link: String) : this(
    id = UUID.randomUUID(),
    userId = userId,
    type = type,
    title = title,
    body = body,
    link = link
  )
}
