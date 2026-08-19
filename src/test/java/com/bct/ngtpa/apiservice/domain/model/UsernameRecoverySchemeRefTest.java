package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the opaque Username Recovery Scheme-Ref value object required by the approved final
 * design.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 6 step
 *       11 (opaque Scheme-Ref creation) and section 11 (Scheme-Ref excluded from logs);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-test-traceability-design.md}
 *       UR-R12 (Scheme-Ref opaque and session-bound);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4
 *       (opaque schemeRef values such as {@code "sch_opaque-1"}).
 * </ul>
 *
 * <p>Scheme-Ref is opaque and non-semantic: no business meaning is parsed from the value and it is
 * never treated as an Account-Ref. Generation and entropy belong to a later task.
 */
class UsernameRecoverySchemeRefTest {

  @Test
  void wrapsOpaqueValue() {
    UsernameRecoverySchemeRef schemeRef = new UsernameRecoverySchemeRef("sch_opaque-1");

    assertEquals("sch_opaque-1", schemeRef.value());
  }

  @Test
  void rejectsNullOrBlankValue() {
    assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySchemeRef(null));
    assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySchemeRef(""));
    assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySchemeRef("   "));
  }

  @Test
  void treatsValueAsOpaqueAndNonSemantic() {
    // No format, pattern, or business-meaning parsing is applied to the opaque value.
    assertDoesNotThrow(() -> new UsernameRecoverySchemeRef("anything-opaque-123"));
    assertNotEquals(new UsernameRecoverySchemeRef("a"), new UsernameRecoverySchemeRef("b"));
    assertEquals(new UsernameRecoverySchemeRef("opaque"), new UsernameRecoverySchemeRef("opaque"));
  }

  @Test
  void toStringDoesNotDiscloseRawValue() {
    assertEquals(
        "UsernameRecoverySchemeRef[value=<redacted>]",
        new UsernameRecoverySchemeRef("sch_secret-9").toString());
  }
}
