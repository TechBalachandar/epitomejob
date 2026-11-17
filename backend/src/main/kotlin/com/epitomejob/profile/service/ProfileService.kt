package com.epitomejob.profile.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.util.SecurityUtil
import com.epitomejob.profile.dto.ProfileResponse
import com.epitomejob.profile.dto.ProfileUpdateRequest
import com.epitomejob.profile.model.Profile
import com.epitomejob.profile.repository.ProfileRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ProfileService(private val profiles: ProfileRepository) {

  @Transactional
  fun upsertMe(incoming: ProfileUpdateRequest): ProfileResponse {
    val userId = SecurityUtil.currentUserId()
    // Fail fast, before loading the profile (same as visibility below):
    // a bad LinkedIn URL or work authorisation is a 400 with no side effects.
    val linkedinUrl = incoming.linkedinUrl
    if (linkedinUrl != null
      && !linkedinUrl.startsWith("http://")
      && !linkedinUrl.startsWith("https://")
    ) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_LINKEDIN_URL)
    }
    val workAuthorization = incoming.workAuthorization
    if (workAuthorization != null
      && !AppConstants.Profile.WORK_AUTHORIZATIONS.contains(workAuthorization)
    ) {
      throw ResponseStatusException(
        HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_WORK_AUTHORIZATION
      )
    }
    val visibility = incoming.visibility
    if (visibility != null
      && AppConstants.Profile.VISIBILITY_PUBLIC != visibility
      && AppConstants.Profile.VISIBILITY_PRIVATE != visibility
    ) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.INVALID_VISIBILITY)
    }
    val profile = profiles.findByUserId(userId).orElseGet { Profile(userId) }
    if (incoming.headline != null) profile.headline = incoming.headline
    if (incoming.summary != null) profile.summary = incoming.summary
    if (incoming.location != null) profile.location = incoming.location
    if (incoming.workType != null) profile.workType = incoming.workType
    if (incoming.salaryMin != null) profile.salaryMin = incoming.salaryMin
    if (incoming.salaryMax != null) profile.salaryMax = incoming.salaryMax
    if (incoming.skills != null) {
      profile.skills = incoming.skills
        .filterNotNull()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toMutableSet()
    }
    if (incoming.h1bClockStartDate != null) profile.h1bClockStartDate = incoming.h1bClockStartDate
    if (incoming.currency != null) profile.currency = incoming.currency
    if (incoming.visibility != null) profile.visibility = incoming.visibility
    if (incoming.linkedinUrl != null) profile.linkedinUrl = incoming.linkedinUrl
    if (incoming.workAuthorization != null) profile.workAuthorization = incoming.workAuthorization
    profile.completeness = profile.computeCompleteness()
    profile.touch()
    return ProfileResponse.from(profiles.save(profile))
  }

  @Transactional(readOnly = true)
  fun getMe(): ProfileResponse {
    val userId = SecurityUtil.currentUserId()
    return ProfileResponse.from(
      profiles.findByUserId(userId).orElseGet { Profile(userId) }
    )
  }
}
