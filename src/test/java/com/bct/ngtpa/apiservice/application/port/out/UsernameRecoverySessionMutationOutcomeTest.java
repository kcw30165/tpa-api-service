package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsernameRecoverySessionMutationOutcomeTest {

    @Test
    void exposesRequiredDeterministicOutcomes() {
        assertThat(UsernameRecoverySessionMutationOutcome.values()).containsExactlyInAnyOrder(
                UsernameRecoverySessionMutationOutcome.CREATED,
                UsernameRecoverySessionMutationOutcome.LOADED,
                UsernameRecoverySessionMutationOutcome.APPLIED,
                UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND,
                UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT,
                UsernameRecoverySessionMutationOutcome.DELETED,
                UsernameRecoverySessionMutationOutcome.ALREADY_ABSENT,
                UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE);
    }
}
