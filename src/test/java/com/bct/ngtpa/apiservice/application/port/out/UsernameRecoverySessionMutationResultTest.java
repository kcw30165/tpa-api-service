package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernameRecoverySessionMutationResultTest {

    @Test
    void nonAppliedTypedOutcomesCarryNoSession() {
        for (var outcome : new UsernameRecoverySessionMutationOutcome[] {
                UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND,
                UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT,
                UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE}) {
            var result = UsernameRecoverySessionMutationResult.outcome(outcome);
            assertThat(result.outcome()).isEqualTo(outcome);
            assertThat(result.session()).isNull();
        }
    }

    @Test
    void rejectsOutcomeFromAnotherOperation() {
        assertThatThrownBy(() -> UsernameRecoverySessionMutationResult.outcome(
                UsernameRecoverySessionMutationOutcome.DELETED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outcome is not valid for create or update");
    }

    @Test
    void appliedOutcomeRequiresStoredSession() {
        assertThatThrownBy(() -> new UsernameRecoverySessionMutationResult(
                UsernameRecoverySessionMutationOutcome.APPLIED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("created or applied mutation requires the stored session");
    }
}
