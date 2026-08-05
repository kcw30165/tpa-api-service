package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpResendWebMapperTest {
    private final ForgotPasswordOtpResendWebMapper mapper = new ForgotPasswordOtpResendWebMapper();
        private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsSuccessReplayAndCooldownToApprovedWorkflowShape() {
        var resent = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.RESENT,
                "m****r@example.com",
                300,
                60,
                4,
                null));
        var replay = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.COMPLETED_REPLAY,
                "m****r@example.com",
                300,
                null,
                4,
                60));
        var cooldown = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE,
                "m****r@example.com",
                300,
                null,
                4,
                25));

        assertThat(resent.status()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(replay.status()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(cooldown.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
        assertThat(resent.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(replay.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(cooldown.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(cooldown.errors()).singleElement().extracting(error -> error.code())
                .isEqualTo("err.forgot-password.otp.resend.cooldown");
    }

    @Test
    void mapsPreviousFailedToBusinessRejectedRetryableOutcome() {
        var previousFailed = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.PREVIOUS_FAILED,
                "m****r@example.com",
                null,
                null,
                null,
                null));

        assertThat(previousFailed.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
        assertThat(previousFailed.result().nextStep()).isEqualTo(WorkflowNextStep.RESEND_OTP);
        assertThat(previousFailed.errors()).singleElement().extracting(error -> error.code())
                .isEqualTo("err.forgot-password.otp.resend.previousFailed");
        assertThat(previousFailed.toString()).doesNotContain("err.system.unavailable");
    }

    @Test
    void mapsLimitReuseAndSystemStatusesWithoutLeakingInternalData() {
        var limitReached = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.RESEND_LIMIT_REACHED,
                "m****r@example.com",
                null,
                null,
                0,
                null));
        var reused = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED,
                null,
                null,
                null,
                null,
                null));
        var unavailable = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME,
                "m****r@example.com",
                null,
                null,
                null,
                null));

        assertThat(limitReached.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
        assertThat(limitReached.errors()).singleElement().extracting(error -> error.code())
                .isEqualTo("err.forgot-password.otp.resend.limitReached");
        assertThat(reused.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(reused.errors()).singleElement().extracting(error -> error.code())
                .isEqualTo("err.workflow.idempotency.keyReused");
        assertThat(unavailable.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
        assertThat(unavailable.errors()).singleElement().extracting(error -> error.code())
                .isEqualTo("err.system.unavailable");
        assertThat(unavailable.toString()).doesNotContain("m****r@example.com");
    }

        @Test
        void cooldownResponseExposesRegistrationStyleRetryAndRemainingAttemptFields() throws Exception {
                var response = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                                ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE,
                                "m****r@example.com",
                                null,
                                null,
                                4,
                                23));

                var json = objectMapper.valueToTree(response);

                assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
                assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
                assertThat(json.at("/result/data/maskedDestination").asText()).isEqualTo("m****r@example.com");
                assertThat(json.at("/result/data/expiresInSeconds").isNull()).isTrue();
                assertThat(json.at("/result/data/resendCooldownSeconds").isNull()).isTrue();
                assertThat(json.at("/result/data/remainingResendAttempts").asInt()).isEqualTo(4);
                assertThat(json.at("/result/data/retryAfterSeconds").asInt()).isEqualTo(23);
        }
}