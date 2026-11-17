package com.epitomejob.profile.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDate

/** Partial update: every field is optional, null means "leave unchanged". */
data class ProfileUpdateRequest(
  @field:Size(max = 200) val headline: String?,
  @field:Size(max = 2000) val summary: String?,
  @field:Size(max = 120) val location: String?,
  @field:Size(max = 20) val workType: String?,
  @field:PositiveOrZero val salaryMin: Int?,
  @field:PositiveOrZero val salaryMax: Int?,
  @field:Size(max = 30) val skills: List<@Size(max = 60) String>?,
  val h1bClockStartDate: LocalDate?,
  @field:Pattern(regexp = "^[A-Z]{3}$") val currency: String?,
  val visibility: String?,
  @field:Size(max = 300) val linkedinUrl: String?,
  val workAuthorization: String?
) {
  /**
   * Per-skill length: Kotlin does not emit a type-use annotation such as
   * `List<@Size(max = 60) String>` into the class file (it lives only in
   * Kotlin metadata, which Hibernate Validator never reads), so the element
   * constraint above is dead. Enforce it as a bean-level @AssertTrue instead.
   */
  @JsonIgnore
  @AssertTrue(message = "skills: each skill must be at most 60 characters")
  fun isSkillsValid(): Boolean = skills == null || skills.all { it.length <= 60 }
}
