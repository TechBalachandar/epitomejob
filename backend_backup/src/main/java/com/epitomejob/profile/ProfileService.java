package com.epitomejob.profile;
import com.epitomejob.common.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class ProfileService {
  private final ProfileRepository profiles;
  public ProfileService(ProfileRepository profiles) { this.profiles = profiles; }
  @Transactional
  public Profile upsertMe(Profile incoming) {
    UUID userId = SecurityUtil.currentUserId();
    Profile profile = profiles.findByUserId(userId).orElseGet(() -> new Profile(userId));
    if (incoming.getHeadline() != null) profile.setHeadline(incoming.getHeadline());
    if (incoming.getSummary() != null) profile.setSummary(incoming.getSummary());
    if (incoming.getLocation() != null) profile.setLocation(incoming.getLocation());
    if (incoming.getWorkType() != null) profile.setWorkType(incoming.getWorkType());
    if (incoming.getSalaryMin() != null) profile.setSalaryMin(incoming.getSalaryMin());
    if (incoming.getSalaryMax() != null) profile.setSalaryMax(incoming.getSalaryMax());
    profile.setCompleteness(profile.computeCompleteness());
    profile.touch();
    return profiles.save(profile);
  }
  @Transactional(readOnly = true)
  public Profile getMe() {
    return profiles.findByUserId(SecurityUtil.currentUserId()).orElseGet(() -> new Profile(SecurityUtil.currentUserId()));
  }
}
