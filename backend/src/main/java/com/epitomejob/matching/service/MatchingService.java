package com.epitomejob.matching.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.job.dto.JobDto;
import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import com.epitomejob.common.util.SecurityUtil;
import com.epitomejob.matching.dto.MatchDto;
import com.epitomejob.matching.dto.MatchReasonDto;
import com.epitomejob.profile.model.Profile;
import com.epitomejob.profile.repository.ProfileRepository;
import com.epitomejob.resume.model.Resume;
import com.epitomejob.resume.repository.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic job matching (W2 contract §4). Total 100: skills 50,
 * location 10, work type 10, salary 15, sponsorship 15.
 *
 * Candidate text = latest READY resume parsedText (else "") + profile
 * headline + profile summary, normalized (lowercase, collapsed whitespace).
 * A job skill matches if its normalized full string is a substring of the
 * candidate text, or its first whitespace-separated token is.
 */
@Service
public class MatchingService {

  private final JobRepository jobs;
  private final ProfileRepository profiles;
  private final ResumeRepository resumes;

  public MatchingService(JobRepository jobs, ProfileRepository profiles, ResumeRepository resumes) {
    this.jobs = jobs;
    this.profiles = profiles;
    this.resumes = resumes;
  }

  @Transactional(readOnly = true)
  public List<MatchDto> matchesForMe() {
    UUID userId = SecurityUtil.currentUserId();
    Candidate candidate = loadCandidate(userId);
    return jobs.findAll().stream()
        .map(job -> score(job, candidate))
        .sorted(Comparator.comparingInt(MatchDto::score).reversed())
        .limit(AppConstants.Matching.MAX_RESULTS)
        .toList();
  }

