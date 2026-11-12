package com.epitomejob;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: the whole application context starts (H2 test profile). */
@SpringBootTest
@ActiveProfiles("test")
class EpitomeJobApplicationTests {

  @Test
  void contextLoads() {
  }
}
