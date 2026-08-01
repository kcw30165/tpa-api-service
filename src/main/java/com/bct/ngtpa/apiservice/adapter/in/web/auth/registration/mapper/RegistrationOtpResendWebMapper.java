package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpResendResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationWorkflowResultResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import java.util.List;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;

public class RegistrationOtpResendWebMapper {
    private static final String SOURCE_SERVER = "SERVER";

    private final RegistrationWorkflowMutationResponseMapper workflowMutationResponseMapper;

    public RegistrationOtpResendWebMapper(
            WorkflowMutationResponseMapper workflowMutationResponseMapper) {
        this(workflowMutationResponseMapper, null);
    }

    public RegistrationOtpResendWebMapper(
            WorkflowMutationResponseMapper workflowMutationResponseMapper,
            Object ignoredMessageResolver) {
        this.workflowMutationResponseMapper = new RegistrationWorkflowMutationResponseMapper(
                workflowMutationResponseMapper == null
                        ? new WorkflowMutationResponseMapper()
                        : workflowMutationResponseMapper);
    }

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpResendResponse>> toMutationResponse(
            ResendRegistrationOtpResult result) {
        return toMutationResponse(result, null);
    }

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpResendResponse>> toMutationResponse(
            ResendRegistrationOtpResult result, String language) {
        var response = workflowMutationResponseMapper.toMutationResponse(toWorkflowResult(result, language));
        if (result.status() == ResendRegistrationOtpResultStatus.MESSAGE_DELIVERY_FAILED) {
            return new MutationResponse<>(
                    false,
                    ApiStatus.SYSTEM_ERROR,
                    response.result(),
                    response.messages(),
                    response.errors());
        }
        return response;
    }

    private WorkflowResult<RegistrationOtpResendResponse> toWorkflowResult(
            ResendRegistrationOtpResult result,
            String language) {
        return new WorkflowResult<>(
                workflowStatus(result.status()),
                result.nextStep(),
                data(result),
                List.of(),
                errors(result));
    }

    private WorkflowResultStatus workflowStatus(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case RESENT, COMPLETED_REPLAY -> WorkflowResultStatus.SUCCESS;
            case DUPLICATE_IN_PROGRESS -> WorkflowResultStatus.WAITING;
            case COOLDOWN_ACTIVE, SEND_RATE_LIMITED, MAX_RESEND_ATTEMPTS_REACHED -> WorkflowResultStatus.BUSINESS_FAILED;
            case MESSAGE_DELIVERY_FAILED, SYSTEM_FAILED -> WorkflowResultStatus.SYSTEM_FAILED;
            default -> WorkflowResultStatus.VALIDATION_FAILED;
        };
    }

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpResendResponse>> missingAuthFlowCookieResponse() {
        return errorResponse(RegistrationOtpResendErrorKeys.REGISTRATION_AUTH_FLOW_INVALID);
    }

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpResendResponse>> missingCsrfTokenResponse() {
        return errorResponse(RegistrationOtpResendErrorKeys.REGISTRATION_CSRF_INVALID);
    }

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpResendResponse>> missingIdempotencyKeyResponse() {
        return errorResponse(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value());
    }

    private RegistrationOtpResendResponse data(ResendRegistrationOtpResult result) {
        if (result.status() != ResendRegistrationOtpResultStatus.RESENT
                && result.status() != ResendRegistrationOtpResultStatus.COMPLETED_REPLAY
                && !exposeResendFailurePayload(result.status())) {
            return null;
        }
        return new RegistrationOtpResendResponse(
                result.maskedDestination(),
                result.expiresInSeconds() > 0 ? result.expiresInSeconds() : null,
                result.resendCooldownSeconds() > 0 ? result.resendCooldownSeconds() : null,
                result.remainingResendAttempts(),
                result.retryAfterSeconds());
    }

    private boolean exposeResendFailurePayload(ResendRegistrationOtpResultStatus status) {
        return status == ResendRegistrationOtpResultStatus.COOLDOWN_ACTIVE
                || status == ResendRegistrationOtpResultStatus.SEND_RATE_LIMITED
                || status == ResendRegistrationOtpResultStatus.MAX_RESEND_ATTEMPTS_REACHED;
    }

    private List<WorkflowErrorDescriptor> errors(ResendRegistrationOtpResult result) {
        if (result.errors() != null && !result.errors().isEmpty()) {
            return result.errors().stream()
                    .map(error -> new WorkflowErrorDescriptor(
                            error.type(),
                            error.code(),
                            null,
                            error.targets(),
                            error.severity(),
                            error.source()))
                    .toList();
        }
        if (workflowStatus(result.status()) == WorkflowResultStatus.SUCCESS
                || workflowStatus(result.status()) == WorkflowResultStatus.WAITING) {
            return List.of();
        }
        return List.of(new WorkflowErrorDescriptor(
                defaultErrorType(result.status()),
                defaultErrorCode(result.status()),
                null,
                List.of("PAGE"),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER));
    }

    private WorkflowErrorType defaultErrorType(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case MESSAGE_DELIVERY_FAILED -> WorkflowErrorType.DOWNSTREAM_SYSTEM;
            case SYSTEM_FAILED -> WorkflowErrorType.SYSTEM;
            default -> WorkflowErrorType.BUSINESS;
        };
    }

    private String defaultErrorCode(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case COOLDOWN_ACTIVE -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_COOLDOWN_ACTIVE;
            case SEND_RATE_LIMITED -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_SEND_RATE_LIMITED;
            case MAX_RESEND_ATTEMPTS_REACHED -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS_REACHED;
            case MESSAGE_DELIVERY_FAILED, SYSTEM_FAILED ->
                RegistrationOtpResendErrorKeys.REGISTRATION_MESSAGE_DELIVERY_FAILED;
            default -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_RESEND_FAILED;
        };
    }

    private MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpResendResponse>> errorResponse(
            String code) {
        var error = new WorkflowErrorDescriptor(
                WorkflowErrorType.FORM,
                code,
                null,
                List.of("PAGE"),
                WorkflowErrorSeverity.ERROR,
                SOURCE_SERVER);
        return workflowMutationResponseMapper.toMutationResponse(new WorkflowResult<>(
                WorkflowResultStatus.VALIDATION_FAILED,
                WorkflowNextStep.VERIFY_OTP,
                null,
                List.of(),
                List.of(error)));
    }
}
