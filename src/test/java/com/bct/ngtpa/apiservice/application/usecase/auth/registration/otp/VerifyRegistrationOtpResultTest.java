package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerifyRegistrationOtpResultTest {

    @Test
    void resultToStringRedactsSensitiveValues() {
        var result = new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.VERIFIED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(new RegistrationUnregisteredTrust("DB", "00000008802", "20", "DBA")),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null);

        assertThat(result.toString())
                .doesNotContain("123456", "otp_secret", "afs_secret", "csrf_secret", "00000008802", "20", "member@example.com", "3024102", "04/04/1982");
    }

    @Test
    void unregisteredTrustToStringRedactsPolicyAndCert() {
        var trust = new RegistrationUnregisteredTrust("DB", "00000008802", "20", "DBA");
        assertThat(trust.toString()).doesNotContain("00000008802", "20").contains("DB", "DBA");
    }
}
