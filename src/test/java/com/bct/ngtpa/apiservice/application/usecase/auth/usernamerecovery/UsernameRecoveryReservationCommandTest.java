package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Username Recovery reservation command: required typed inputs, rejection of null
 * components, and safe redaction of the session id and opaque Scheme-Ref.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 5 (request targets a single session-bound opaque Scheme-Ref; CSRF token is transported
 * separately and is never part of the command) and section 8 (UR-REQ-002 CSRF, UR-REQ-003 Scheme-Ref)
 * and {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 6
 * (request is bound to the auth-flow session).
 *
 * <p>Invariants: {@code sessionId}, {@code schemeRef}, and the authoritative {@code now} instant are
 * all required. The command never carries the raw CSRF token. {@link #toString()} never discloses
 * the session id or the opaque Scheme-Ref.
 */
class UsernameRecoveryReservationCommandTest {

    private static final UsernameRecoverySessionId SESSION_ID =
            new UsernameRecoverySessionId("afs_opaque-1");
    private static final UsernameRecoverySchemeRef SCHEME_REF =
            new UsernameRecoverySchemeRef("sch_opaque-1");
    private static final Instant NOW = Instant.parse("2026-01-01T00:05:00Z");

    @Test
    void retainsTypedCommandComponents() {
        UsernameRecoveryReservationCommand command =
                new UsernameRecoveryReservationCommand(SESSION_ID, SCHEME_REF, NOW);

        assertThat(command.sessionId()).isEqualTo(SESSION_ID);
        assertThat(command.schemeRef()).isEqualTo(SCHEME_REF);
        assertThat(command.now()).isEqualTo(NOW);
    }

    @Test
    void rejectsNullSessionId() {
        assertThatThrownBy(() -> new UsernameRecoveryReservationCommand(null, SCHEME_REF, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sessionId must not be null");
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
    void toStringRedactsSessionIdAndSchemeRef() {
        UsernameRecoveryReservationCommand command =
                new UsernameRecoveryReservationCommand(SESSION_ID, SCHEME_REF, NOW);

        assertThat(command.toString())
                .doesNotContain("afs_opaque-1")
                .doesNotContain("sch_opaque-1")
                .contains("<redacted>");
    }
}
