package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import org.junit.jupiter.api.Test;

class UsernameRecoveryCancelCommandTest {

    private static final String IDEMPOTENCY_KEY = "idem-cancel-123";

    @Test
    void requiresSessionAndIdempotencyKey() {
        assertThatThrownBy(() -> new UsernameRecoveryCancelCommand(null, IDEMPOTENCY_KEY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryCancelCommand(new AuthFlowSessionId("afs_1"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        assertThatThrownBy(() -> new UsernameRecoveryCancelCommand(new AuthFlowSessionId("afs_1"), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username recovery cancel command idempotencyKey must not be blank");
    }

    @Test
    void trimsIdempotencyKeyWithoutChangingCase() {
        var command = new UsernameRecoveryCancelCommand(new AuthFlowSessionId("afs_opaque"), "  IdEm_CaNcEl_123  ");

        assertThat(command.authFlowSessionId().value()).isEqualTo("afs_opaque");
        assertThat(command.idempotencyKey()).isEqualTo("IdEm_CaNcEl_123");
    }

    @Test
    void retainsSessionAndIdempotencyKey() {
        var command = new UsernameRecoveryCancelCommand(new AuthFlowSessionId("afs_opaque"), IDEMPOTENCY_KEY);

        assertThat(command.authFlowSessionId().value()).isEqualTo("afs_opaque");
        assertThat(command.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void toStringDoesNotDiscloseSessionOrIdempotencyKey() {
        var command = new UsernameRecoveryCancelCommand(new AuthFlowSessionId("afs_secret"), "idem_secret_key");

        assertThat(command.toString())
                .doesNotContain("afs_secret", "idem_secret_key")
                .contains("authFlowSessionId=<redacted>", "idempotencyKey=<redacted>");
    }
}
