package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordPasswordSetupDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpResult;
import java.util.List;

public final class ForgotPasswordOtpVerifyWebMapper {
    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordPasswordSetupDataResponse>>
            toMutationResponse(VerifyForgotPasswordOtpResult result) {
        if (result == null) {
            return systemUnavailable();
        }
        return switch (result.status()) {
            case VERIFIED, COMPLETED_REPLAY -> MutationResponse.success(
                    ApiStatus.SUCCESS,
                    new ForgotPasswordWorkflowResultResponse<>(
                            WorkflowNextStep.PASSWORD_SETUP,
                            new ForgotPasswordPasswordSetupDataResponse(result.remainingVerifyAttempts())),
                    List.of());
            case OTP_INVALID -> MutationResponse.failure(
                    ApiStatus.VALIDATION_FAILED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.VERIFY_OTP,
                            new ForgotPasswordPasswordSetupDataResponse(result.remainingVerifyAttempts())),
                    List.of(ApiError.form(ForgotPasswordErrorKey.OTP_INVALID.value(), null, List.of("code"), "BFF")));
            case OTP_EXPIRED -> MutationResponse.failure(
                    ApiStatus.BUSINESS_REJECTED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.RESEND_OTP,
                            new ForgotPasswordPasswordSetupDataResponse(result.remainingVerifyAttempts())),
                    List.of(ApiError.form(ForgotPasswordErrorKey.OTP_EXPIRED.value(), null, List.of(), "SERVER")));
            case OTP_LOCKED -> MutationResponse.failure(
                    ApiStatus.BUSINESS_REJECTED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.RESTART_FLOW,
                            new ForgotPasswordPasswordSetupDataResponse(0)),
                    List.of(ApiError.form(ForgotPasswordErrorKey.OTP_MAX_ATTEMPTS_REACHED.value(), null, List.of(), "SERVER")));
            case AUTH_FLOW_INVALID -> MutationResponse.failure(
                    ApiStatus.VALIDATION_FAILED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                    List.of(ApiError.form(ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value(), null, List.of(), "BFF")));
            case CSRF_INVALID -> MutationResponse.failure(
                    ApiStatus.VALIDATION_FAILED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                    List.of(ApiError.form(ForgotPasswordErrorKey.CSRF_INVALID.value(), null, List.of(), "BFF")));
            case DUPLICATE_IN_PROGRESS -> MutationResponse.failure(
                    ApiStatus.BUSINESS_REJECTED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.WAIT, null),
                    List.of(ApiError.form(SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(), null,
                            List.of(), "BFF")));
            case IDEMPOTENCY_KEY_REUSED -> MutationResponse.failure(
                    ApiStatus.VALIDATION_FAILED,
                    new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                    List.of(ApiError.form(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(), null,
                            List.of("Idempotency-Key"), "BFF")));
            case SYSTEM_FAILED -> systemUnavailable();
        };
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordPasswordSetupDataResponse>> missingAuthFlowCookie() {
        return MutationResponse.failure(
                ApiStatus.VALIDATION_FAILED,
                new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                                List.of(ApiError.form(ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value(), null, List.of(), "BFF")));
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordPasswordSetupDataResponse>> missingCsrfToken() {
        return MutationResponse.failure(
                ApiStatus.VALIDATION_FAILED,
                new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                                List.of(ApiError.form(ForgotPasswordErrorKey.CSRF_INVALID.value(), null, List.of(), "BFF")));
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordPasswordSetupDataResponse>> missingIdempotencyKey() {
        return MutationResponse.failure(
                ApiStatus.VALIDATION_FAILED,
                new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                List.of(ApiError.form(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), null,
                        List.of("Idempotency-Key"), "BFF")));
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordPasswordSetupDataResponse>> missingCode() {
        return MutationResponse.failure(
                ApiStatus.VALIDATION_FAILED,
                new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.VERIFY_OTP, null),
                List.of(ApiError.form(ForgotPasswordErrorKey.OTP_INVALID.value(), null, List.of("code"), "BFF")));
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordPasswordSetupDataResponse>> systemUnavailable() {
        return MutationResponse.failure(
                ApiStatus.SYSTEM_ERROR,
                new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.NONE, null),
                                List.of(ApiError.form(ForgotPasswordErrorKey.SYSTEM_UNAVAILABLE.value(), null, List.of(), "BFF")));
    }
}