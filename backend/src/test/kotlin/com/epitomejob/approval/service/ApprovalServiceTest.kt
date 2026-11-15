package com.epitomejob.approval.service

import com.epitomejob.approval.model.Approval
import com.epitomejob.approval.repository.ApprovalRepository
import com.epitomejob.notification.service.NotificationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ApprovalServiceTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private val APPROVAL_ID: UUID = UUID.randomUUID()
  }

  @Mock
  private lateinit var approvals: ApprovalRepository

  @Mock
  private lateinit var notifications: NotificationService

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticate() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, emptyList())
  }

  private fun service(): ApprovalService {
    return ApprovalService(approvals, notifications)
  }

  private fun pendingApproval(): Approval {
    return Approval(
      USER_ID, "CREATE_APPLICATION", "{\"jobId\":\"job-1\"}",
      "token-hash", Instant.now().plusSeconds(3600L)
    )
  }

  @Test
  fun proposeCreatesPendingApprovalExpiringIn24Hours() {
    authenticate()
    Mockito.`when`(approvals.save(any())).thenAnswer { inv -> inv.getArgument<Approval>(0) }

    val created = service().propose("CREATE_APPLICATION", "{\"jobId\":\"job-1\"}")

    assertEquals("PENDING", created.status)
    assertEquals("CREATE_APPLICATION", created.action)
    assertTrue(
      created.expiresAt.isAfter(Instant.now().plusSeconds(23 * 3600L)),
      "approval must expire ~24h after proposal"
    )
    assertTrue(created.expiresAt.isBefore(Instant.now().plusSeconds(25 * 3600L)))
  }

  @Test
  fun proposeCreatesApprovalProposedNotification() {
    authenticate()
    Mockito.`when`(approvals.save(any())).thenAnswer { inv -> inv.getArgument<Approval>(0) }

    service().propose("APPLY_JOB", "{\"jobId\":\"job-1\"}")

    verify(notifications).create(
      eq(USER_ID), eq("APPROVAL_PROPOSED"),
      eq("Approval proposed: Apply for a job"),
      eq("Review it in your approvals inbox to approve or reject it."),
      eq("/approvals")
    )
  }

  @Test
  fun proposeUnknownActionUsesActionItselfAsLabel() {
    authenticate()
    Mockito.`when`(approvals.save(any())).thenAnswer { inv -> inv.getArgument<Approval>(0) }

    service().propose("SOMETHING_ELSE", "{}")

    verify(notifications).create(
      eq(USER_ID), eq("APPROVAL_PROPOSED"),
      eq("Approval proposed: SOMETHING_ELSE"), anyString(), anyString()
    )
  }

  @Test
  fun approveSetsApprovedAndDecidedAt() {
    authenticate()
    val approval = pendingApproval()
    Mockito.`when`(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(approval))
    Mockito.`when`(approvals.save(any())).thenAnswer { inv -> inv.getArgument<Approval>(0) }

    val decided = service().decide(APPROVAL_ID, true)

    assertEquals("APPROVED", decided.status)
    assertNotNull(decided.decidedAt)
  }

  @Test
  fun rejectSetsRejected() {
    authenticate()
    val approval = pendingApproval()
    Mockito.`when`(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(approval))
    Mockito.`when`(approvals.save(any())).thenAnswer { inv -> inv.getArgument<Approval>(0) }

    assertEquals("REJECTED", service().decide(APPROVAL_ID, false).status)
  }

  @Test
  fun approveTwiceExecutesOnce() {
    authenticate()
    val approval = pendingApproval()
    Mockito.`when`(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(approval))
    Mockito.`when`(approvals.save(any())).thenAnswer { inv -> inv.getArgument<Approval>(0) }

    service().decide(APPROVAL_ID, true)
    val second = service().decide(APPROVAL_ID, true)

    assertEquals("APPROVED", second.status)
    verify(approvals, times(1)).save(any())
  }

  @Test
  fun decidingAnotherUsersApprovalIsNotFound() {
    authenticate()
    val other = Approval(
      UUID.randomUUID(), "CREATE_APPLICATION", "{}",
      "token-hash", Instant.now().plusSeconds(3600L)
    )
    Mockito.`when`(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(other))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().decide(APPROVAL_ID, true)
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun decidingMissingApprovalIsNotFound() {
    authenticate()
    Mockito.`when`(approvals.findById(APPROVAL_ID)).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().decide(APPROVAL_ID, true)
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun decidingExpiredApprovalIsGone() {
    authenticate()
    val expired = Approval(
      USER_ID, "CREATE_APPLICATION", "{}",
      "token-hash", Instant.now().minusSeconds(60L)
    )
    Mockito.`when`(approvals.findById(APPROVAL_ID)).thenReturn(Optional.of(expired))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().decide(APPROVAL_ID, true)
    }
    assertEquals(HttpStatus.GONE, ex.statusCode)
  }

  @Test
  fun pendingMineReturnsOnlyPending() {
    authenticate()
    val approval = pendingApproval()
    Mockito.`when`(approvals.findByUserIdAndStatusOrderByExpiresAtAsc(USER_ID, "PENDING"))
      .thenReturn(listOf(approval))

    assertEquals(listOf(approval), service().pendingMine())
    val status = ArgumentCaptor.forClass(String::class.java)
    verify(approvals).findByUserIdAndStatusOrderByExpiresAtAsc(any(), status.capture())
    assertEquals("PENDING", status.value)
  }
}
