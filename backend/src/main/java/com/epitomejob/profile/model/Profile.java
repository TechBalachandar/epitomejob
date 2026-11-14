package com.epitomejob.profile.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "profiles", schema = "profile")
public class Profile {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false, unique = true)
  private UUID userId;

  private String headline;

  @Column(columnDefinition = "TEXT")
  private String summary;

  private String location;
  private String workType;
  private Integer salaryMin;
  private Integer salaryMax;

  private String currency = AppConstants.Profile.DEFAULT_CURRENCY;

  @Column(nullable = false)
  private String visibility = AppConstants.Profile.DEFAULT_VISIBILITY;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "profile_skills", schema = "profile",
      joinColumns = @JoinColumn(name = "profile_id"))
  @Column(name = "skill", nullable = false, length = 60)
  private Set<String> skills = new LinkedHashSet<>();

  private LocalDate h1bClockStartDate;

  /** LinkedIn profile URL (W6 contract §2e): nullable, max 300 chars. */
  @Column(length = 300)
  private String linkedinUrl;

  /** Work authorisation (W6 contract §2e): one of the AppConstants.Profile WORK_AUTH_* values. */
  @Column(length = 20)
  private String workAuthorization;

  @Column(nullable = false)
  private int completeness = 0;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  protected Profile() {}

  public Profile(UUID userId) {
    this.userId = userId;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getHeadline() { return headline; }
  public void setHeadline(String h) { this.headline = h; }
  public String getSummary() { return summary; }
  public void setSummary(String s) { this.summary = s; }
  public String getLocation() { return location; }
  public void setLocation(String l) { this.location = l; }
  public String getWorkType() { return workType; }
  public void setWorkType(String w) { this.workType = w; }
  public Integer getSalaryMin() { return salaryMin; }
  public void setSalaryMin(Integer v) { this.salaryMin = v; }
  public Integer getSalaryMax() { return salaryMax; }
  public void setSalaryMax(Integer v) { this.salaryMax = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String c) { this.currency = c; }
  public String getVisibility() { return visibility; }
  public void setVisibility(String v) { this.visibility = v; }
  public Set<String> getSkills() { return skills; }
  public void setSkills(Collection<String> skills) {
    this.skills = skills == null ? new LinkedHashSet<>() : new LinkedHashSet<>(skills);
  }
  public LocalDate getH1bClockStartDate() { return h1bClockStartDate; }
  public void setH1bClockStartDate(LocalDate d) { this.h1bClockStartDate = d; }
  public String getLinkedinUrl() { return linkedinUrl; }
  public void setLinkedinUrl(String u) { this.linkedinUrl = u; }
  public String getWorkAuthorization() { return workAuthorization; }
  public void setWorkAuthorization(String w) { this.workAuthorization = w; }
  public int getCompleteness() { return completeness; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void touch() { this.updatedAt = Instant.now(); }

  /** Completeness: headline + summary + location + work type + salary = 20 points each. */
  // Skills and the H-1B clock date deliberately add NO completeness points:
  // completeness measures the five core profile fields only. Skills are an
  // additive signal for matching; the clock date is a wedge tracker.
  // (W6 contract §2e) The LinkedIn URL and work authorisation likewise add
  // NO points — they are display / eligibility fields, not completeness.
  public int computeCompleteness() {
    int score = 0;
    int points = AppConstants.Profile.COMPLETENESS_POINTS_PER_FIELD;
    if (headline != null && !headline.isBlank()) score += points;
    if (summary != null && !summary.isBlank()) score += points;
    if (location != null && !location.isBlank()) score += points;
    if (workType != null && !workType.isBlank()) score += points;
    if (salaryMin != null && salaryMax != null) score += points;
    return score;
  }

  public void setCompleteness(int c) { this.completeness = c; }
}
