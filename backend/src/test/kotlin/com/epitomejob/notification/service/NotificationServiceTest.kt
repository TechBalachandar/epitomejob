package com.epitomejob.notification.service

import com.epitomejob.notification.dto.NotificationPage
import com.epitomejob.notification.model.Notification
import com.epitomejob.notification.repository.NotificationRepository
import com.epitomejob.profile.model.Profile
import com.epitomejob.profile.repository.ProfileRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotificationServiceTest {

  companion object {
    private val USER_ID = UUID.randomUUID()
  }

  @Mock
  private lateinit var notifications: NotificationRepository

  @Mock
  private lateinit var profiles: ProfileRepository

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, listOf()))
  }

  private fun service(): NotificationService {
    return NotificationService(notifications, profiles)
  }

  private fun notification(type: String, title: String): Notification {
    return Notification(USER_ID, type, title, "body", "/tracker")
  }

  /** No profile at all → no derived warning. */
  private fun noProfile() {
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty())
  }

  /** Profile whose H-1B clock started `daysAgo` days before today (UTC). */
  private fun profileWithClockStartedDaysAgo(daysAgo: Long) {
    val profile = Profile(USER_ID)
    profile.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo))
    Mockito.`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(profile))
  }

  private fun storedPage(): List<Notification> {
    val unread = notification("TRACKER_CHANGED", "Application updated")
    val read = notification("APPROVAL_PROPOSED", "Approval proposed: Apply for a job")
    read.isRead = true
    return listOf(unread, read)
  }

  @Test
  fun createPersistsWithOwner() {
    authenticate()
    Mockito.`when`(notifications.save(any())).thenAnswer { inv -> inv.getArgument<Notification>(0) }

    val created = service().create(USER_ID, "APPROVAL_PROPOSED",
      "Approval proposed: Apply for a job",
      "Review it in your approvals inbox to approve or reject it.", "/approvals")

    assertEquals(USER_ID, created.userId)
    assertEquals("APPROVAL_PROPOSED", created.type)
    assertFalse(created.isRead)
    assertTrue(created.createdAt != null)
  }

  @Test
  fun listOrderingIsUnreadFirstThenNewest() {
    authenticate()
    noProfile()
    val stored = storedPage()
    Mockito.`when`(notifications.findByUserId(any(), any()))
      .thenReturn(PageImpl(stored, PageRequest.of(0, 20), 2))

    val page: NotificationPage = service().listMine(0, 20)

    val pageable = ArgumentCaptor.forClass(Pageable::class.java)
    verify(notifications).findByUserId(any(), pageable.capture())
    val sort: Sort = pageable.value.sort
    assertEquals(Sort.Direction.ASC, sort.getOrderFor("isRead")?.direction)
    assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt")?.direction)
    assertEquals(2, page.content.size)
    assertEquals("Application updated", page.content[0].title)
    assertFalse(page.content[0].derived)
    assertEquals(2, page.totalElements)
    assertEquals(1, page.totalPages)
  }

  @Test
  fun listClampsSizeTo1And50() {
    authenticate()
    noProfile()
    Mockito.`when`(notifications.findByUserId(any(), any()))
      .thenReturn(PageImpl<Notification>(listOf(), PageRequest.of(0, 20), 0))

    service().listMine(0, 200)
    val pageable = ArgumentCaptor.forClass(Pageable::class.java)
    verify(notifications).findByUserId(any(), pageable.capture())
    assertEquals(50, pageable.value.pageSize)

    service().listMine(0, 0)
    verify(notifications, Mockito.times(2))
      .findByUserId(any(), pageable.capture())
    assertEquals(1, pageable.value.pageSize)
  }

  @Test
  fun derivedWarningBand14PrependedFirst() {
    authenticate()
    profileWithClockStartedDaysAgo(46) // daysLeft = 14
    val stored = storedPage()
    Mockito.`when`(notifications.findByUserId(any(), any()))
      .thenReturn(PageImpl(stored, PageRequest.of(0, 20), 2))

    val page = service().listMine(0, 20)

    assertEquals(3, page.content.size)
    val warning = page.content[0]
    assertEquals("clock-14", warning.id)
    assertEquals("CLOCK_WARNING", warning.type)
    assertEquals("H-1B clock: 14 days left", warning.title)
    assertEquals("Your 60-day grace period ends on "
      + LocalDate.now(ZoneOffset.UTC).plusDays(14) + ".", warning.body)
    assertEquals("/profile", warning.link)
    assertFalse(warning.isRead)
    assertTrue(warning.derived)
    // Metadata counts STORED rows only.
    assertEquals(2, page.totalElements)
    assertEquals(1, page.totalPages)
  }

  @Test
  fun derivedWarningBand7AndOneDay() {
    authenticate()
    val seven = Profile(USER_ID)
    seven.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(53)) // daysLeft = 7
    val one = Profile(USER_ID)
    one.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(59)) // daysLeft = 1
    // Sequential stubbing: re-stubbing the same matcher would override the first.
    Mockito.`when`(profiles.findByUserId(USER_ID))
      .thenReturn(Optional.of(seven), Optional.of(one))
    Mockito.`when`(notifications.findByUserId(any(), any()))
      .thenReturn(PageImpl<Notification>(listOf(), PageRequest.of(0, 20), 0))

    var warning = service().listMine(0, 20).content[0]
    assertEquals("clock-7", warning.id)
    assertEquals("H-1B clock: 7 days left", warning.title)

    warning = service().listMine(0, 20).content[0]
    assertEquals("clock-7", warning.id)
    assertEquals("H-1B clock: 1 day left", warning.title)
  }

  @Test
  fun derivedWarningBand0IsLastDay() {
    authenticate()
    profileWithClockStartedDaysAgo(60) // daysLeft = 0
    Mockito.`when`(notifications.findByUserId(any(), any()))
      .thenReturn(PageImpl<Notification>(listOf(), PageRequest.of(0, 20), 0))

    val warning = service().listMine(0, 20).content[0]
    assertEquals("clock-0", warning.id)
    assertEquals("H-1B clock: last day", warning.title)
    assertEquals("Your 60-day grace period ends today ("
      + LocalDate.now(ZoneOffset.UTC) + ").", warning.body)
  }

  @Test
  fun derivedWarningExpired() {
    authenticate()
    profileWithClockStartedDaysAgo(61) // daysLeft = -1
    Mockito.`when`(notifications.findByUserId(any(), any()))
      .thenReturn(PageImpl<Notification>(listOf(), PageRequest.of(0, 20), 0))

    val warning = service().listMine(0, 20).content[0]
    assertEquals("clock-expired", warning.id)
    assertEquals("H-1B grace period ended", warning.title)
    assertEquals("Your 60-day grace period ended on "
      + LocalDate.now(ZoneOffset.UTC).minusDays(1) + ".", warning.body)
  }

  @Test
  fun derivedWarningAbsentWithoutClockDateAndBeyond14Days() {
    authenticate()
    val noDate = Profile(USER_ID)
    val farOut = Profile(USER_ID)
    farOut.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(30)) // daysLeft = 30
    // Sequential stubbing: re-stubbing the same matcher would override the first.
    Mockito.`when`(profiles.findByUserId(USER_ID))
      .thenReturn(Optional.of(noDate), Optional.of(farOut))
    Mockito.`when`(notifications.findByUserId(any(), any()))
      .thenReturn(PageImpl<Notification>(listOf(), PageRequest.of(0, 20), 0))

    assertTrue(service().listMine(0, 20).content.isEmpty())
    assertTrue(service().listMine(0, 20).content.isEmpty())
  }

  @Test
  fun derivedWarningNotPrependedOnLaterPages() {
    authenticate()
    // Active clock (14 days left) — would warn on page 0, but the service
    // never loads the profile for later pages, so this stub is lenient.
    val profile = Profile(USER_ID)
    profile.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(46))
    lenient().`when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(profile))
    Mockito.`when`(notifications.findByUserId(any(), any()))
      .thenReturn(PageImpl<Notification>(listOf(), PageRequest.of(1, 20), 0))

    val page = service().listMine(1, 20)
    assertTrue(page.content.isEmpty(), "derived warning appears on the first page only")
  }

  @Test
  fun unreadCountIncludesWarning() {
    authenticate()
    Mockito.`when`(notifications.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(2L)
    val warning = Profile(USER_ID)
    warning.setH1bClockStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(46)) // daysLeft = 14
    // Sequential stubbing: re-stubbing the same matcher would override the first.
    Mockito.`when`(profiles.findByUserId(USER_ID))
      .thenReturn(Optional.empty(), Optional.of(warning))

    assertEquals(2L, service().unreadCount())
    assertEquals(3L, service().unreadCount())
  }

  @Test
  fun markReadFlipsStoredRow() {
    authenticate()
    val stored = notification("TRACKER_CHANGED", "Application updated")
    Mockito.`when`(notifications.findById(stored.id)).thenReturn(Optional.of(stored))
    Mockito.`when`(notifications.save(any())).thenAnswer { inv -> inv.getArgument<Notification>(0) }

    val dto = service().markRead(stored.id)

    assertTrue(dto.isRead)
    assertEquals(stored.id.toString(), dto.id)
    assertFalse(dto.derived)
  }

  @Test
  fun markReadAnotherUsersIdIs404() {
    authenticate()
    val other = Notification(UUID.randomUUID(),
      "TRACKER_CHANGED", "Application updated", "body", "/tracker")
    Mockito.`when`(notifications.findById(other.id)).thenReturn(Optional.of(other))

    val ex = assertThrows(ResponseStatusException::class.java) { service().markRead(other.id) }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun markReadMissingIdIs404() {
    authenticate()
    val missing = UUID.randomUUID()
    Mockito.`when`(notifications.findById(missing)).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) { service().markRead(missing) }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun markAllReadCountsAndMarksOnlyStoredRows() {
    authenticate()
    val first = notification("TRACKER_CHANGED", "Application updated")
    val second = notification("APPROVAL_PROPOSED", "Approval proposed: Apply for a job")
    Mockito.`when`(notifications.findByUserIdAndIsReadFalse(USER_ID))
      .thenReturn(listOf(first, second))

    assertEquals(2, service().markAllRead())

    assertTrue(first.isRead)
    assertTrue(second.isRead)
    verify(notifications).saveAll(listOf(first, second))
  }
}
