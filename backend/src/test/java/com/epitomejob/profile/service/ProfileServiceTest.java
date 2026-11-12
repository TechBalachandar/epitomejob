package com.epitomejob.profile.service;

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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        "Java Developer", null, "Reston, VA", "REMOTE", null, null));

    assertEquals("Java Developer", response.headline());
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
        null, null, "Reston, VA", null, null, null));

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
  }
}
