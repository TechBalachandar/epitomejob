package com.epitomejob.matching.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.exception.ResumeNotReadyException
import com.epitomejob.common.util.SecurityUtil
import com.epitomejob.job.dto.JobDto
import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import com.epitomejob.matching.dto.MatchDto
import com.epitomejob.matching.dto.MatchReasonDto
import com.epitomejob.profile.model.Profile
import com.epitomejob.profile.repository.ProfileRepository
import com.epitomejob.resume.model.Resume
import com.epitomejob.resume.repository.ResumeRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

/**
 * Deterministic job matching (W2 contract §4). Total 100: skills 50,
 * location 10, work type 10, salary 15, sponsorship 15.
 *
 * Candidate text = latest READY resume parsedText (else "") + profile
 * headline + profile summary + profile skills, normalized (lowercase,
 * collapsed whitespace). With a resumeId (W6 contract §2c) THAT resume's
 * parsedText is used instead of the latest READY one. A job skill matches
 * if its normalized full string
 * is a substring of the candidate text, or its first whitespace-separated
 * token is.
 */
@Service
class MatchingService(
  private val jobs: JobRepository,
  private val profiles: ProfileRepository,
  private val resumes: ResumeRepository
) {

  @Transactional(readOnly = true)
  fun matchesForMe(resumeId: UUID?): List<MatchDto> {
    val userId = SecurityUtil.currentUserId()
    val candidate = loadCandidate(userId, resumeId)
    return jobs.findAll()
      .map { score(it, candidate) }
      .sortedByDescending { it.score }
      .take(AppConstants.Matching.MAX_RESULTS)
  }

  @Transactional(readOnly = true)
  fun matchForJob(jobId: UUID, resumeId: UUID?): MatchDto {
    val userId = SecurityUtil.currentUserId()
    val job = jobs.findById(jobId)
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.JOB_NOT_FOUND)
      }
    return score(job, loadCandidate(userId, resumeId))
  }

  /** Scores one job. Package-visible so unit tests can drive it directly. */
  internal fun score(job: Job, candidate: Candidate): MatchDto {
    val reasons = ArrayList<MatchReasonDto>()
    val gaps = ArrayList<String>()
    var total = 0

    // Skills: 50 max, round(50 * matched / total).
    val jobSkills = job.skills
    val matched = ArrayList<String>()
    for (skill in jobSkills) {
      if (skillMatches(skill, candidate.text)) {
        matched.add(skill)
      } else {
        gaps.add("Missing skill: $skill")
      }
    }
    val skillsPoints = if (jobSkills.isEmpty()) {
      0
    } else {
      Math.round(AppConstants.Matching.SKILLS_MAX * matched.size.toDouble() / jobSkills.size).toInt()
    }
    if (skillsPoints > 0) {
      reasons.add(
        MatchReasonDto("Skills: ${matched.joinToString(", ")} (+$skillsPoints)", skillsPoints)
      )
    }
    total += skillsPoints

    // Location: 10 if the job is remote, or locations overlap either way.
    val locationPoints = if (locationMatches(job, candidate.profile)) {
      AppConstants.Matching.LOCATION_MAX
    } else {
      0
    }
    if (locationPoints > 0) {
      reasons.add(reason(AppConstants.Matching.LOCATION_MATCH_REASON, locationPoints))
    } else {
      gaps.add("Location: ${job.location}")
    }
    total += locationPoints

    // Work type: 10 on exact match, 5 partial when the profile has none set.
    val profile = candidate.profile
    val workTypePoints: Int
    if (profile?.workType != null && profile.workType.equals(job.workType, ignoreCase = true)) {
      workTypePoints = AppConstants.Matching.WORK_TYPE_MAX
      reasons.add(reason(AppConstants.Matching.WORK_TYPE_MATCH_REASON, workTypePoints))
    } else if (profile == null || profile.workType == null) {
      workTypePoints = AppConstants.Matching.WORK_TYPE_PARTIAL
      reasons.add(MatchReasonDto(AppConstants.Matching.WORK_TYPE_NOT_SET_REASON, workTypePoints))
    } else {
      workTypePoints = 0
      gaps.add("Work type: ${job.workType}")
    }
    total += workTypePoints

    // Salary: 15 if job min >= profile min; 7 if ranges overlap; 7 neutral if no minimum set.
    var salaryPoints = 0
    val profileMin = profile?.salaryMin
    val profileMax = profile?.salaryMax
    if (profileMin == null) {
      salaryPoints = AppConstants.Matching.SALARY_PARTIAL
      reasons.add(MatchReasonDto(AppConstants.Matching.SALARY_NOT_SET_REASON, salaryPoints))
    } else if (job.salaryMin != null && job.salaryMin >= profileMin) {
      salaryPoints = AppConstants.Matching.SALARY_MAX
      reasons.add(reason(AppConstants.Matching.SALARY_MEETS_REASON, salaryPoints))
    } else if (rangesOverlap(job, profileMin, profileMax)) {
      salaryPoints = AppConstants.Matching.SALARY_PARTIAL
      reasons.add(reason(AppConstants.Matching.SALARY_OVERLAP_REASON, salaryPoints))
    } else {
      if (job.salaryMax != null && job.salaryMax < profileMin) {
        gaps.add("Salary below your minimum")
      }
    }
    total += salaryPoints

    // Sponsorship: 15 if the job sponsors H-1B.
    val sponsorshipPoints = if (job.sponsorsH1b) AppConstants.Matching.SPONSORSHIP_MAX else 0
    if (sponsorshipPoints > 0) {
      reasons.add(reason(AppConstants.Matching.SPONSORSHIP_REASON, sponsorshipPoints))
    } else {
      gaps.add("No H-1B sponsorship listed")
    }
    total += sponsorshipPoints

    return MatchDto(JobDto.from(job), total, reasons, gaps, candidate.source)
  }

  /**
   * Loads the candidate for a user (W6 contract §2c). With [resumeId]
   * null the latest READY resume is used (unchanged behaviour); with a
   * resumeId THAT resume is used — not found or not owned is a 404, not
   * READY is a 400 `RESUME_NOT_READY`. Package-visible so unit tests
   * can drive it directly, like [score].
   */
  internal fun loadCandidate(userId: UUID, resumeId: UUID?): Candidate {
    val profile: Optional<Profile> = profiles.findByUserId(userId)
    val resume: Optional<Resume>
    if (resumeId != null) {
      val chosen = resumes.findById(resumeId)
        .filter { it.userId == userId }
        .orElseThrow {
          ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.RESUME_NOT_FOUND)
        }
      if (AppConstants.Resume.STATUS_READY != chosen.status) {
        throw ResumeNotReadyException()
      }
      resume = Optional.of(chosen)
    } else {
      resume = resumes.findByUserIdOrderByCreatedAtDesc(userId)
        .firstOrNull { AppConstants.Resume.STATUS_READY == it.status }
        .let { Optional.ofNullable(it) }
    }
    val resumeText = resume.map { it.parsedText }.orElse("")
    val source = if (resume.isPresent) {
      AppConstants.Matching.SOURCE_RESUME
    } else {
      AppConstants.Matching.SOURCE_PROFILE
    }
    val text = normalize(resumeText) + " " +
      normalize(profile.map { it.headline }.orElse(null)) + " " +
      normalize(profile.map { it.summary }.orElse(null)) + " " +
      normalize(profile.map { p -> p.skills.joinToString(" ") }.orElse(null))
    return Candidate(profile.orElse(null), normalize(text), source)
  }

  private fun skillMatches(skill: String, normalizedCandidate: String): Boolean {
    val normalizedSkill = normalize(skill)
    if (normalizedSkill.isEmpty()) {
      return false
    }
    if (normalizedCandidate.contains(normalizedSkill)) {
      return true
    }
    val firstToken = normalizedSkill.split(AppConstants.Matching.WHITESPACE_REGEX.toRegex())[0]
    return normalizedCandidate.contains(firstToken)
  }

  private fun locationMatches(job: Job, profile: Profile?): Boolean {
    if (job.location.lowercase().contains(AppConstants.Matching.LOCATION_REMOTE.lowercase())) {
      return true
    }
    val profileLocation = profile?.location ?: return false
    val jobLocation = job.location.lowercase()
    val profileLocationLower = profileLocation.lowercase()
    return jobLocation.contains(profileLocationLower) || profileLocationLower.contains(jobLocation)
  }

  private fun rangesOverlap(job: Job, profileMin: Int, profileMax: Int?): Boolean {
    if (job.salaryMax == null || job.salaryMax < profileMin) {
      return false
    }
    return profileMax == null || job.salaryMin == null || job.salaryMin <= profileMax
  }

  private fun reason(label: String, points: Int): MatchReasonDto {
    return MatchReasonDto("$label (+$points)", points)
  }

  companion object {
    internal fun normalize(value: String?): String {
      if (value == null) {
        return ""
      }
      return value.lowercase().replace(AppConstants.Matching.WHITESPACE_REGEX.toRegex(), " ").trim()
    }
  }

  /** The candidate's normalized text, profile (nullable), and match source. */
  data class Candidate(val profile: Profile?, val text: String, val source: String)
}
