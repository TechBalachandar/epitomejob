package com.epitomejob.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Hashing helpers. Tokens are stored hashed, never in plain text. */
public final class HashUtil {

  private HashUtil() {}

  /** SHA-256 hex of a token value (refresh tokens, approval tokens). */
  public static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
