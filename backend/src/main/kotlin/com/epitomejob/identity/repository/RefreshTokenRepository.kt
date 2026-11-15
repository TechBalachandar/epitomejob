package com.epitomejob.identity.repository

import com.epitomejob.identity.model.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.List
import java.util.Optional
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
  fun findByTokenHash(tokenHash: String): Optional<RefreshToken>
  fun findByUserIdAndRevokedFalse(userId: UUID): List<RefreshToken>
}
