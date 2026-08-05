package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ForgotPasswordResetAuthorizationValidatorTest {
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    private final ForgotPasswordResetAuthorizationValidator validator = new ForgotPasswordResetAuthorizationValidator();

    @Test
    void validatesActiveResetAuthorizationAgainstSessionAndSubject() {
        var result = validator.validate(authorization(null, NOW.plusSeconds(300)),
                session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.RESET_AUTHORIZED),
                new AuthFlowSessionId("afs_fp"), "reset-subject-01", NOW);

        assertThat(result.status()).isEqualTo(ForgotPasswordResetAuthorizationValidationStatus.VALID);
    }

    @Test
    void rejectsWrongSessionWrongScenarioWrongSubjectExpiryConsumedTerminalAndUnknownOutcomeBlocked() {
        assertThat(validator.validate(authorization(null, NOW.plusSeconds(300)),
                session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.RESET_AUTHORIZED),
                new AuthFlowSessionId("afs_other"), "reset-subject-01", NOW).status())
                .isEqualTo(ForgotPasswordResetAuthorizationValidationStatus.WRONG_SESSION);

        assertThat(validator.validate(authorization(null, NOW.plusSeconds(300)),
                session(AuthFlowScenario.LOGIN, AuthFlowStatus.RESET_AUTHORIZED),
                new AuthFlowSessionId("afs_fp"), "reset-subject-01", NOW).status())
                .isEqualTo(ForgotPasswordResetAuthorizationValidationStatus.WRONG_SCENARIO);

        assertThat(validator.validate(authorization(null, NOW.plusSeconds(300)),
                session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.RESET_AUTHORIZED),
                new AuthFlowSessionId("afs_fp"), "reset-subject-02", NOW).status())
                .isEqualTo(ForgotPasswordResetAuthorizationValidationStatus.WRONG_SUBJECT);

        assertThat(validator.validate(authorization(null, NOW.minusSeconds(1)),
                session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.RESET_AUTHORIZED),
                new AuthFlowSessionId("afs_fp"), "reset-subject-01", NOW).status())
                .isEqualTo(ForgotPasswordResetAuthorizationValidationStatus.EXPIRED);

        assertThat(validator.validate(authorization(NOW.minusSeconds(1), NOW.plusSeconds(300)),
                session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.RESET_AUTHORIZED),
                new AuthFlowSessionId("afs_fp"), "reset-subject-01", NOW).status())
                .isEqualTo(ForgotPasswordResetAuthorizationValidationStatus.CONSUMED);

        assertThat(validator.validate(authorization(null, NOW.plusSeconds(300)),
                session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.CANCELLED),
                new AuthFlowSessionId("afs_fp"), "reset-subject-01", NOW).status())
                .isEqualTo(ForgotPasswordResetAuthorizationValidationStatus.TERMINAL_SESSION);

        assertThat(validator.validate(authorization(null, NOW.plusSeconds(300)),
                session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED),
                new AuthFlowSessionId("afs_fp"), "reset-subject-01", NOW).status())
                .isEqualTo(ForgotPasswordResetAuthorizationValidationStatus.UNKNOWN_OUTCOME_BLOCKED);
    }

    private static ForgotPasswordResetAuthorization authorization(Instant consumedAt, Instant expiresAt) {
        return new ForgotPasswordResetAuthorization(
                "authz-123", "afs_fp", "reset-subject-01", NOW.minusSeconds(60), expiresAt, consumedAt);
    }

    private static AuthFlowSession session(AuthFlowScenario scenario, AuthFlowStatus status) {
        return new AuthFlowSession(new AuthFlowSessionId("afs_fp"), scenario, status, null, "otp-fp",
                AuthFlowSessionData.empty(), NOW, NOW, NOW.plusSeconds(300));
    }
}