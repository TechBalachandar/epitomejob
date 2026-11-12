package com.epitomejob.identity.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.model.User;
import com.epitomejob.identity.service.AuthService;
import com.epitomejob.identity.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AuthService auth;

  @MockitoBean
  private JwtService jwt;

  private User user() {
    return new User("a@b.com", "hashed", "A B");
  }

  private AuthService.AuthResult tokens() {
    return new AuthService.AuthResult("access-token", "refresh-token", 900_000L, user());
  }

  @Test
  void registerReturns201WithTokens() throws Exception {
    when(auth.register(any(), any(), any())).thenReturn(user());
    when(auth.login(any(), any())).thenReturn(tokens());

    mockMvc.perform(post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"a@b.com","fullName":"A B","password":"Passw0rd!"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.email").value("a@b.com"));
  }

  @Test
  void registerDuplicateEmailReturns409() throws Exception {
    when(auth.register(any(), any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered"));

    mockMvc.perform(post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"a@b.com","fullName":"A B","password":"Passw0rd!"}"""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("409 CONFLICT"));
  }

  @Test
  void registerInvalidEmailReturns400() throws Exception {
    mockMvc.perform(post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"not-an-email","fullName":"A B","password":"Passw0rd!"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void loginReturns200WithTokens() throws Exception {
    when(auth.login(any(), any())).thenReturn(tokens());

    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"a@b.com","password":"Passw0rd!"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.expiresInMs").value(900_000));
  }

  @Test
  void loginBadCredentialsReturns401() throws Exception {
    when(auth.login(any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"a@b.com","password":"wrong"}"""))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid credentials"));
  }

  @Test
  void refreshReturns200WithTokens() throws Exception {
    when(auth.refresh(any())).thenReturn(tokens());

    mockMvc.perform(post("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken":"refresh-token"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"));
  }

  @Test
  void logoutReturns200() throws Exception {
    mockMvc.perform(post("/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken":"refresh-token"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("logged out"));
  }
}
