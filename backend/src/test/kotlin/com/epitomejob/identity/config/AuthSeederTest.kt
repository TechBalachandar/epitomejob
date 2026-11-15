package com.epitomejob.identity.config

import com.epitomejob.identity.model.User
import com.epitomejob.identity.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AuthSeederTest {

  @Mock
  private lateinit var users: UserRepository

  @Mock
  private lateinit var passwords: PasswordEncoder

  @Test
  fun seedsDevAccountWhenMissing() {
    Mockito.`when`(users.findByEmail(AuthSeeder.DEV_EMAIL)).thenReturn(Optional.empty())
    Mockito.`when`(passwords.encode(AuthSeeder.DEV_PASSWORD)).thenReturn("hashed")

    AuthSeeder(users, passwords).run()

    verify(users).save(any(User::class.java))
  }

  @Test
  fun skipsSeedingWhenDevAccountExists() {
    Mockito.`when`(users.findByEmail(AuthSeeder.DEV_EMAIL))
      .thenReturn(Optional.of(User(AuthSeeder.DEV_EMAIL, "hashed", "Dev User")))

    AuthSeeder(users, passwords).run()

    verify(users, never()).save(any())
  }
}
