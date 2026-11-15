package com.epitomejob.common.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Hashing helpers. Tokens are stored hashed, never in plain text.
 *
 * `object` with `@JvmStatic` so the Java callers (`AuthService`,
 * `ApprovalService`) keep calling `HashUtil.sha256(...)` unchanged.
 */
object HashUtil {

  /** SHA-256 hex of a token value (refresh tokens, approval tokens). */
  @JvmStatic
  fun sha256(value: String): String {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
      )
    } catch (e: Exception) {
      throw IllegalStateException(e)
    }
  }
}
