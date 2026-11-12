package com.epitomejob.profile.controller;

import com.epitomejob.profile.dto.ProfileResponse;
import com.epitomejob.profile.dto.ProfileUpdateRequest;
import com.epitomejob.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profiles")
public class ProfileController {

  private final ProfileService service;

  public ProfileController(ProfileService service) {
    this.service = service;
  }

  @GetMapping("/me")
  public ProfileResponse getMe() {
    return service.getMe();
  }

  @PutMapping("/me")
  public ProfileResponse upsertMe(@Valid @RequestBody ProfileUpdateRequest incoming) {
    return service.upsertMe(incoming);
  }
}
