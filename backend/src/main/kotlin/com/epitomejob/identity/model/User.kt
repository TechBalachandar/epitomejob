package com.epitomejob.identity.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users", schema = "identity")
class User(
  @Column(nullable = false, unique = true)
  var email: String = "",

  var fullName: String = "",

  @Id
  var id: UUID = UUID.randomUUID(),

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var role: Role = Role.USER,

  @Column(nullable = false)
  var status: String = AppConstants.User.STATUS_ACTIVE,

  @Column(nullable = false)
  var emailVerified: Boolean = false,

  @Column(nullable = false)
  var createdAt: Instant = Instant.now(),

  @Column(nullable = false)
  var updatedAt: Instant = Instant.now()
) {

  /** BCrypt hash. Null for Google-only accounts (Phase 1). */
  var passwordHash: String? = null
    set(value) {
      field = value
      updatedAt = Instant.now()
    }

  var lastLoginAt: Instant? = null
    set(value) {
      field = value
      updatedAt = Instant.now()
    }

  /**
   * Java-compatible 3-arg constructor (used by [com.epitomejob.identity.service.AuthService],
   * [com.epitomejob.identity.config.AuthSeeder] and Java callers such as
   * `AppUserDetailsServiceTest`). The primary constructor keeps every field
   * defaulted so the kotlin-jpa plugin can generate the no-arg constructor
   * Hibernate needs.
   */
  constructor(email: String, passwordHash: String?, fullName: String) : this(
    email = email,
    fullName = fullName
  ) {
    this.passwordHash = passwordHash
  }
}
