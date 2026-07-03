package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartRegistrationResultTest {
    @Test
    void startRegistrationResultToStringRedactsSensitiveValues() {
        var result = new StartRegistrationResult(
                StartRegistrationResultStatus.STARTED,
                WorkflowNextStep.VERIFY_OTP,
                "m****r@example.com",
                300,
                60,
                3,
                "csrf_secret",
                "afs_secret",
                List.of(),
                List.of(new StartRegistrationError(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED, "identityValue")),
                RegistrationIdempotencyStartOutcome.STARTED,
                null);

        assertThat(result.toString())
                .doesNotContain("csrf_secret", "afs_secret", "member@example.com", "91234567", "A1234567", "otp_secret", "otp_challenge_internal", "activeOtpChallengeId", "otpCode")
                .contains("m****r@example.com");
    }

    @Test
    void startRegistrationCommandToStringRedactsSensitiveValues() {
        var command = new StartRegistrationCommand(
                new RegistrationIdempotencyKey("idem_abc123"),
                "GPFS",
                "1234567890",
                "31/01/1980",
                "member@example.com",
                null,
                "cap_secret",
                "en",
                "corr-secret",
                Duration.ofMinutes(5));

        assertThat(command.toString())
                .doesNotContain("idem_abc123", "1234567890", "31/01/1980", "member@example.com", "cap_secret", "corr-secret");
    }
}
