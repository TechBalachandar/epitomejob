package com.epitomejob.profile.service;

import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.profile.dto.ProfileResponse;
import com.epitomejob.profile.dto.ProfileUpdateRequest;
import com.epitomejob.profile.model.Profile;
import com.epitomejob.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    Profile profile = profiles.findByUserId(userId).orElseGet(() -> new Profile(userId));
    if (incoming.headline() != null) profile.setHeadline(incoming.headline());
    if (incoming.summary() != null) profile.setSummary(incoming.summary());
    if (incoming.location() != null) profile.setLocation(incoming.location());
    if (incoming.workType() != null) profile.setWorkType(incoming.workType());
    if (incoming.salaryMin() != null) profile.setSalaryMin(incoming.salaryMin());
    if (incoming.salaryMax() != null) profile.setSalaryMax(incoming.salaryMax());
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
