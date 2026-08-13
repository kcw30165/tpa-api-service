package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException.Reason;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 021 {@link UsernameRecoveryCancelException} typed rejection signal.
 *
 * <p>Every distinct non-result cancel condition is a typed {@link Reason} constant:
 * validation failure, missing security context, invalid CSRF/origin, idempotency in progress,
 * key reused, stable unknown outcome, revision/concurrency conflict, and definite system failure
 * (Task 021 "Keep distinct" list; reviewed audit decision section 6).
 *
 * <p>Invariants verified: {@code reason} is always present, the message never contains raw
 * exception text or request secrets, and {@link #toString()} is fully redacted.
 */
class UsernameRecoveryCancelExceptionTest {

    @Test
    void eachFactoryProducesTheDistinctReason() {
        assertThat(UsernameRecoveryCancelException.validationFailed().reason())
                .isEqualTo(Reason.VALIDATION_FAILED);
        assertThat(UsernameRecoveryCancelException.securityContextRequired().reason())
                .isEqualTo(Reason.SECURITY_CONTEXT_REQUIRED);
        assertThat(UsernameRecoveryCancelException.securityInvalid().reason())
                .isEqualTo(Reason.SECURITY_INVALID);
        assertThat(UsernameRecoveryCancelException.idempotencyInProgress().reason())
                .isEqualTo(Reason.IDEMPOTENCY_IN_PROGRESS);
        assertThat(UsernameRecoveryCancelException.keyReused().reason())
                .isEqualTo(Reason.IDEMPOTENCY_KEY_REUSED);
        assertThat(UsernameRecoveryCancelException.unknownOutcomeExisting().reason())
                .isEqualTo(Reason.UNKNOWN_OUTCOME_EXISTING);
        assertThat(UsernameRecoveryCancelException.revisionConflict().reason())
                .isEqualTo(Reason.REVISION_CONFLICT);
        assertThat(UsernameRecoveryCancelException.systemFailure().reason())
                .isEqualTo(Reason.SYSTEM_FAILURE);
    }

    @Test
    void systemFailureCarriesTheOriginalCauseWithoutDisclosingItsText() {
        Throwable cause = new IllegalStateException("raw session secret value");

        UsernameRecoveryCancelException exception =
                UsernameRecoveryCancelException.systemFailure(cause);

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage())
                .doesNotContain("raw session secret value")
                .doesNotContain("secret");
    }

    @Test
    void toStringIsFullyRedacted() {
        UsernameRecoveryCancelException exception = UsernameRecoveryCancelException.systemFailure(
                new IllegalStateException("csrf-token-secret session-secret idem-key-secret"));

        assertThat(exception.toString())
                .isEqualTo("UsernameRecoveryCancelException[reason=SYSTEM_FAILURE]")
                .doesNotContain("csrf", "session", "idem", "secret");
    }

    @Test
    void everyFactoryHasARedactedToString() {
        assertThat(UsernameRecoveryCancelException.validationFailed().toString())
                .isEqualTo("UsernameRecoveryCancelException[reason=VALIDATION_FAILED]");
        assertThat(UsernameRecoveryCancelException.securityContextRequired().toString())
                .isEqualTo("UsernameRecoveryCancelException[reason=SECURITY_CONTEXT_REQUIRED]");
        assertThat(UsernameRecoveryCancelException.securityInvalid().toString())
                .isEqualTo("UsernameRecoveryCancelException[reason=SECURITY_INVALID]");
        assertThat(UsernameRecoveryCancelException.idempotencyInProgress().toString())
                .isEqualTo("UsernameRecoveryCancelException[reason=IDEMPOTENCY_IN_PROGRESS]");
        assertThat(UsernameRecoveryCancelException.keyReused().toString())
                .isEqualTo("UsernameRecoveryCancelException[reason=IDEMPOTENCY_KEY_REUSED]");
        assertThat(UsernameRecoveryCancelException.unknownOutcomeExisting().toString())
                .isEqualTo("UsernameRecoveryCancelException[reason=UNKNOWN_OUTCOME_EXISTING]");
        assertThat(UsernameRecoveryCancelException.revisionConflict().toString())
                .isEqualTo("UsernameRecoveryCancelException[reason=REVISION_CONFLICT]");
    }

    @Test
    void messageIsTypedAndNeverCarriesRequestValues() {
        UsernameRecoveryCancelException exception =
                UsernameRecoveryCancelException.securityInvalid();

        assertThat(exception.getMessage())
                .contains("Username recovery cancel rejected")
                .contains("SECURITY_INVALID");
    }
}
