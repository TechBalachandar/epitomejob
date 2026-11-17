package com.epitomejob.profile.dto

import com.epitomejob.profile.model.Profile
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** What the profile endpoints return. Controllers never expose the entity. */
data class ProfileResponse(
  val id: UUID,
  val userId: UUID,
  val headline: String?,
  val summary: String?,
  val location: String?,
  val workType: String?,
  val salaryMin: Int?,
  val salaryMax: Int?,
  val currency: String,
  val visibility: String,
  val skills: List<String>,
  val h1bClockStartDate: LocalDate?,
  val completeness: Int,
  val createdAt: Instant,
  val updatedAt: Instant,
  val linkedinUrl: String?,
  val workAuthorization: String?
) {

  companion object {
    @JvmStatic
    fun from(p: Profile): ProfileResponse {
      val skills = p.skills.sorted()
      return ProfileResponse(
        p.id, p.userId, p.headline, p.summary,
        p.location, p.workType, p.salaryMin, p.salaryMax,
        p.currency, p.visibility, skills, p.h1bClockStartDate,
        p.completeness, p.createdAt, p.updatedAt,
        p.linkedinUrl, p.workAuthorization
      )
    }
  }
}
