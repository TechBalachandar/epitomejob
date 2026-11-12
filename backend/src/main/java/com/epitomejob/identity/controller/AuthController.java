package com.epitomejob.identity.controller;

import com.epitomejob.identity.dto.AuthResponse;
import com.epitomejob.identity.dto.LoginRequest;
import com.epitomejob.identity.dto.RegisterRequest;
import com.epitomejob.identity.dto.TokenRequest;
import com.epitomejob.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
    auth.register(req.email(), req.fullName(), req.password());
    var result = auth.login(req.email(), req.password());
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest req) {
    return toResponse(auth.login(req.email(), req.password()));
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(@Valid @RequestBody TokenRequest req) {
    return toResponse(auth.refresh(req.refreshToken()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody TokenRequest req) {
    auth.logout(req.refreshToken());
    return ResponseEntity.ok(Map.of("status", "logged out"));
  }

  private AuthResponse toResponse(AuthService.AuthResult r) {
    return new AuthResponse(r.accessToken(), r.refreshToken(), r.expiresInMs(),
        r.user().getId().toString(), r.user().getEmail(), r.user().getRole().name());
  }
}
