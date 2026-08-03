package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowPublicApiStatusNormalizer;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordError;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class ForgotPasswordStartWebMapper {
    private final Duration idempotencyTtl;

    public ForgotPasswordStartWebMapper(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }

    public StartForgotPasswordCommand toCommand(
            ForgotPasswordStartRequest request,
            String idempotencyKey,
            String requestId,
            String language,
            String authFlowSessionId) {
        if (request == null) {
            throw new IllegalArgumentException("forgot-password request required");
        }
        return new StartForgotPasswordCommand(
                new ForgotPasswordIdempotencyKey(idempotencyKey),
                request.username(),
                authFlowSessionId,
                language,
                requestId,
                idempotencyTtl);
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordStartDataResponse>> toMutationResponse(
            StartForgotPasswordResult result,
            String language) {
        if (result == null) {
            return systemUnavailable();
        }
        return switch (result.status()) {
            case STARTED, COMPLETED_REPLAY -> MutationResponse.success(
                    ApiStatus.SUCCESS,
                    new ForgotPasswordWorkflowResultResponse<>(
                            WorkflowNextStep.ENTER_ID,
                            new ForgotPasswordStartDataResponse(result.csrfToken())),
                    List.of());
            case USERNAME_NOT_FOUND ->
                failure(ApiStatus.BUSINESS_REJECTED, WorkflowNextStep.ENTER_USERNAME, result.errors());
            case DUPLICATE_IN_PROGRESS -> failure(ApiStatus.BUSINESS_REJECTED, WorkflowNextStep.WAIT,
                    List.of(new StartForgotPasswordError(SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(), null,
                            null)));
            case IDEMPOTENCY_KEY_REUSED -> failure(ApiStatus.VALIDATION_FAILED, WorkflowNextStep.NONE,
                    List.of(new StartForgotPasswordError(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                            "Idempotency-Key", null)));
            case FAILED_EXISTING, UNKNOWN_OUTCOME,
                    SYSTEM_FAILED ->
                failure(ApiStatus.SYSTEM_ERROR, WorkflowNextStep.NONE,
                        result.errors().isEmpty()
                                ? List.of(new StartForgotPasswordError(
                                        SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(), null, null))
                                : result.errors());
        };
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordStartDataResponse>> missingIdempotencyKey() {
        return failure(ApiStatus.VALIDATION_FAILED, WorkflowNextStep.NONE,
                List.of(new StartForgotPasswordError(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(),
                        "Idempotency-Key", null)));
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordStartDataResponse>> invalidRequest() {
        return failure(ApiStatus.VALIDATION_FAILED, WorkflowNextStep.NONE,
                                List.of(new StartForgotPasswordError("err.forgotpassword.start.requestInvalid", null, null)));
    }

    public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordStartDataResponse>> systemUnavailable() {
        return failure(ApiStatus.SYSTEM_ERROR, WorkflowNextStep.NONE,
                                List.of(new StartForgotPasswordError(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(), null, null)));
    }

    private MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordStartDataResponse>> failure(
            ApiStatus status,
            WorkflowNextStep nextStep,
            List<StartForgotPasswordError> errors) {
        ApiStatus normalized = WorkflowPublicApiStatusNormalizer.normalize(status);
        return MutationResponse.failure(
                normalized,
                new ForgotPasswordWorkflowResultResponse<>(nextStep, null),
                errors == null ? List.of()
                        : errors.stream()
                                .map(error -> ApiError.form(error.code(), null,
                                        sanitizeTargets(error),
                                        "BFF"))
                                .toList());
    }

    private List<String> sanitizeTargets(StartForgotPasswordError error) {
                if (error == null || error.target() == null) {
            return List.of();
        }
                return List.of(error.target()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(target -> !target.isEmpty())
                .filter(target -> !target.equalsIgnoreCase("PAGE"))
                .toList();
    }
}