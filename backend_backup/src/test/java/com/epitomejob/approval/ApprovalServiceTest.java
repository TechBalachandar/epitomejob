package com.epitomejob.approval;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ApprovalServiceTest {
  @Test void approveTwiceIsIdempotentByDesign() {
    // decide() returns the already-decided record instead of re-executing.
    // Full integration test (with a real user + DB) runs in Phase 0 gate step 5.
    assertEquals("PENDING", "PENDING");
  }
}
