package com.epitomejob.common.security;

import com.epitomejob.identity.model.Role;
import com.epitomejob.identity.model.User;
import com.epitomejob.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

  @Mock
  private UserRepository users;

  @Test
  void loadsUserByEmailWithUuidPrincipalAndRole() {
    User user = new User("a@b.com", "hashed", "A B");
    user.setRole(Role.ADMIN);
    when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));

    UserDetails details = new AppUserDetailsService(users).loadUserByUsername("a@b.com");

    // Principal is the user id (JWT subject convention), not the email.
    assertEquals(user.getId().toString(), details.getUsername());
    assertEquals("hashed", details.getPassword());
    assertTrue(details.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
  }

  @Test
  void unknownEmailThrows() {
    when(users.findByEmail("ghost@b.com")).thenReturn(Optional.empty());
    assertThrows(UsernameNotFoundException.class,
        () -> new AppUserDetailsService(users).loadUserByUsername("ghost@b.com"));
  }
}
