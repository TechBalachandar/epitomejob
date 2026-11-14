package com.epitomejob.profile.service;

import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.profile.dto.ProfileUpdateRequest;
import com.epitomejob.profile.model.Profile;
import com.epitomejob.profile.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock
  private ProfileRepository profiles;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private ProfileService service() {
    return new ProfileService(profiles);
  }

  @Test
  void upsertCreatesProfileAndScoresCompleteness() {
    authenticate();
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        "Java Developer", null, "Reston, VA", "REMOTE", null, null, null, null, null, null, null, null));

    assertEquals("Java Developer", response.headline());
    assertEquals(List.of(), response.skills(), "no skills set yet");
    assertEquals(null, response.h1bClockStartDate());
    assertEquals(60, response.completeness(), "headline + location + work type = 60");
    ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
    verify(profiles).save(saved.capture());
    assertEquals(60, saved.getValue().getCompleteness());
  }

  @Test
  void upsertKeepsUnchangedFields() {
    authenticate();
    Profile existing = new Profile(USER_ID);
    existing.setHeadline("Old headline");
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, "Reston, VA", null, null, null, null, null, null, null, null, null));

    assertEquals("Old headline", response.headline(), "null fields must not overwrite");
    assertEquals(40, response.completeness(), "headline + location = 40");
  }

  @Test
  void getMeReturnsExistingProfile() {
    authenticate();
    Profile existing = new Profile(USER_ID);
    existing.setHeadline("Java Developer");
    existing.setCompleteness(20);
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

    var response = service().getMe();

    assertEquals(USER_ID, response.userId());
    assertEquals(20, response.completeness());
  }

  @Test
  void getMeReturnsEmptyProfileWhenNoneExists() {
    authenticate();
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());

    var response = service().getMe();

    assertEquals(USER_ID, response.userId());
    assertEquals(0, response.completeness());
    assertEquals(List.of(), response.skills(), "skills are never null");
    assertEquals(null, response.h1bClockStartDate());
  }

  @Test
  void upsertPersistsSkillsTrimmedDedupedAndSortedInResponse() {
    authenticate();
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null,
        List.of("  Spring Boot ", "Java", "Java", "", "   ", "LangGraph"),
        null, null, null, null, null));

    assertEquals(List.of("Java", "LangGraph", "Spring Boot"),
        response.skills(), "trimmed, deduped, sorted alphabetically");
    ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
    verify(profiles).save(saved.capture());
    assertEquals(List.of("Spring Boot", "Java", "LangGraph"),
        List.copyOf(saved.getValue().getSkills()),
        "entity keeps request order after trim/dedupe");
  }

  @Test
  void upsertPersistsSkillsAtTheCap() {
    authenticate();
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));
    List<String> thirty = IntStream.rangeClosed(1, 30).mapToObj(i -> "Skill" + i).toList();

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null, thirty, null, null, null, null, null));

    assertEquals(30, response.skills().size());
  }

  @Test
  void upsertEmptySkillsListClears() {
    authenticate();
    Profile existing = new Profile(USER_ID);
    existing.setSkills(List.of("Java", "Spring Boot"));
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null, List.of(), null, null, null, null, null));

    assertEquals(List.of(), response.skills());
    ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
    verify(profiles).save(saved.capture());
    assertTrue(saved.getValue().getSkills().isEmpty());
  }

  @Test
  void upsertNullSkillsLeaveUnchanged() {
    authenticate();
    Profile existing = new Profile(USER_ID);
    existing.setSkills(List.of("Java"));
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null, null, null, null, null, null, null));

    assertEquals(List.of("Java"), response.skills(), "null skills must not overwrite");
  }

  @Test
  void upsertPersistsClockDateCurrencyAndVisibility() {
    authenticate();
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null, null,
        LocalDate.of(2026, 10, 15), "EUR", "PUBLIC", null, null));

    assertEquals(LocalDate.of(2026, 10, 15), response.h1bClockStartDate());
    assertEquals("EUR", response.currency());
    assertEquals("PUBLIC", response.visibility());
  }

  @Test
  void upsertNullClockDateLeavesUnchanged() {
    authenticate();
    Profile existing = new Profile(USER_ID);
    existing.setH1bClockStartDate(LocalDate.of(2026, 10, 15));
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null, null, null, null, null, null, null));

    assertEquals(LocalDate.of(2026, 10, 15), response.h1bClockStartDate(),
        "null clock date must not overwrite");
  }

  @Test
  void upsertBadVisibilityIs400() {
    authenticate();

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().upsertMe(new ProfileUpdateRequest(
            null, null, null, null, null, null, null, null, null, "SECRET", null, null)));

    assertEquals(400, ex.getStatusCode().value());
  }

  @Test
  void completenessUnchangedBySkillsAndClockDate() {
    authenticate();
    Profile existing = new Profile(USER_ID);
    existing.setHeadline("Java Developer");
    existing.setSummary("17 years of experience");
    existing.setLocation("Reston, VA");
    existing.setWorkType("REMOTE");
    existing.setSalaryMin(120_000);
    existing.setSalaryMax(160_000);
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null,
        List.of("Java", "LangGraph"), LocalDate.of(2026, 10, 15), null, null, null, null));

    assertEquals(100, response.completeness(),
        "skills + clock date add no completeness points");
    assertEquals(List.of("Java", "LangGraph"), response.skills());

    // Same profile without skills/clock scores the same 100.
    Profile plain = new Profile(USER_ID);
    plain.setHeadline("Java Developer");
    plain.setSummary("17 years of experience");
    plain.setLocation("Reston, VA");
    plain.setWorkType("REMOTE");
    plain.setSalaryMin(120_000);
    plain.setSalaryMax(160_000);
    assertEquals(100, plain.computeCompleteness());
  }

  @Test
  void upsertPersistsLinkedinUrlAndWorkAuthorization() {
    authenticate();
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null, null, null, null, null,
        "https://www.linkedin.com/in/balachandar-jeldi", "H1B"));

    assertEquals("https://www.linkedin.com/in/balachandar-jeldi", response.linkedinUrl());
    assertEquals("H1B", response.workAuthorization());
    assertEquals(0, response.completeness(),
        "LinkedIn URL + work authorisation add no completeness points");
    ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
    verify(profiles).save(saved.capture());
    assertEquals("https://www.linkedin.com/in/balachandar-jeldi",
        saved.getValue().getLinkedinUrl());
    assertEquals("H1B", saved.getValue().getWorkAuthorization());
  }

  @Test
  void upsertBadLinkedinUrlIs400() {
    authenticate();
    // Validation runs before the profile is loaded: no repository stub here —
    // a stubbed findByUserId would be unused and Mockito strict stubs fail.
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().upsertMe(new ProfileUpdateRequest(
            null, null, null, null, null, null, null, null, null, null,
            "www.linkedin.com/in/balachandar-jeldi", null)));

    assertEquals(400, ex.getStatusCode().value());
    assertEquals(AppConstants.Errors.INVALID_LINKEDIN_URL, ex.getReason());
  }

  @Test
  void upsertBadWorkAuthorizationIs400() {
    authenticate();
    // Validation runs before the profile is loaded: no repository stub.
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().upsertMe(new ProfileUpdateRequest(
            null, null, null, null, null, null, null, null, null, null,
            null, "CITIZEN")));

    assertEquals(400, ex.getStatusCode().value());
    assertEquals(AppConstants.Errors.INVALID_WORK_AUTHORIZATION, ex.getReason());
  }

  @Test
  void upsertNullLinkedinUrlAndWorkAuthorizationLeaveUnchanged() {
    authenticate();
    Profile existing = new Profile(USER_ID);
    existing.setLinkedinUrl("https://www.linkedin.com/in/balachandar-jeldi");
    existing.setWorkAuthorization("EAD");
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, "Reston, VA", null, null, null, null, null, null, null, null, null));

    assertEquals("https://www.linkedin.com/in/balachandar-jeldi", response.linkedinUrl(),
        "null LinkedIn URL must not overwrite");
    assertEquals("EAD", response.workAuthorization(),
        "null work authorisation must not overwrite");
  }

  @Test
  void linkedinUrlAndWorkAuthorizationAddNoCompletenessPoints() {
    authenticate();
    Profile existing = new Profile(USER_ID);
    existing.setHeadline("Java Developer");
    existing.setSummary("17 years of experience");
    existing.setLocation("Reston, VA");
    existing.setWorkType("REMOTE");
    existing.setSalaryMin(120_000);
    existing.setSalaryMax(160_000);
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service().upsertMe(new ProfileUpdateRequest(
        null, null, null, null, null, null, null, null, null, null,
        "https://www.linkedin.com/in/balachandar-jeldi", "US_CITIZEN"));

    assertEquals(100, response.completeness(),
        "LinkedIn URL + work authorisation add no completeness points");
  }
}
