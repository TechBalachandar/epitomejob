package com.epitomejob.common.security

import com.epitomejob.identity.model.Role
import com.epitomejob.identity.model.User
import com.epitomejob.identity.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AppUserDetailsServiceTest {

  @Mock
  private lateinit var users: UserRepository

  @Test
  fun loadsUserByEmailWithUuidPrincipalAndRole() {
    val user = User("a@b.com", "hashed", "A B")
    user.role = Role.ADMIN
    Mockito.`when`(users.findByEmail("a@b.com")).thenReturn(Optional.of(user))

    val details: UserDetails = AppUserDetailsService(users).loadUserByUsername("a@b.com")

    // Principal is the user id (JWT subject convention), not the email.
    assertEquals(user.id.toString(), details.username)
    assertEquals("hashed", details.password)
    assertTrue(details.authorities.any { a -> a.authority == "ROLE_ADMIN" })
  }

  @Test
  fun unknownEmailThrows() {
    Mockito.`when`(users.findByEmail("ghost@b.com")).thenReturn(Optional.empty())
    assertThrows(UsernameNotFoundException::class.java) {
      AppUserDetailsService(users).loadUserByUsername("ghost@b.com")
    }
  }
}
