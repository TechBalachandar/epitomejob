package com.epitomejob.identity.controller

import com.epitomejob.identity.dto.AuthResponse
import com.epitomejob.identity.dto.LoginRequest
import com.epitomejob.identity.dto.RegisterRequest
import com.epitomejob.identity.dto.TokenRequest
import com.epitomejob.identity.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val auth: AuthService) {

  @PostMapping("/register")
  fun register(@Valid @RequestBody req: RegisterRequest): ResponseEntity<AuthResponse> {
    auth.register(req.email, req.fullName, req.password)
    val result = auth.login(req.email, req.password)
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result))
  }

  @PostMapping("/login")
  fun login(@Valid @RequestBody req: LoginRequest): AuthResponse {
    return toResponse(auth.login(req.email, req.password))
  }

  @PostMapping("/refresh")
  fun refresh(@Valid @RequestBody req: TokenRequest): AuthResponse {
    return toResponse(auth.refresh(req.refreshToken))
  }

  @PostMapping("/logout")
  fun logout(@Valid @RequestBody req: TokenRequest): ResponseEntity<Map<String, String>> {
    auth.logout(req.refreshToken)
    return ResponseEntity.ok(mapOf("status" to "logged out"))
  }

  private fun toResponse(r: AuthService.AuthResult): AuthResponse {
    return AuthResponse(
      r.accessToken, r.refreshToken, r.expiresInMs,
      r.user.id.toString(), r.user.email, r.user.role.name
    )
  }
}
