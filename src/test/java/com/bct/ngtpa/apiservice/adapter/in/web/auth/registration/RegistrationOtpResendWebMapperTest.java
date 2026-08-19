package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationOtpResendWebMapperTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RegistrationOtpResendWebMapper mapper = new RegistrationOtpResendWebMapper(null);

  @Test
  void successMapsToWorkflowMutationResponseWithoutSensitiveValues() throws Exception {
    var response =
        mapper.toMutationResponse(
            new ResendRegistrationOtpResult(
                ResendRegistrationOtpResultStatus.RESENT,
                WorkflowNextStep.VERIFY_OTP,
                "s****v@hcglt.nla",
                287,
                47,
                5,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

    String json = objectMapper.writeValueAsString(response);
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(response.result().data().maskedDestination()).isEqualTo("s****v@hcglt.nla");
    assertThat(response.result().retryAfterSeconds()).isNull();
    assertThat(json).contains("\"success\":true", "\"nextStep\":\"VERIFY_OTP\"");
    assertThat(json)
        .contains(
            "\"maskedDestination\":\"s****v@hcglt.nla\"",
            "\"expiresInSeconds\":287",
            "\"resendCooldownSeconds\":47",
            "\"remainingResendAttempts\":5");
    assertThat(json).doesNotContain("registration-otp-verification", "pageRef");
    assertThat(json)
        .doesNotContain(
            "csrfToken",
            "auth_flow_sid",
            "otpChallengeId",
            "activeOtpChallengeId",
            "123456",
            "member@example.com",
            "3024102",
            "04/04/1982");
  }

  @Test
  void messageDeliveryFailureMapsToDownstreamError() throws Exception {
    Object response =
        mapper.toMutationResponse(
            new ResendRegistrationOtpResult(
                ResendRegistrationOtpResultStatus.MESSAGE_DELIVERY_FAILED,
                WorkflowNextStep.VERIFY_OTP,
                null,
                0,
                0,
                0,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

    String json = objectMapper.writeValueAsString(response);
    assertThat(json).contains("\"success\":false", "err.registration.message.deliveryFailed");
    assertThat(json).doesNotContain("123456", "member@example.com", "SMS content", "email body");
  }

  @Test
  void duplicateInProgressMapsToWait() throws Exception {
    var response =
        mapper.toMutationResponse(
            new ResendRegistrationOtpResult(
                ResendRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS,
                WorkflowNextStep.WAIT,
                null,
                0,
                0,
                0,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS,
                null));

    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.WAIT);
    assertThat(response.result().data()).isNull();
  }
}
