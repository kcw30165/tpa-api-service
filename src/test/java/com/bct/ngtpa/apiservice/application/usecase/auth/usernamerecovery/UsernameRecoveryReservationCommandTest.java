package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Username Recovery reservation command under the Model B canonical identity:
 * required typed inputs, rejection of null components, safe redaction, and the transitional
 * legacy bridge that keeps committed callers compiling until Repairs 025B/025C migrate them.
 *
 * <p>Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR}: the command's canonical
 * session identifier is {@link AuthFlowSessionId}; the legacy {@code sessionId} component was
 * replaced. Raw CSRF remains outside the command; Scheme-Ref and timestamp behavior are unchanged.
 */
class UsernameRecoveryReservationCommandTest {

    private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_opaque-1");
    private static final UsernameRecoverySchemeRef SCHEME_REF =
            new UsernameRecoverySchemeRef("sch_opaque-1");
    private static final Instant NOW = Instant.parse("2026-01-01T00:05:00Z");

    @Test
    void retainsCanonicalTypedCommandComponents() {
        UsernameRecoveryReservationCommand command =
                new UsernameRecoveryReservationCommand(SESSION_ID, SCHEME_REF, NOW);

        assertThat(command.authFlowSessionId()).isEqualTo(SESSION_ID);
        assertThat(command.schemeRef()).isEqualTo(SCHEME_REF);
        assertThat(command.now()).isEqualTo(NOW);
    }

    @Test
    void rejectsNullAuthFlowSessionId() {
        AuthFlowSessionId missing = null;
        assertThatThrownBy(() -> new UsernameRecoveryReservationCommand(missing, SCHEME_REF, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("authFlowSessionId must not be null");
    }

    @Test
    void rejectsNullSchemeRef() {
        assertThatThrownBy(() -> new UsernameRecoveryReservationCommand(SESSION_ID, null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("schemeRef must not be null");
    }

    @Test
    void rejectsNullNow() {
        assertThatThrownBy(() -> new UsernameRecoveryReservationCommand(SESSION_ID, SCHEME_REF, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now must not be null");
    }

    @Test
    void toStringRedactsAuthFlowSessionIdAndSchemeRef() {
        UsernameRecoveryReservationCommand command =
                new UsernameRecoveryReservationCommand(SESSION_ID, SCHEME_REF, NOW);

        assertThat(command.toString())
                .doesNotContain("afs_opaque-1")
                .doesNotContain("sch_opaque-1")
                .contains("<redacted>");
    }

    @Test
    void transitionalLegacyConstructorPreservesCanonicalValue() {
        UsernameRecoverySessionId legacy = new UsernameRecoverySessionId("afs_opaque-1");
        UsernameRecoveryReservationCommand command =
                new UsernameRecoveryReservationCommand(legacy, SCHEME_REF, NOW);

        assertThat(command.authFlowSessionId().value()).isEqualTo("afs_opaque-1");
    }

    @Test
    void transitionalSessionIdAccessorDerivesLegacyIdentifierFromCanonicalValue() {
        UsernameRecoveryReservationCommand command =
                new UsernameRecoveryReservationCommand(SESSION_ID, SCHEME_REF, NOW);

        assertThat(command.sessionId().value()).isEqualTo("afs_opaque-1");
    }
}
