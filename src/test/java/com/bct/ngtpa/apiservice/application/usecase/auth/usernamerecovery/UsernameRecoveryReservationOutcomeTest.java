package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the typed reservation outcome vocabulary required by Task 015.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 8 (UR-REQ-001..011 outcomes) and section 7 (HTTP mapping rules) and
 * {@code .cline/tasks/015-csrf-session-integration.md} "Typed outcome expectations" (the bounded
 * result must distinguish reserved/applied, absent/invalid, expired, CSRF invalid, Scheme-Ref
 * invalid, registration required, already requested, terminal, revision conflict, and system
 * failure).
 *
 * <p>Each expected workflow outcome is a typed enum constant; no null, empty {@code Mono},
 * exception, or arbitrary string is used for expected outcomes.
 */
class UsernameRecoveryReservationOutcomeTest {

    @Test
    void definesAllRequiredTypedOutcomes() {
        assertThat(UsernameRecoveryReservationOutcome.values())
                .containsExactlyInAnyOrder(
                        UsernameRecoveryReservationOutcome.RESERVED,
                        UsernameRecoveryReservationOutcome.SESSION_ABSENT,
                        UsernameRecoveryReservationOutcome.SESSION_EXPIRED,
                        UsernameRecoveryReservationOutcome.CSRF_INVALID,
                        UsernameRecoveryReservationOutcome.SCHEME_REF_INVALID,
                        UsernameRecoveryReservationOutcome.REGISTRATION_REQUIRED,
                        UsernameRecoveryReservationOutcome.ALREADY_REQUESTED,
                        UsernameRecoveryReservationOutcome.CANDIDATE_NOT_AVAILABLE,
                        UsernameRecoveryReservationOutcome.WORKFLOW_TERMINAL,
                        UsernameRecoveryReservationOutcome.REVISION_CONFLICT,
                        UsernameRecoveryReservationOutcome.SYSTEM_FAILED);
    }
}
