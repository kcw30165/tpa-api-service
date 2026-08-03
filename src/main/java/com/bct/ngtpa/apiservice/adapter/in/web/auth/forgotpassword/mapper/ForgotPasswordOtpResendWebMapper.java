package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordOtpDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.*;
import java.util.List;

public final class ForgotPasswordOtpResendWebMapper {
    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>> toMutationResponse(
            ResendForgotPasswordOtpResult result) {
        if (result == null) {
            return systemUnavailable();
        }
        return switch (result.status()) {
            case RESENT, COMPLETED_REPLAY -> MutationResponse.success(ApiStatus.SUCCESS,
                    payload(WorkflowNextStep.VERIFY_OTP, result), List.of());
            case COOLDOWN_ACTIVE -> MutationResponse.failure(ApiStatus.BUSINESS_REJECTED,
                    payload(WorkflowNextStep.VERIFY_OTP, result),
                    List.of(ApiError.form("err.forgot-password.otp.resend.cooldown", null, List.of(),
                            "BFF")));
            case RESEND_LIMIT_REACHED -> MutationResponse.failure(ApiStatus.BUSINESS_REJECTED,
                    payload(WorkflowNextStep.VERIFY_OTP, result),
                    List.of(ApiError.form("err.forgot-password.otp.resend.limitReached", null, List.of(),
                            "BFF")));
            case EXPIRED -> MutationResponse.failure(ApiStatus.BUSINESS_REJECTED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.RESEND_OTP, null),
                    List.of(ApiError.form("err.forgot-password.otp.expired", null, List.of(), "BFF")));
            case IDEMPOTENCY_KEY_REUSED -> MutationResponse.failure(ApiStatus.VALIDATION_FAILED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                    List.of(ApiError.form(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(), null,
                            List.of("Idempotency-Key"), "BFF")));
            case DELIVERY_FAILED, UNKNOWN_OUTCOME, AUTH_FLOW_INVALID, SECURITY_REJECTED, SYSTEM_FAILED ->
                systemUnavailable();
        };
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>> missingIdempotencyKey() {
        return MutationResponse.failure(ApiStatus.VALIDATION_FAILED,
                new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                List.of(ApiError.form(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), null,
                        List.of("Idempotency-Key"), "BFF")));
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>> systemUnavailable() {
        return MutationResponse.failure(ApiStatus.SYSTEM_ERROR,
                new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                                List.of(ApiError.form("err.system.unavailable", null, List.of(), "BFF")));
    }

    private ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse> payload(WorkflowNextStep nextStep,
            ResendForgotPasswordOtpResult result) {
        return new ForgotPasswordWorkflowResultResponse<>(nextStep,
                new ForgotPasswordOtpDataResponse(result.maskedDestination(),
                        result.expiresInSeconds() == null ? 0 : result.expiresInSeconds(),
                        result.resendAvailableInSeconds() == null ? 0 : result.resendAvailableInSeconds()));
    }
}