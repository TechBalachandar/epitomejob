package com.epitomejob.identity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/auth")
public class AuthController {
  private final AuthService auth;
  public AuthController(AuthService auth) { this.auth = auth; }
  public record RegisterRequest(@Email @NotBlank String email, @NotBlank String fullName, @NotBlank String password) {}
  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
  public record TokenRequest(@NotBlank String refreshToken) {}
  public record AuthResponse(String accessToken, String refreshToken, long expiresInMs, String userId, String email, String role) {}
  private AuthResponse toResponse(AuthService.AuthResult r) {
    return new AuthResponse(r.accessToken(), r.refreshToken(), r.expiresInMs(),
        r.user().getId().toString(), r.user().getEmail(), r.user().getRole().name());
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
}
