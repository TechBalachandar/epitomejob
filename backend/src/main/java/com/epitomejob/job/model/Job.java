package com.epitomejob.job.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A seeded (or provider-supplied) job posting. Skills are stored as one TEXT
 * column, comma-separated (see {@link AppConstants.Job#SKILLS_SEPARATOR});
 * no skill in the seed data contains a comma.
 */
@Entity
@Table(name = "jobs", schema = "job")
public class Job {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String company;

  @Column(nullable = false)
  private String location;

  @Column(nullable = false)
  private String workType;

  private Integer salaryMin;
  private Integer salaryMax;

  @Column(nullable = false)
  private String currency = AppConstants.Job.DEFAULT_CURRENCY;

  // Explicit column name: Spring's naming rule inserts '_' before an
  // uppercase letter only when the next char is lowercase. In
  // "sponsorsH1b" the 'H' is followed by the digit '1', so Hibernate
  // expects "sponsorsh1b", but the V3 migration created "sponsors_h1b".
  @Column(name = "sponsors_h1b", nullable = false)
  private boolean sponsorsH1b;

  /** Comma-separated skills; use {@link #getSkills()} / {@link #setSkills(List)}. */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String skills = "";

  @Column(columnDefinition = "TEXT")
  private String summary;

  @Column(nullable = false)
  private String role;

  /** Where the posting was aggregated from (W6 contract §2d). Defaults to
   * SEED for rows created before V8; the setter validates the value. */
  @Column(nullable = false, length = 30)
  private String source = AppConstants.Job.SOURCE_SEED;

  /** Apply link back to the source portal (W6 contract §2d). Nullable; when
   * present it must start with http:// or https:// (see setter). */
  @Column(length = 500)
  private String sourceUrl;

  @Column(nullable = false)
  private Instant postedAt = Instant.now();

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  protected Job() {}

  public Job(String title, String company, String location, String workType,
             Integer salaryMin, Integer salaryMax, boolean sponsorsH1b,
             List<String> skills, String summary, String role, Instant postedAt) {
    this.title = title;
    this.company = company;
    this.location = location;
    this.workType = workType;
    this.salaryMin = salaryMin;
    this.salaryMax = salaryMax;
    this.sponsorsH1b = sponsorsH1b;
    setSkills(skills);
    this.summary = summary;
    this.role = role;
    this.postedAt = postedAt;
  }

  public UUID getId() { return id; }
  public String getTitle() { return title; }
  public String getCompany() { return company; }
  public String getLocation() { return location; }
  public String getWorkType() { return workType; }
  public Integer getSalaryMin() { return salaryMin; }
  public Integer getSalaryMax() { return salaryMax; }
  public String getCurrency() { return currency; }
  public boolean isSponsorsH1b() { return sponsorsH1b; }
  public String getSummary() { return summary; }
  public String getRole() { return role; }
  public String getSource() { return source; }
  public void setSource(String source) {
    if (source == null || !AppConstants.Job.SOURCES.contains(source)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.INVALID_JOB_SOURCE);
    }
    this.source = source;
  }
  public String getSourceUrl() { return sourceUrl; }
  public void setSourceUrl(String sourceUrl) {
    if (sourceUrl != null && !sourceUrl.startsWith("http://")
        && !sourceUrl.startsWith("https://")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.INVALID_JOB_SOURCE_URL);
    }
    this.sourceUrl = sourceUrl;
  }
  public Instant getPostedAt() { return postedAt; }
  public Instant getCreatedAt() { return createdAt; }

  public List<String> getSkills() {
    if (skills == null || skills.isBlank()) {
      return List.of();
    }
    return Arrays.stream(skills.split(AppConstants.Job.SKILLS_SEPARATOR))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public void setSkills(List<String> skills) {
    this.skills = skills == null ? "" : String.join(AppConstants.Job.SKILLS_SEPARATOR, skills);
  }
}
