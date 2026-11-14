package com.epitomejob.profile.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.profile.dto.ProfileResponse;
import com.epitomejob.profile.dto.ProfileUpdateRequest;
import com.epitomejob.profile.model.Profile;
import com.epitomejob.profile.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

@Service
public class ProfileService {

  private final ProfileRepository profiles;

  public ProfileService(ProfileRepository profiles) {
    this.profiles = profiles;
  }

  @Transactional
  public ProfileResponse upsertMe(ProfileUpdateRequest incoming) {
    UUID userId = SecurityUtil.currentUserId();
    // Fail fast, before loading the profile (same as visibility below):
    // a bad LinkedIn URL or work authorisation is a 400 with no side effects.
    if (incoming.linkedinUrl() != null
        && !incoming.linkedinUrl().startsWith("http://")
        && !incoming.linkedinUrl().startsWith("https://")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.INVALID_LINKEDIN_URL);
    }
    if (incoming.workAuthorization() != null
        && !AppConstants.Profile.WORK_AUTHORIZATIONS.contains(incoming.workAuthorization())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.INVALID_WORK_AUTHORIZATION);
    }
    if (incoming.visibility() != null
        && !AppConstants.Profile.VISIBILITY_PUBLIC.equals(incoming.visibility())
        && !AppConstants.Profile.VISIBILITY_PRIVATE.equals(incoming.visibility())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.INVALID_VISIBILITY);
    }
    Profile profile = profiles.findByUserId(userId).orElseGet(() -> new Profile(userId));
    if (incoming.headline() != null) profile.setHeadline(incoming.headline());
    if (incoming.summary() != null) profile.setSummary(incoming.summary());
    if (incoming.location() != null) profile.setLocation(incoming.location());
    if (incoming.workType() != null) profile.setWorkType(incoming.workType());
    if (incoming.salaryMin() != null) profile.setSalaryMin(incoming.salaryMin());
    if (incoming.salaryMax() != null) profile.setSalaryMax(incoming.salaryMax());
    if (incoming.skills() != null) {
      profile.setSkills(incoming.skills().stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .distinct()
          .toList());
    }
    if (incoming.h1bClockStartDate() != null) profile.setH1bClockStartDate(incoming.h1bClockStartDate());
    if (incoming.currency() != null) profile.setCurrency(incoming.currency());
    if (incoming.visibility() != null) profile.setVisibility(incoming.visibility());
    if (incoming.linkedinUrl() != null) profile.setLinkedinUrl(incoming.linkedinUrl());
    if (incoming.workAuthorization() != null) profile.setWorkAuthorization(incoming.workAuthorization());
    profile.setCompleteness(profile.computeCompleteness());
    profile.touch();
    return ProfileResponse.from(profiles.save(profile));
  }

  @Transactional(readOnly = true)
  public ProfileResponse getMe() {
    UUID userId = SecurityUtil.currentUserId();
    return ProfileResponse.from(
        profiles.findByUserId(userId).orElseGet(() -> new Profile(userId)));
  }
}
