package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;

class UsernameRecoveryReservationResultTest {

    @Test
    void reservedOutcomeIsADataMinimizedSuccessProjection() {
        var result = UsernameRecoveryReservationResult.reserved();
        assertThat(result.outcome()).isEqualTo(UsernameRecoveryReservationOutcome.RESERVED);
        assertThat(UsernameRecoveryReservationResult.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("outcome");
    }

    @Test
    void rejectsNullOutcome() {
        assertThatThrownBy(() -> new UsernameRecoveryReservationResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("outcome must not be null");
    }

    @Test
    void outcomeFactoriesProduceTypedResults() {
        assertThat(UsernameRecoveryReservationResult.absent().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.SESSION_ABSENT);
        assertThat(UsernameRecoveryReservationResult.expired().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.SESSION_EXPIRED);
        assertThat(UsernameRecoveryReservationResult.csrfInvalid().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.CSRF_INVALID);
        assertThat(UsernameRecoveryReservationResult.schemeRefInvalid().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.SCHEME_REF_INVALID);
        assertThat(UsernameRecoveryReservationResult.registrationRequired().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.REGISTRATION_REQUIRED);
        assertThat(UsernameRecoveryReservationResult.alreadyRequested().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.ALREADY_REQUESTED);
        assertThat(UsernameRecoveryReservationResult.candidateNotAvailable().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.CANDIDATE_NOT_AVAILABLE);
        assertThat(UsernameRecoveryReservationResult.terminal().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.WORKFLOW_TERMINAL);
        assertThat(UsernameRecoveryReservationResult.revisionConflict().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.REVISION_CONFLICT);
        assertThat(UsernameRecoveryReservationResult.systemFailed().outcome())
                .isEqualTo(UsernameRecoveryReservationOutcome.SYSTEM_FAILED);
    }

    @Test
    void toStringContainsNoProtectedSessionData() {
        assertThat(UsernameRecoveryReservationResult.reserved().toString())
                .isEqualTo("UsernameRecoveryReservationResult[outcome=RESERVED]");
    }
}
