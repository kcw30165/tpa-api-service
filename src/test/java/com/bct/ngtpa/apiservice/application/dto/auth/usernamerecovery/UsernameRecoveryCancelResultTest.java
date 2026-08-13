package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernameRecoveryCancelResultTest {

    @Test
    void exposesSuccessAndSystemFailedOutcomes() {
        assertThat(UsernameRecoveryCancelResult.Outcome.values()).containsExactly(
                UsernameRecoveryCancelResult.Outcome.SUCCESS,
                UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED);
    }

    @Test
    void retainsOutcome() {
        var result = new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS);

        assertThat(result.outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS);
    }

    @Test
    void requiresOutcome() {
        assertThatThrownBy(() -> new UsernameRecoveryCancelResult(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringIsSafe() {
        var result = new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED);

        assertThat(result.toString()).isEqualTo("UsernameRecoveryCancelResult[outcome=SYSTEM_FAILED]");
    }
}
