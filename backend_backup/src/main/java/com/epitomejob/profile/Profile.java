package com.epitomejob.profile;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "profiles", schema = "profile")
public class Profile {
  @Id private UUID id = UUID.randomUUID();
  @Column(nullable = false, unique = true) private UUID userId;
  private String headline;
  @Column(columnDefinition = "TEXT") private String summary;
  private String location;
  private String workType;
  private Integer salaryMin;
  private Integer salaryMax;
  private String currency = "USD";
  @Column(nullable = false) private String visibility = "PRIVATE";
  @Column(nullable = false) private int completeness = 0;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  @Column(nullable = false) private Instant updatedAt = Instant.now();
  protected Profile() {}
  public Profile(UUID userId) { this.userId = userId; }
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
  public int getCompleteness() { return completeness; }
  public void touch() { this.updatedAt = Instant.now(); }
  /** Completeness: headline + summary + location + work type + salary = 20 points each. */
  public int computeCompleteness() {
    int score = 0;
    if (headline != null && !headline.isBlank()) score += 20;
    if (summary != null && !summary.isBlank()) score += 20;
    if (location != null && !location.isBlank()) score += 20;
    if (workType != null && !workType.isBlank()) score += 20;
    if (salaryMin != null && salaryMax != null) score += 20;
    return score;
  }
  public void setCompleteness(int c) { this.completeness = c; }
}
