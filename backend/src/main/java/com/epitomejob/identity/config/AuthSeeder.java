package com.epitomejob.identity.config;

import com.epitomejob.identity.model.User;
import com.epitomejob.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DEV ONLY. Never enable in production. Change the password before any
 * shared deployment. Disabled entirely on the {@code prod} profile.
 */
@Component
@Profile("!prod")
public class AuthSeeder implements CommandLineRunner {

  public static final String DEV_EMAIL = "dev@example.com";
  public static final String DEV_PASSWORD = "DevPass123!";
  public static final String DEV_FULL_NAME = "Dev User";

  private static final Logger log = LoggerFactory.getLogger(AuthSeeder.class);

  private final UserRepository users;
  private final PasswordEncoder passwords;

  public AuthSeeder(UserRepository users, PasswordEncoder passwords) {
    this.users = users;
    this.passwords = passwords;
  }

  @Override
  public void run(String... args) {
    if (users.findByEmail(DEV_EMAIL).isEmpty()) {
      users.save(new User(DEV_EMAIL, passwords.encode(DEV_PASSWORD), DEV_FULL_NAME));
      log.warn("DEV SEED ACCOUNT created: {} / {} — dev profile only, change it before any shared deployment.",
          DEV_EMAIL, DEV_PASSWORD);
    }
  }
}
