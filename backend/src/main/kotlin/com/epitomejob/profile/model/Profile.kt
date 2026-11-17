package com.epitomejob.profile.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.LinkedHashSet
import java.util.UUID

@Entity
@Table(name = "profiles", schema = "profile")
class Profile(
  @Column(nullable = false, unique = true)
  var userId: UUID = UUID.randomUUID(),

  @Id
  var id: UUID = UUID.randomUUID(),

  var headline: String? = null,

  @Column(columnDefinition = "TEXT")
  var summary: String? = null,

  var location: String? = null,
  var workType: String? = null,
  var salaryMin: Int? = null,
  var salaryMax: Int? = null,

  var currency: String = AppConstants.Profile.DEFAULT_CURRENCY,

  @Column(nullable = false)
  var visibility: String = AppConstants.Profile.DEFAULT_VISIBILITY,

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
    name = "profile_skills", schema = "profile",
    joinColumns = [JoinColumn(name = "profile_id")]
  )
  @Column(name = "skill", nullable = false, length = 60)
  var skills: MutableCollection<String> = LinkedHashSet(),

  var h1bClockStartDate: LocalDate? = null,

  /** LinkedIn profile URL (W6 contract §2e): nullable, max 300 chars. */
  @Column(length = 300)
  var linkedinUrl: String? = null,

  /** Work authorisation (W6 contract §2e): one of the AppConstants.Profile WORK_AUTH_* values. */
  @Column(length = 20)
  var workAuthorization: String? = null,

  @Column(nullable = false)
  var completeness: Int = 0,

  @Column(nullable = false)
  var createdAt: Instant = Instant.now(),

  @Column(nullable = false)
  var updatedAt: Instant = Instant.now()
) {

  /**
   * Java-compatible 1-arg constructor (used by [com.epitomejob.profile.service.ProfileService]
   * and Java callers such as `MatchingServiceTest` and `NotificationServiceTest`).
   * The primary constructor keeps every field defaulted so the kotlin-jpa
   * plugin can generate the no-arg constructor Hibernate needs.
   */
  constructor(userId: UUID) : this(userId = userId, id = UUID.randomUUID())

  fun touch() {
    updatedAt = Instant.now()
  }

  /** Completeness: headline + summary + location + work type + salary = 20 points each. */
  // Skills and the H-1B clock date deliberately add NO completeness points:
  // completeness measures the five core profile fields only. Skills are an
  // additive signal for matching; the clock date is a wedge tracker.
  // (W6 contract §2e) The LinkedIn URL and work authorisation likewise add
  // NO points — they are display / eligibility fields, not completeness.
  fun computeCompleteness(): Int {
    var score = 0
    val points = AppConstants.Profile.COMPLETENESS_POINTS_PER_FIELD
    if (!headline.isNullOrBlank()) score += points
    if (!summary.isNullOrBlank()) score += points
    if (!location.isNullOrBlank()) score += points
    if (!workType.isNullOrBlank()) score += points
    if (salaryMin != null && salaryMax != null) score += points
    return score
  }
}
