package com.epitomejob.identity.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "identity")
public class User {

  @jakarta.persistence.Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false, unique = true)
  private String email;

  /** BCrypt hash. Null for Google-only accounts (Phase 1). */
  private String passwordHash;

  @Column(nullable = false)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.USER;

  @Column(nullable = false)
  private String status = AppConstants.User.STATUS_ACTIVE;

  @Column(nullable = false)
  private boolean emailVerified = false;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  private Instant lastLoginAt;

  protected User() {}

  public User(String email, String passwordHash, String fullName) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.fullName = fullName;
  }

  public UUID getId() { return id; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public String getFullName() { return fullName; }
  public Role getRole() { return role; }
  public void setRole(Role role) { this.role = role; }
  public boolean isEmailVerified() { return emailVerified; }
  public void setEmailVerified(boolean v) { this.emailVerified = v; }
  public void setLastLoginAt(Instant t) { this.lastLoginAt = t; this.updatedAt = Instant.now(); }
  public void setPasswordHash(String h) { this.passwordHash = h; this.updatedAt = Instant.now(); }
}
