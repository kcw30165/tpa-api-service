package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernameRecoverySessionDeleteResultTest {

    @Test
    void acceptsOnlyDeleteOutcomes() {
        assertThat(new UsernameRecoverySessionDeleteResult(
                UsernameRecoverySessionMutationOutcome.DELETED).outcome())
                .isEqualTo(UsernameRecoverySessionMutationOutcome.DELETED);
        assertThat(new UsernameRecoverySessionDeleteResult(
                UsernameRecoverySessionMutationOutcome.ALREADY_ABSENT).outcome())
                .isEqualTo(UsernameRecoverySessionMutationOutcome.ALREADY_ABSENT);
        assertThat(new UsernameRecoverySessionDeleteResult(
                UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE).outcome())
                .isEqualTo(UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE);
        assertThatThrownBy(() -> new UsernameRecoverySessionDeleteResult(
                UsernameRecoverySessionMutationOutcome.CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outcome is not valid for delete");
    }

    @Test
    void requiresOutcome() {
        assertThatThrownBy(() -> new UsernameRecoverySessionDeleteResult(null))
                .isInstanceOf(NullPointerException.class);
    }
}
