package com.epitomejob.matching.service;

import com.epitomejob.common.exception.ResumeNotReadyException;
import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import com.epitomejob.matching.dto.MatchDto;
import com.epitomejob.profile.model.Profile;
import com.epitomejob.profile.repository.ProfileRepository;
import com.epitomejob.resume.model.Resume;
import com.epitomejob.resume.repository.ResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock
  private JobRepository jobs;
  @Mock
  private ProfileRepository profiles;
  @Mock
  private ResumeRepository resumes;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private MatchingService service() {
    return new MatchingService(jobs, profiles, resumes);
  }

  private Job job(boolean sponsors, String location, String workType,
                  Integer min, Integer max, List<String> skills) {
    return new Job("Senior Software Engineer, Backend (Java)", "Northwind Health Systems",
        location, workType, min, max, sponsors, skills, "Backend APIs in Java.", "Backend",
        Instant.parse("2026-11-10T09:00:00Z"));
  }

  private Profile profile(String location, String workType, Integer min, Integer max) {
    Profile p = new Profile(USER_ID);
    p.setLocation(location);
    p.setWorkType(workType);
    p.setSalaryMin(min);
    p.setSalaryMax(max);
    return p;
  }

  private Resume readyResume(String parsedText) {
    Resume r = new Resume(USER_ID, "u/r.pdf", "resume.pdf", "pdf", 100);
    r.setStatus("READY");
    r.setParsedText(parsedText);
    return r;
  }

  private void stubCandidate(Profile p, Resume r) {
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.ofNullable(p));
    when(resumes.findByUserIdOrderByCreatedAtDesc(USER_ID))
        .thenReturn(r == null ? List.of() : List.of(r));
  }

  @Test
  void fullMatchScores100FromResume() {
    authenticate();
    Job j = job(true, "Reston, VA", "Hybrid", 145000, 175000,
        List.of("Java 21", "Spring Boot", "Kafka"));
    stubCandidate(profile("Reston, VA", "Hybrid", 140000, 180000),
        readyResume("Java 21 developer with Spring Boot and Kafka experience."));
    when(jobs.findAll()).thenReturn(List.of(j));

    List<MatchDto> matches = service().matchesForMe(null);

    assertEquals(1, matches.size());
    assertEquals(100, matches.get(0).score());
    assertEquals("RESUME", matches.get(0).source());
    assertTrue(matches.get(0).gaps().isEmpty());
    assertEquals("Skills: Java 21, Spring Boot, Kafka (+50)",
        matches.get(0).reasons().get(0).label());
  }

  @Test
  void noResumeUsesProfileSourceAndPartialCreditForUnsetFields() {
    authenticate();
    Job j = job(false, "McLean, VA", "Onsite", 120000, 150000, List.of("Java", "Oracle"));
    stubCandidate(null, null);
    when(jobs.findAll()).thenReturn(List.of(j));

    MatchDto match = service().matchesForMe(null).get(0);

    assertEquals("PROFILE", match.source());
    // skills 0 + location 0 + work type 5 + salary 7 + sponsorship 0 = 12
    assertEquals(12, match.score());
    assertTrue(match.gaps().contains("Missing skill: Java"));
    assertTrue(match.gaps().contains("Missing skill: Oracle"));
    assertTrue(match.gaps().contains("Location: McLean, VA"));
    assertTrue(match.gaps().contains("No H-1B sponsorship listed"));
    assertTrue(match.reasons().stream()
        .anyMatch(r -> r.label().equals("Work type not set — partial credit") && r.points() == 5));
    assertTrue(match.reasons().stream()
        .anyMatch(r -> r.label().equals("Salary preference not set — partial credit") && r.points() == 7));
  }

  @Test
  void remoteJobMatchesAnyLocationAndSalaryBelowMinimumIsAGap() {
    authenticate();
    Job j = job(false, "Remote (US)", "Remote", 100000, 150000, List.of("Python"));
    stubCandidate(profile("Reston, VA", "Onsite", 200000, 220000), null);
    when(jobs.findAll()).thenReturn(List.of(j));

    MatchDto match = service().matchesForMe(null).get(0);

    assertEquals("PROFILE", match.source());
    // skills 0 + location 10 + work type 0 + salary 0 + sponsorship 0 = 10
    assertEquals(10, match.score());
    assertTrue(match.gaps().contains("Salary below your minimum"));
    assertTrue(match.gaps().contains("Work type: Remote"));
  }

  @Test
  void salaryOverlapGivesPartialCredit() {
    authenticate();
    Job j = job(true, "Remote (US)", "Hybrid", 140000, 155000, List.of("Python"));
    stubCandidate(profile("Reston, VA", "Hybrid", 150000, 160000),
        readyResume("Python developer."));
    when(jobs.findAll()).thenReturn(List.of(j));

    MatchDto match = service().matchesForMe(null).get(0);

    // skills 50 + location 10 + work type 10 + salary 7 + sponsorship 15 = 92
    assertEquals(92, match.score());
    assertTrue(match.reasons().stream()
        .anyMatch(r -> r.label().equals("Salary ranges overlap (+7)") && r.points() == 7));
  }

  @Test
  void skillMatchesOnFirstToken() {
    authenticate();
    Job j = job(true, "Remote (US)", "Remote", 100000, 200000, List.of("Spring Boot"));
    // Candidate mentions only "spring", the first token of "Spring Boot".
    MatchingService svc = service();
    MatchingService.Candidate candidate =
        new MatchingService.Candidate(null, "spring developer", "PROFILE");

    MatchDto match = svc.score(j, candidate);

    assertTrue(match.reasons().stream().anyMatch(r -> r.points() == 50));
    assertTrue(match.gaps().isEmpty()
        || match.gaps().stream().noneMatch(g -> g.startsWith("Missing skill")));
  }

  @Test
  void matchesAreSortedScoreDesc() {
    authenticate();
    Job low = job(false, "McLean, VA", "Onsite", 120000, 150000, List.of("Cobol"));
    Job high = job(true, "Remote (US)", "Remote", 100000, 200000, List.of("Java"));
    stubCandidate(profile("Reston, VA", "Remote", 100000, 200000),
        readyResume("Java developer."));
    when(jobs.findAll()).thenReturn(List.of(low, high));

    List<MatchDto> matches = service().matchesForMe(null);

    assertEquals(high.getId(), matches.get(0).job().id());
    assertTrue(matches.get(0).score() > matches.get(1).score());
  }

  @Test
  void profileSkillRaisesScoreForMatchingJob() {
    authenticate();
    Job j = job(true, "Remote (US)", "Remote", 100000, 200000, List.of("LangGraph"));
    when(jobs.findAll()).thenReturn(List.of(j));

    Profile withoutSkill = profile("Reston, VA", "Remote", 100000, 200000);
    stubCandidate(withoutSkill, null);
    int scoreWithoutSkill = service().matchesForMe(null).get(0).score();

    Profile withSkill = profile("Reston, VA", "Remote", 100000, 200000);
    withSkill.setSkills(List.of("LangGraph"));
    stubCandidate(withSkill, null);
    int scoreWithSkill = service().matchesForMe(null).get(0).score();

    // skills 0 + location 10 + work type 10 + salary 15 + sponsorship 15 = 50
    assertEquals(50, scoreWithoutSkill);
    // skills 50 + location 10 + work type 10 + salary 15 + sponsorship 15 = 100
    assertEquals(100, scoreWithSkill);
    assertTrue(scoreWithSkill > scoreWithoutSkill,
        "adding a matching profile skill must raise the score, all else equal");
  }

  @Test
  void matchForJobMissingJobIs404() {
    authenticate();
    UUID jobId = UUID.randomUUID();
    when(jobs.findById(jobId)).thenReturn(Optional.empty());

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().matchForJob(jobId, null));
    assertEquals(404, ex.getStatusCode().value());
  }

  // ---- W6 contract §2c: per-resume matching ----

  private Job jobWithSkills(String title, List<String> skills) {
    return new Job(title, "Northwind Health Systems", "Remote (US)", "Remote",
        100000, 200000, true, skills, "Remote role.", "Backend",
        Instant.parse("2026-11-10T09:00:00Z"));
  }

  @Test
  void differentResumesGiveDifferentTopMatches() {
    authenticate();
    Job javaJob = jobWithSkills("Senior Software Engineer, Backend (Java)",
        List.of("Java", "Spring Boot"));
    Job androidJob = jobWithSkills("Senior Android Engineer",
        List.of("Kotlin", "Android"));
    // Profile is skill-free and identical for both calls, so only the resume
    // text can move the scores: non-skill points are equal (50) for both jobs.
    Profile p = profile("Reston, VA", "Remote", 100000, 200000);
    Resume javaResume = readyResume("Java Spring Boot developer with Kafka experience.");
    Resume androidResume = readyResume("Kotlin Android developer, Jetpack Compose.");
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(p));
    when(resumes.findById(javaResume.getId())).thenReturn(Optional.of(javaResume));
    when(resumes.findById(androidResume.getId())).thenReturn(Optional.of(androidResume));
    when(jobs.findAll()).thenReturn(List.of(javaJob, androidJob));

    List<MatchDto> javaMatches = service().matchesForMe(javaResume.getId());
    List<MatchDto> androidMatches = service().matchesForMe(androidResume.getId());

    assertEquals(javaJob.getId(), javaMatches.get(0).job().id());
    assertEquals(100, javaMatches.get(0).score());
    assertEquals(androidJob.getId(), androidMatches.get(0).job().id());
    assertEquals(100, androidMatches.get(0).score());
  }

  @Test
  void matchesForMeWithWrongOwnerResumeIs404() {
    authenticate();
    Resume someoneElses = new Resume(UUID.randomUUID(), "u/r.pdf", "resume.pdf", "pdf", 100);
    someoneElses.setStatus("READY");
    someoneElses.setParsedText("Java developer.");
    // Only the resume lookup is stubbed: the 404 throws before the profile
    // or the jobs are ever read, so stubbing them would be unused.
    when(resumes.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().matchesForMe(someoneElses.getId()));
    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void matchesForMeWithMissingResumeIs404() {
    authenticate();
    UUID missing = UUID.randomUUID();
    when(resumes.findById(missing)).thenReturn(Optional.empty());

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().matchesForMe(missing));
    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void matchesForMeWithNotReadyResumeIs400() {
    authenticate();
    Resume uploaded = new Resume(USER_ID, "u/r.pdf", "resume.pdf", "pdf", 100);
    // Default status is UPLOADED, not READY.
    when(resumes.findById(uploaded.getId())).thenReturn(Optional.of(uploaded));

    assertThrows(ResumeNotReadyException.class,
        () -> service().matchesForMe(uploaded.getId()));
  }

  @Test
  void matchesForMeWithoutResumeIdUsesLatestReadyResume() {
    authenticate();
    Job javaJob = jobWithSkills("Senior Software Engineer, Backend (Java)",
        List.of("Java", "Spring Boot"));
    Job androidJob = jobWithSkills("Senior Android Engineer",
        List.of("Kotlin", "Android"));
    Profile p = profile("Reston, VA", "Remote", 100000, 200000);
    // List is newest-first: the Android resume is the latest READY one.
    Resume latest = readyResume("Kotlin Android developer, Jetpack Compose.");
    Resume older = readyResume("Java Spring Boot developer.");
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(p));
    when(resumes.findByUserIdOrderByCreatedAtDesc(USER_ID))
        .thenReturn(List.of(latest, older));
    when(jobs.findAll()).thenReturn(List.of(javaJob, androidJob));

    List<MatchDto> matches = service().matchesForMe(null);

    assertEquals(androidJob.getId(), matches.get(0).job().id(),
        "no resumeId must still use the latest READY resume");
    assertEquals(100, matches.get(0).score());
  }

  @Test
  void matchForJobWithResumeIdUsesThatResume() {
    authenticate();
    Job javaJob = jobWithSkills("Senior Software Engineer, Backend (Java)",
        List.of("Java", "Spring Boot"));
    Job androidJob = jobWithSkills("Senior Android Engineer",
        List.of("Kotlin", "Android"));
    Profile p = profile("Reston, VA", "Remote", 100000, 200000);
    Resume androidResume = readyResume("Kotlin Android developer, Jetpack Compose.");
    when(jobs.findById(androidJob.getId())).thenReturn(Optional.of(androidJob));
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(p));
    when(resumes.findById(androidResume.getId())).thenReturn(Optional.of(androidResume));

    MatchDto match = service().matchForJob(androidJob.getId(), androidResume.getId());

    assertEquals(100, match.score());
    assertEquals("RESUME", match.source());
    // The Java job would score 50 with this resume; the chosen resume, not
    // the latest READY one, decides the score — prove it via the Android job.
    assertEquals(androidJob.getId(), match.job().id());
  }

  @Test
  void loadCandidateWithResumeIdUsesThatResumeText() {
    authenticate();
    Resume androidResume = readyResume("Kotlin Android developer, Jetpack Compose.");
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(resumes.findById(androidResume.getId())).thenReturn(Optional.of(androidResume));

    MatchingService.Candidate candidate =
        service().loadCandidate(USER_ID, androidResume.getId());

    assertEquals("RESUME", candidate.source());
    assertTrue(candidate.text().contains("kotlin"));
    assertTrue(candidate.text().contains("android"));
  }
}
