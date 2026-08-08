package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernameRecoverySessionLoadResultTest {

    @Test
    void absentAndInfrastructureFailureAreDistinctTypedOutcomes() {
        assertThat(UsernameRecoverySessionLoadResult.notFound().outcome())
                .isEqualTo(UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND);
        assertThat(UsernameRecoverySessionLoadResult.systemFailure().outcome())
                .isEqualTo(UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE);
    }

    @Test
    void loadedRequiresSessionAndRejectsAnotherOperationOutcome() {
        assertThatThrownBy(() -> new UsernameRecoverySessionLoadResult(
                UsernameRecoverySessionMutationOutcome.LOADED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("loaded outcome requires a session");
        assertThatThrownBy(() -> new UsernameRecoverySessionLoadResult(
                UsernameRecoverySessionMutationOutcome.DELETED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outcome is not valid for load");
    }

    @Test
    void toStringDoesNotExposeSessionContent() {
        assertThat(UsernameRecoverySessionLoadResult.notFound().toString())
                .isEqualTo(
                        "UsernameRecoverySessionLoadResult[outcome=SESSION_NOT_FOUND, sessionPresent=false]");
    }
}
