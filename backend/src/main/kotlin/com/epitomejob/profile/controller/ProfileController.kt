package com.epitomejob.profile.controller

import com.epitomejob.profile.dto.ProfileResponse
import com.epitomejob.profile.dto.ProfileUpdateRequest
import com.epitomejob.profile.service.ProfileService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/profiles")
class ProfileController(private val service: ProfileService) {

  @GetMapping("/me")
  fun getMe(): ProfileResponse {
    return service.getMe()
  }

  @PutMapping("/me")
  fun upsertMe(@Valid @RequestBody incoming: ProfileUpdateRequest): ProfileResponse {
    return service.upsertMe(incoming)
  }
}
