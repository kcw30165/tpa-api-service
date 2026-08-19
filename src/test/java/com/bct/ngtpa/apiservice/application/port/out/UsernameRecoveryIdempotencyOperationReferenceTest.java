package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the opaque reservation ownership reference: the durable claim token that proves which
 * concurrent caller owns a Username Recovery idempotency reservation. Values are opaque and {@link
 * #toString()} never discloses the reference.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4 (confirm
 * reservation ownership/idempotency operation) and section 8 (safe persistence). Mirrors the opaque
 * operation-reference conventions used by the auth-flow idempotency ports.
 */
class UsernameRecoveryIdempotencyOperationReferenceTest {

  @Test
  void acceptsOpaqueNonblankValues() {
    assertThat(new UsernameRecoveryIdempotencyOperationReference("op-1234567890").value())
        .isEqualTo("op-1234567890");
    assertThat(
            new UsernameRecoveryIdempotencyOperationReference(
                    "550e8400-e29b-41d4-a716-446655440000")
                .value())
        .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
  }

  @Test
  void rejectsBlankValues() {
    for (String value : List.of("", "   ")) {
      assertThatThrownBy(() -> new UsernameRecoveryIdempotencyOperationReference(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("operation reference must not be null or blank");
    }
  }

  @Test
  void toStringNeverDisclosesTheReference() {
    assertThat(new UsernameRecoveryIdempotencyOperationReference("op-secret").toString())
        .isEqualTo("UsernameRecoveryIdempotencyOperationReference[redacted]");
  }
}
