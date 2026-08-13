package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the Username Recovery idempotency action vocabulary: one action per protected
 * mutation (start, request, cancel) with a stable key namespace.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * sections 4-6 (start/request/cancel are protected mutations) and
 * {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4
 * (idempotency operation ownership for the request delivery path). Mirrors
 * {@code RegistrationIdempotencyAction} naming.
 */
class UsernameRecoveryIdempotencyActionTest {

    @Test
    void exposesOneActionPerProtectedMutation() {
        assertThat(UsernameRecoveryIdempotencyAction.values()).containsExactly(
                UsernameRecoveryIdempotencyAction.START,
                UsernameRecoveryIdempotencyAction.REQUEST,
                UsernameRecoveryIdempotencyAction.CANCEL);
    }

    @Test
    void definesStableKeyNamespaces() {
        assertThat(UsernameRecoveryIdempotencyAction.START.keyNamespace())
                .isEqualTo("username-recovery-start");
        assertThat(UsernameRecoveryIdempotencyAction.REQUEST.keyNamespace())
                .isEqualTo("username-recovery-request");
        assertThat(UsernameRecoveryIdempotencyAction.CANCEL.keyNamespace())
                .isEqualTo("username-recovery-cancel");
    }
}
