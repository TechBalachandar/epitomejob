package com.epitomejob.identity.controller

import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.model.User
import com.epitomejob.identity.service.AuthService
import com.epitomejob.identity.service.JwtService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class)
class AuthControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var auth: AuthService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun user(): User {
    return User("a@b.com", "hashed", "A B")
  }

  private fun tokens(): AuthService.AuthResult {
    return AuthService.AuthResult("access-token", "refresh-token", 900_000L, user())
  }

  @Test
  fun registerReturns201WithTokens() {
    Mockito.`when`(auth.register(any(), any(), any())).thenReturn(user())
    Mockito.`when`(auth.login(any(), any())).thenReturn(tokens())

    mockMvc.perform(
      post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"a@b.com","fullName":"A B","password":"Passw0rd!"}""")
    )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.accessToken").value("access-token"))
      .andExpect(jsonPath("$.email").value("a@b.com"))
  }

  @Test
  fun registerDuplicateEmailReturns409() {
    Mockito.`when`(auth.register(any(), any(), any()))
      .thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "Email already registered"))

    mockMvc.perform(
      post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"a@b.com","fullName":"A B","password":"Passw0rd!"}""")
    )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("409 CONFLICT"))
  }

  @Test
  fun registerInvalidEmailReturns400() {
    mockMvc.perform(
      post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"not-an-email","fullName":"A B","password":"Passw0rd!"}""")
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
  }

  @Test
  fun loginReturns200WithTokens() {
    Mockito.`when`(auth.login(any(), any())).thenReturn(tokens())

    mockMvc.perform(
      post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"a@b.com","password":"Passw0rd!"}""")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").value("access-token"))
      .andExpect(jsonPath("$.expiresInMs").value(900_000))
  }

  @Test
  fun loginBadCredentialsReturns401() {
    Mockito.`when`(auth.login(any(), any()))
      .thenThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"))

    mockMvc.perform(
      post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"a@b.com","password":"wrong"}""")
    )
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.message").value("Invalid credentials"))
  }

  @Test
  fun refreshReturns200WithTokens() {
    Mockito.`when`(auth.refresh(any())).thenReturn(tokens())

    mockMvc.perform(
      post("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"refreshToken":"refresh-token"}""")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").value("access-token"))
  }

  @Test
  fun logoutReturns200() {
    mockMvc.perform(
      post("/auth/logout")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"refreshToken":"refresh-token"}""")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("logged out"))
  }
}
