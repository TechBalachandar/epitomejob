package com.epitomejob.identity.config

import com.epitomejob.identity.model.User
import com.epitomejob.identity.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * DEV ONLY. Never enable in production. Change the password before any
 * shared deployment. Disabled entirely on the `prod` profile.
 */
@Component
@Profile("!prod")
class AuthSeeder(
  private val users: UserRepository,
  private val passwords: PasswordEncoder
) : CommandLineRunner {

  companion object {
    const val DEV_EMAIL = "dev@example.com"
    const val DEV_PASSWORD = "DevPass123!"
    const val DEV_FULL_NAME = "Dev User"
  }

  private val log = LoggerFactory.getLogger(AuthSeeder::class.java)

  override fun run(vararg args: String) {
    if (users.findByEmail(DEV_EMAIL).isEmpty) {
      users.save(User(DEV_EMAIL, passwords.encode(DEV_PASSWORD), DEV_FULL_NAME))
      log.warn(
        "DEV SEED ACCOUNT created: {} / {} — dev profile only, change it before any shared deployment.",
        DEV_EMAIL, DEV_PASSWORD
      )
    }
  }
}
