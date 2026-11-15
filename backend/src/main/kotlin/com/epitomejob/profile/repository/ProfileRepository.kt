package com.epitomejob.profile.repository

import com.epitomejob.profile.model.Profile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface ProfileRepository : JpaRepository<Profile, UUID> {
  fun findByUserId(userId: UUID): Optional<Profile>
}