  @Transactional(readOnly = true)
  public MatchDto matchForJob(UUID jobId) {
    UUID userId = SecurityUtil.currentUserId();
    Job job = jobs.findById(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.JOB_NOT_FOUND));
    return score(job, loadCandidate(userId));
  }

  /** Scores one job. Package-visible so unit tests can drive it directly. */
  MatchDto score(Job job, Candidate candidate) {
    List<MatchReasonDto> reasons = new ArrayList<>();
    List<String> gaps = new ArrayList<>();
    int total = 0;

    // Skills: 50 max, round(50 * matched / total).
    List<String> jobSkills = job.getSkills();
    List<String> matched = new ArrayList<>();
    for (String skill : jobSkills) {
      if (skillMatches(skill, candidate.text())) {
        matched.add(skill);
      } else {
        gaps.add("Missing skill: " + skill);
      }
    }
    int skillsPoints = jobSkills.isEmpty() ? 0
        : (int) Math.round(AppConstants.Matching.SKILLS_MAX * (double) matched.size() / jobSkills.size());
    if (skillsPoints > 0) {
      reasons.add(new MatchReasonDto(
          "Skills: " + String.join(", ", matched) + " (+" + skillsPoints + ")", skillsPoints));
    }
    total += skillsPoints;

    // Location: 10 if the job is remote, or locations overlap either way.
    int locationPoints = locationMatches(job, candidate.profile()) ? AppConstants.Matching.LOCATION_MAX : 0;
    if (locationPoints > 0) {
      reasons.add(reason(AppConstants.Matching.LOCATION_MATCH_REASON, locationPoints));
    } else {
      gaps.add("Location: " + job.getLocation());
    }
    total += locationPoints;

    // Work type: 10 on exact match, 5 partial when the profile has none set.
    int workTypePoints;
    if (candidate.profile() != null && candidate.profile().getWorkType() != null
        && candidate.profile().getWorkType().equalsIgnoreCase(job.getWorkType())) {
      workTypePoints = AppConstants.Matching.WORK_TYPE_MAX;
      reasons.add(reason(AppConstants.Matching.WORK_TYPE_MATCH_REASON, workTypePoints));
    } else if (candidate.profile() == null || candidate.profile().getWorkType() == null) {
      workTypePoints = AppConstants.Matching.WORK_TYPE_PARTIAL;
      reasons.add(new MatchReasonDto(AppConstants.Matching.WORK_TYPE_NOT_SET_REASON, workTypePoints));
    } else {
      workTypePoints = 0;
      gaps.add("Work type: " + job.getWorkType());
    }
    total += workTypePoints;

    // Salary: 15 if job min >= profile min; 7 if ranges overlap; 7 neutral if no minimum set.
    int salaryPoints = 0;
    Integer profileMin = candidate.profile() == null ? null : candidate.profile().getSalaryMin();
    Integer profileMax = candidate.profile() == null ? null : candidate.profile().getSalaryMax();
    if (profileMin == null) {
      salaryPoints = AppConstants.Matching.SALARY_PARTIAL;
      reasons.add(new MatchReasonDto(AppConstants.Matching.SALARY_NOT_SET_REASON, salaryPoints));
    } else if (job.getSalaryMin() != null && job.getSalaryMin() >= profileMin) {
      salaryPoints = AppConstants.Matching.SALARY_MAX;
      reasons.add(reason(AppConstants.Matching.SALARY_MEETS_REASON, salaryPoints));
    } else if (rangesOverlap(job, profileMin, profileMax)) {
      salaryPoints = AppConstants.Matching.SALARY_PARTIAL;
      reasons.add(reason(AppConstants.Matching.SALARY_OVERLAP_REASON, salaryPoints));
    } else {
      if (job.getSalaryMax() != null && job.getSalaryMax() < profileMin) {
        gaps.add("Salary below your minimum");
      }
    }
    total += salaryPoints;

    // Sponsorship: 15 if the job sponsors H-1B.
    int sponsorshipPoints = job.isSponsorsH1b() ? AppConstants.Matching.SPONSORSHIP_MAX : 0;
    if (sponsorshipPoints > 0) {
      reasons.add(reason(AppConstants.Matching.SPONSORSHIP_REASON, sponsorshipPoints));
    } else {
      gaps.add("No H-1B sponsorship listed");
    }
    total += sponsorshipPoints;

    return new MatchDto(JobDto.from(job), total, reasons, gaps, candidate.source());
  }

  private Candidate loadCandidate(UUID userId) {
    Optional<Profile> profile = profiles.findByUserId(userId);
    Optional<Resume> resume = resumes.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(r -> AppConstants.Resume.STATUS_READY.equals(r.getStatus()))
        .findFirst();
    String resumeText = resume.map(Resume::getParsedText).orElse("");
    String source = resume.isPresent()
        ? AppConstants.Matching.SOURCE_RESUME : AppConstants.Matching.SOURCE_PROFILE;
    String text = normalize(resumeText) + " "
        + normalize(profile.map(Profile::getHeadline).orElse(null)) + " "
        + normalize(profile.map(Profile::getSummary).orElse(null));
    return new Candidate(profile.orElse(null), normalize(text), source);
  }

  private boolean skillMatches(String skill, String normalizedCandidate) {
    String normalizedSkill = normalize(skill);
    if (normalizedSkill.isEmpty()) {
      return false;
    }
    if (normalizedCandidate.contains(normalizedSkill)) {
      return true;
    }
    String firstToken = normalizedSkill.split(AppConstants.Matching.WHITESPACE_REGEX)[0];
    return normalizedCandidate.contains(firstToken);
  }

  private boolean locationMatches(Job job, Profile profile) {
    if (job.getLocation() != null
        && job.getLocation().toLowerCase().contains(AppConstants.Matching.LOCATION_REMOTE.toLowerCase())) {
      return true;
    }
    if (profile == null || profile.getLocation() == null || job.getLocation() == null) {
      return false;
    }
    String jobLocation = job.getLocation().toLowerCase();
    String profileLocation = profile.getLocation().toLowerCase();
    return jobLocation.contains(profileLocation) || profileLocation.contains(jobLocation);
  }

  private boolean rangesOverlap(Job job, Integer profileMin, Integer profileMax) {
    if (job.getSalaryMax() == null || job.getSalaryMax() < profileMin) {
      return false;
    }
    return profileMax == null
        || job.getSalaryMin() == null || job.getSalaryMin() <= profileMax;
  }

  private MatchReasonDto reason(String label, int points) {
    return new MatchReasonDto(label + " (+" + points + ")", points);
  }

  static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.toLowerCase().replaceAll(AppConstants.Matching.WHITESPACE_REGEX, " ").trim();
  }

  /** The candidate's normalized text, profile (nullable), and match source. */
  record Candidate(Profile profile, String text, String source) {
  }
}
