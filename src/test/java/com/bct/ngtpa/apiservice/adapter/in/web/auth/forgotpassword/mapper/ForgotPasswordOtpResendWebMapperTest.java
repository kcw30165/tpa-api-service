package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpResendWebMapperTest {
    private final ForgotPasswordOtpResendWebMapper mapper = new ForgotPasswordOtpResendWebMapper();

    @Test
    void mapsSuccessReplayAndCooldownToApprovedWorkflowShape() {
        var resent = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.RESENT,
                "m****r@example.com",
                300,
                60));
        var replay = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.COMPLETED_REPLAY,
                "m****r@example.com",
                300,
                60));
        var cooldown = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE,
                "m****r@example.com",
                300,
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
    void mapsLimitReuseAndSystemStatusesWithoutLeakingInternalData() {
        var limitReached = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.RESEND_LIMIT_REACHED,
                "m****r@example.com",
                null,
                null));
        var reused = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED,
                null,
                null,
                null));
        var unavailable = mapper.toMutationResponse(new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME,
                "m****r@example.com",
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
}