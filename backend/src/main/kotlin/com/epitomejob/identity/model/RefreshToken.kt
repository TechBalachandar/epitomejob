package com.epitomejob.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens", schema = "identity")
class RefreshToken(
  @Column(nullable = false)
  var userId: UUID = UUID.randomUUID(),

  /** SHA-256 of the token, never the plain token. */
  @Column(nullable = false)
  var tokenHash: String = "",

  @Column(nullable = false)
  var expiresAt: Instant = Instant.now(),

  @Id
  var id: UUID = UUID.randomUUID(),

  @Column(nullable = false)
  var revoked: Boolean = false,

  @Column(nullable = false)
  var createdAt: Instant = Instant.now()
)
