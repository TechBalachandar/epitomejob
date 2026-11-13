package com.epitomejob.notification.service;

import com.epitomejob.notification.dto.NotificationPage;
import com.epitomejob.notification.model.Notification;
import com.epitomejob.notification.repository.NotificationRepository;
import com.epitomejob.profile.model.Profile;
import com.epitomejob.profile.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock
  private NotificationRepository notifications;
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

  private NotificationService service() {
    return new NotificationService(notifications, profiles);
  }

  private Notification notification(String type, String title) {
    return new Notification(USER_ID, type, title, "body", "/tracker");
  }

  /** No profile at all → no derived warning. */
  private void noProfile() {
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());
  }

  /** Profile whose H-1B clock started {@code daysAgo} days before today (UTC). */
  private void profileWithClockStartedDaysAgo(long daysAgo) {
    Profile profile = new Profile(USER_ID);
    profile.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo));
    when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
  }

  private List<Notification> storedPage() {
    Notification unread = notification("TRACKER_CHANGED", "Application updated");
    Notification read = notification("APPROVAL_PROPOSED", "Approval proposed: Apply for a job");
    read.setRead(true);
    return List.of(unread, read);
  }

  @Test
  void createPersistsWithOwner() {
    authenticate();
    when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Notification created = service().create(USER_ID, "APPROVAL_PROPOSED",
        "Approval proposed: Apply for a job",
        "Review it in your approvals inbox to approve or reject it.", "/approvals");

    assertEquals(USER_ID, created.getUserId());
    assertEquals("APPROVAL_PROPOSED", created.getType());
    assertFalse(created.isRead());
    assertTrue(created.getCreatedAt() != null);
  }

  @Test
  void listOrderingIsUnreadFirstThenNewest() {
    authenticate();
    noProfile();
    List<Notification> stored = storedPage();
    when(notifications.findByUserId(any(), any()))
        .thenReturn(new PageImpl<>(stored, PageRequest.of(0, 20), 2));

    NotificationPage page = service().listMine(0, 20);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(notifications).findByUserId(any(), pageable.capture());
    Sort sort = pageable.getValue().getSort();
    assertEquals(Sort.Direction.ASC, sort.getOrderFor("isRead").getDirection());
    assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
    assertEquals(2, page.content().size());
    assertEquals("Application updated", page.content().get(0).title());
    assertFalse(page.content().get(0).derived());
    assertEquals(2, page.totalElements());
    assertEquals(1, page.totalPages());
  }

  @Test
  void listClampsSizeTo1And50() {
    authenticate();
    noProfile();
    when(notifications.findByUserId(any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    service().listMine(0, 200);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(notifications).findByUserId(any(), pageable.capture());
    assertEquals(50, pageable.getValue().getPageSize());

    service().listMine(0, 0);
    verify(notifications, org.mockito.Mockito.times(2))
        .findByUserId(any(), pageable.capture());
    assertEquals(1, pageable.getValue().getPageSize());
  }

  @Test
  void derivedWarningBand14PrependedFirst() {
    authenticate();
    profileWithClockStartedDaysAgo(46); // daysLeft = 14
    List<Notification> stored = storedPage();
    when(notifications.findByUserId(any(), any()))
        .thenReturn(new PageImpl<>(stored, PageRequest.of(0, 20), 2));

    NotificationPage page = service().listMine(0, 20);

    assertEquals(3, page.content().size());
    var warning = page.content().get(0);
    assertEquals("clock-14", warning.id());
    assertEquals("CLOCK_WARNING", warning.type());
    assertEquals("H-1B clock: 14 days left", warning.title());
    assertEquals("Your 60-day grace period ends on "
        + LocalDate.now(ZoneOffset.UTC).plusDays(14) + ".", warning.body());
    assertEquals("/profile", warning.link());
    assertFalse(warning.isRead());
    assertTrue(warning.derived());
    // Metadata counts STORED rows only.
    assertEquals(2, page.totalElements());
    assertEquals(1, page.totalPages());
  }

  @Test
  void derivedWarningBand7AndOneDay() {
    authenticate();
    Profile seven = new Profile(USER_ID);
    seven.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(53)); // daysLeft = 7
    Profile one = new Profile(USER_ID);
    one.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(59)); // daysLeft = 1
    // Sequential stubbing: re-stubbing the same matcher would override the first.
    when(profiles.findByUserId(USER_ID))
        .thenReturn(Optional.of(seven), Optional.of(one));
    when(notifications.findByUserId(any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    var warning = service().listMine(0, 20).content().get(0);
    assertEquals("clock-7", warning.id());
    assertEquals("H-1B clock: 7 days left", warning.title());

    warning = service().listMine(0, 20).content().get(0);
    assertEquals("clock-7", warning.id());
    assertEquals("H-1B clock: 1 day left", warning.title());
  }

  @Test
  void derivedWarningBand0IsLastDay() {
    authenticate();
    profileWithClockStartedDaysAgo(60); // daysLeft = 0
    when(notifications.findByUserId(any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    var warning = service().listMine(0, 20).content().get(0);
    assertEquals("clock-0", warning.id());
    assertEquals("H-1B clock: last day", warning.title());
    assertEquals("Your 60-day grace period ends today ("
        + LocalDate.now(ZoneOffset.UTC) + ").", warning.body());
  }

  @Test
  void derivedWarningExpired() {
    authenticate();
    profileWithClockStartedDaysAgo(61); // daysLeft = -1
    when(notifications.findByUserId(any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    var warning = service().listMine(0, 20).content().get(0);
    assertEquals("clock-expired", warning.id());
    assertEquals("H-1B grace period ended", warning.title());
    assertEquals("Your 60-day grace period ended on "
        + LocalDate.now(ZoneOffset.UTC).minusDays(1) + ".", warning.body());
  }

  @Test
  void derivedWarningAbsentWithoutClockDateAndBeyond14Days() {
    authenticate();
    Profile noDate = new Profile(USER_ID);
    Profile farOut = new Profile(USER_ID);
    farOut.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(30)); // daysLeft = 30
    // Sequential stubbing: re-stubbing the same matcher would override the first.
    when(profiles.findByUserId(USER_ID))
        .thenReturn(Optional.of(noDate), Optional.of(farOut));
    when(notifications.findByUserId(any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    assertTrue(service().listMine(0, 20).content().isEmpty());
    assertTrue(service().listMine(0, 20).content().isEmpty());
  }

  @Test
  void derivedWarningNotPrependedOnLaterPages() {
    authenticate();
    // Active clock (14 days left) — would warn on page 0, but the service
    // never loads the profile for later pages, so this stub is lenient.
    Profile profile = new Profile(USER_ID);
    profile.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(46));
    lenient().when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
    when(notifications.findByUserId(any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 0));

    NotificationPage page = service().listMine(1, 20);
    assertTrue(page.content().isEmpty(), "derived warning appears on the first page only");
  }

  @Test
  void unreadCountIncludesWarning() {
    authenticate();
    when(notifications.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(2L);
    Profile warning = new Profile(USER_ID);
    warning.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(46)); // daysLeft = 14
    // Sequential stubbing: re-stubbing the same matcher would override the first.
    when(profiles.findByUserId(USER_ID))
        .thenReturn(Optional.empty(), Optional.of(warning));

    assertEquals(2L, service().unreadCount());
    assertEquals(3L, service().unreadCount());
  }

  @Test
  void markReadFlipsStoredRow() {
    authenticate();
    Notification stored = notification("TRACKER_CHANGED", "Application updated");
    when(notifications.findById(stored.getId())).thenReturn(Optional.of(stored));
    when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var dto = service().markRead(stored.getId());

    assertTrue(dto.isRead());
    assertEquals(stored.getId().toString(), dto.id());
    assertFalse(dto.derived());
  }

  @Test
  void markReadAnotherUsersIdIs404() {
    authenticate();
    Notification other = new Notification(UUID.randomUUID(),
        "TRACKER_CHANGED", "Application updated", "body", "/tracker");
    when(notifications.findById(other.getId())).thenReturn(Optional.of(other));

    var ex = assertThrows(ResponseStatusException.class, () -> service().markRead(other.getId()));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void markReadMissingIdIs404() {
    authenticate();
    UUID missing = UUID.randomUUID();
    when(notifications.findById(missing)).thenReturn(Optional.empty());

    var ex = assertThrows(ResponseStatusException.class, () -> service().markRead(missing));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void markAllReadCountsAndMarksOnlyStoredRows() {
    authenticate();
    Notification first = notification("TRACKER_CHANGED", "Application updated");
    Notification second = notification("APPROVAL_PROPOSED", "Approval proposed: Apply for a job");
    when(notifications.findByUserIdAndIsReadFalse(USER_ID)).thenReturn(List.of(first, second));

    assertEquals(2, service().markAllRead());

    assertTrue(first.isRead());
    assertTrue(second.isRead());
    verify(notifications).saveAll(List.of(first, second));
  }
}
