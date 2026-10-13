package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordOtpDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordPasswordSetupDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ForgotPasswordHttpResponsesTest {
  @Test
  void shouldReturnTooManyRequestsWhenOtpMaxAttemptsReached() {
    var body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            new ForgotPasswordWorkflowResultResponse<>(
                WorkflowNextStep.RESTART_FLOW, new ForgotPasswordPasswordSetupDataResponse(0)),
            List.of(
                ApiError.form(
                    ForgotPasswordErrorKey.OTP_MAX_ATTEMPTS_REACHED.value(),
                    null,
                    List.of(),
                    "SERVER")));

    ResponseEntity<?> response = ForgotPasswordHttpResponses.toResponse(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void shouldReturnTooManyRequestsWhenOtpResendCooldownIsActive() {
    var body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            new ForgotPasswordWorkflowResultResponse<>(
                WorkflowNextStep.VERIFY_OTP,
                new ForgotPasswordOtpDataResponse("j***@example.com", 240, null, 3, 25)),
            List.of(
                ApiError.form(
                    ForgotPasswordErrorKey.OTP_RESEND_COOLDOWN.value(), null, List.of(), "BFF")));

    ResponseEntity<?> response = ForgotPasswordHttpResponses.toResponse(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void shouldKeepUnprocessableEntityWhenOtpResendLimitIsReached() {
    var body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            new ForgotPasswordWorkflowResultResponse<>(
                WorkflowNextStep.VERIFY_OTP,
                new ForgotPasswordOtpDataResponse("j***@example.com", 240, null, 0, null)),
            List.of(
                ApiError.form(
                    ForgotPasswordErrorKey.OTP_RESEND_LIMIT_REACHED.value(),
                    null,
                    List.of(),
                    "BFF")));

    ResponseEntity<?> response = ForgotPasswordHttpResponses.toResponse(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
