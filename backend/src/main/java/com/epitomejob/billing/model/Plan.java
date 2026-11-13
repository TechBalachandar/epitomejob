package com.epitomejob.billing.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A purchasable plan (W4 contract §1b). Seeded by V5__billing.sql; the
 * service never invents plans. {@code currency} is CHAR(3) in the migration,
 * so the JDBC type is declared explicitly — without it Hibernate's
 * ddl-auto=validate rejects a String mapped to a CHAR column.
 */
@Entity
@Table(name = "plans", schema = "billing")
public class Plan {

  @Id
  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "price_cents", nullable = false)
  private int priceCents;

  @Column(name = "currency", nullable = false)
  @JdbcTypeCode(SqlTypes.CHAR)
  private String currency = AppConstants.Profile.DEFAULT_CURRENCY;

  @Column(name = "duration_days")
  private Integer durationDays;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  protected Plan() {}

  public Plan(String code, String name, int priceCents, String currency,
              Integer durationDays, String description, boolean active) {
    this.code = code;
    this.name = name;
    this.priceCents = priceCents;
    this.currency = currency;
    this.durationDays = durationDays;
    this.description = description;
    this.active = active;
  }

  public String getCode() { return code; }
  public String getName() { return name; }
  public int getPriceCents() { return priceCents; }
  public String getCurrency() { return currency; }
  public Integer getDurationDays() { return durationDays; }
  public String getDescription() { return description; }
  public boolean isActive() { return active; }
}
