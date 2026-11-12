package com.epitomejob.profile.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileTest {

  private Profile profile() {
    return new Profile(UUID.randomUUID());
  }

  @Test
  void emptyProfileScoresZero() {
    assertEquals(0, profile().computeCompleteness());
  }

  @Test
  void eachFieldIsWorthTwentyPoints() {
    Profile p = profile();
    p.setHeadline("Java Developer");
    assertEquals(20, p.computeCompleteness());
    p.setSummary("17 years of experience");
    assertEquals(40, p.computeCompleteness());
    p.setLocation("Reston, VA");
    assertEquals(60, p.computeCompleteness());
    p.setWorkType("REMOTE");
    assertEquals(80, p.computeCompleteness());
    p.setSalaryMin(150_000);
    assertEquals(80, p.computeCompleteness(), "salary counts only when min AND max are set");
    p.setSalaryMax(200_000);
    assertEquals(100, p.computeCompleteness());
  }

  @Test
  void blankStringsDoNotCount() {
    Profile p = profile();
    p.setHeadline("   ");
    p.setSummary("");
    assertEquals(0, p.computeCompleteness());
  }
}
