package com.epitomejob.identity.repository

import com.epitomejob.identity.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
  fun findByEmail(email: String): Optional<User>
  fun existsByEmail(email: String): Boolean
}
