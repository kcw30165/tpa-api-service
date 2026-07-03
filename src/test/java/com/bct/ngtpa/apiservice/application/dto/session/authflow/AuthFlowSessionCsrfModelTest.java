package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfValidationStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.ValidateAuthFlowSessionCsrfCommand;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthFlowSessionCsrfModelTest {

    @Test
    void authFlowSessionCsrfValidationStatusDefinesExpectedValues() {
        assertThat(AuthFlowSessionCsrfValidationStatus.values()).containsExactly(
                AuthFlowSessionCsrfValidationStatus.VALID,
                AuthFlowSessionCsrfValidationStatus.SESSION_MISSING,
                AuthFlowSessionCsrfValidationStatus.SESSION_EXPIRED,
                AuthFlowSessionCsrfValidationStatus.SESSION_NOT_USABLE,
                AuthFlowSessionCsrfValidationStatus.TOKEN_MISSING,
                AuthFlowSessionCsrfValidationStatus.TOKEN_HASH_MISSING,
                AuthFlowSessionCsrfValidationStatus.TOKEN_MISMATCH,
                AuthFlowSessionCsrfValidationStatus.ORIGIN_REFERER_INVALID,
                AuthFlowSessionCsrfValidationStatus.INVALID_INPUT);
    }

    @Test
    void csrfServiceDoesNotExposeTokenHashOrRawTokenInToString() {
        var result = new CreateAuthFlowSessionCsrfResult("csrf_secret_value", Instant.parse("2026-06-29T00:30:00Z"), 1800);
        var command = new ValidateAuthFlowSessionCsrfCommand(
                new AuthFlowSessionId("afs_secret"),
                "csrf_secret_value",
                Instant.parse("2026-06-29T00:00:00Z"),
                null,
                null,
                java.util.List.of(),
                "corr-1");

        assertThat(result.toString()).doesNotContain("csrf_secret_value");
        assertThat(command.toString()).doesNotContain("csrf_secret_value").doesNotContain("afs_secret");
    }
}
