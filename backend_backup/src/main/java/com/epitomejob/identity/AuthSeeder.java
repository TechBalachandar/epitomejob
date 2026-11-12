package com.epitomejob.identity;
import org.slf4j.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
/** DEV ONLY. Never enable in production. Change the password before any shared deployment. */
@Component @Profile("!prod")
public class AuthSeeder implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(AuthSeeder.class);
  private final UserRepository users;
  private final PasswordEncoder passwords;
  public AuthSeeder(UserRepository users, PasswordEncoder passwords) { this.users = users; this.passwords = passwords; }
  @Override public void run(String... args) {
    if (users.findByEmail("dev@example.com").isEmpty()) {
      users.save(new User("dev@example.com", passwords.encode("DevPass123!"), "Dev User"));
      log.warn("DEV SEED ACCOUNT created: dev@example.com / DevPass123! — dev profile only, change it before any shared deployment.");
    }
  }
}
