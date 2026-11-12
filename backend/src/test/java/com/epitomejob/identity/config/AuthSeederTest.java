package com.epitomejob.identity.config;

import com.epitomejob.identity.model.User;
import com.epitomejob.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSeederTest {

  @Mock
  private UserRepository users;

  @Mock
  private PasswordEncoder passwords;

  @Test
  void seedsDevAccountWhenMissing() {
    when(users.findByEmail(AuthSeeder.DEV_EMAIL)).thenReturn(Optional.empty());
    when(passwords.encode(AuthSeeder.DEV_PASSWORD)).thenReturn("hashed");

    new AuthSeeder(users, passwords).run();

    verify(users).save(any(User.class));
  }

  @Test
  void skipsSeedingWhenDevAccountExists() {
    when(users.findByEmail(AuthSeeder.DEV_EMAIL))
        .thenReturn(Optional.of(new User(AuthSeeder.DEV_EMAIL, "hashed", "Dev User")));

    new AuthSeeder(users, passwords).run();

    verify(users, never()).save(any());
  }
}
