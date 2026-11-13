package com.epitomejob.profile.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Partial update: every field is optional, null means "leave unchanged". */
public record ProfileUpdateRequest(
    @Size(max = 200) String headline,
    @Size(max = 2000) String summary,
    @Size(max = 120) String location,
    @Size(max = 20) String workType,
    @PositiveOrZero Integer salaryMin,
    @PositiveOrZero Integer salaryMax,
    @Size(max = 30) List<@Size(max = 60) String> skills,
    LocalDate h1bClockStartDate,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    String visibility) {}
