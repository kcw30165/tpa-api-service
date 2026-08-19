package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResendRegistrationOtpResultTest {

  @Test
  void resultToStringRedactsSensitiveValues() {
    var result =
        new ResendRegistrationOtpResult(
            ResendRegistrationOtpResultStatus.RESENT,
            WorkflowNextStep.VERIFY_OTP,
            "m****r@example.com",
            300,
            60,
            4,
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);

    assertThat(result.toString())
        .doesNotContain("123456", "otp_", "afs_secret", "csrf_secret", "member@example.com");
  }
}
