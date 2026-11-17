package com.epitomejob.approval.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "approvals", schema = "coach")
class Approval(
  @Column(nullable = false)
  var userId: UUID = UUID.randomUUID(),

  @Column(nullable = false)
  var action: String = "",

  @Column(nullable = false, columnDefinition = "TEXT")
  var payload: String = "",

  tokenHash: String = "",

  @Column(nullable = false)
  var expiresAt: Instant = Instant.now()
) {

  @Id
  var id: UUID = UUID.randomUUID()

  /**
   * SHA-256 of the approval token — the plain token is never stored, and the
   * hash itself is never exposed (no getter, matching the Java class).
   * The column keeps the Java field's name (`tokenHash`).
   */
  @Column(name = "tokenHash", nullable = false)
  private var tokenHashValue: String = tokenHash

  @Column(nullable = false)
  var status: String = AppConstants.Approval.STATUS_PENDING

  var decidedAt: Instant? = null
}
