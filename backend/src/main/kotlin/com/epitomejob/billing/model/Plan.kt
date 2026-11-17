package com.epitomejob.billing.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * A purchasable plan (W4 contract §1b). Seeded by V5__billing.sql; the
 * service never invents plans. `currency` is CHAR(3) in the migration,
 * so the JDBC type is declared explicitly — without it Hibernate's
 * ddl-auto=validate rejects a String mapped to a CHAR column.
 */
@Entity
@Table(name = "plans", schema = "billing")
class Plan(
  @Id
  @Column(name = "code", nullable = false)
  var code: String,

  @Column(name = "name", nullable = false)
  var name: String,

  @Column(name = "price_cents", nullable = false)
  var priceCents: Int,

  @Column(name = "currency", nullable = false)
  @JdbcTypeCode(SqlTypes.CHAR)
  var currency: String = AppConstants.Profile.DEFAULT_CURRENCY,

  @Column(name = "duration_days")
  var durationDays: Int?,

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  var description: String,

  @Column(name = "active", nullable = false)
  var active: Boolean = true
)
