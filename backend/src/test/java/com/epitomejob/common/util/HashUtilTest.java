package com.epitomejob.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HashUtilTest {

  @Test
  void sha256MatchesKnownVector() {
    // SHA-256("abc") — FIPS 180-4 test vector.
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        HashUtil.sha256("abc"));
  }

  @Test
  void sha256IsDeterministic() {
    assertEquals(HashUtil.sha256("refresh-token-1"), HashUtil.sha256("refresh-token-1"));
  }

  @Test
  void sha256DiffersForDifferentInputs() {
    assertNotEquals(HashUtil.sha256("token-a"), HashUtil.sha256("token-b"));
  }
}
