package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpResendResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowConstants;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowPageRefs;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;

public class RegistrationOtpResendWebMapper {
    private static final String SUCCESS = "SUCCESS";
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String BUSINESS_FAILED = "BUSINESS_FAILED";
    private static final String DOWNSTREAM_ERROR = "DOWNSTREAM_ERROR";
    private static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    private static final String SOURCE_SERVER = "SERVER";

    private final WorkflowMutationResponseMapper workflowMutationResponseMapper;
    public RegistrationOtpResendWebMapper(WorkflowMutationResponseMapper workflowMutationResponseMapper) {
        this(workflowMutationResponseMapper, null);
    }

    public RegistrationOtpResendWebMapper(
            WorkflowMutationResponseMapper workflowMutationResponseMapper,
            Object ignoredMessageResolver) {
        this.workflowMutationResponseMapper = workflowMutationResponseMapper;
    }

    public Object toMutationResponse(ResendRegistrationOtpResult result) {
        return toMutationResponse(result, null);
    }

    public Object toMutationResponse(ResendRegistrationOtpResult result, String language) {
        if (workflowMutationResponseMapper != null) {
            return workflowMutationResponseMapper.toMutationResponse(toWorkflowResult(result, language));
        }
        return fallbackResponse(result);
    }


    private WorkflowResult<RegistrationOtpResendResponse> toWorkflowResult(
            ResendRegistrationOtpResult result,
            String language) {
        return new WorkflowResult<>(
                workflowStatus(result.status()),
                result.nextStep(),
                data(result),
                RegistrationWorkflowPageRefs.forNextStep(result.nextStep()),
                List.of(),
                result.errors().stream()
                        .map(error -> new com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor(
                                error.type(), error.code(), null, error.targets(), error.severity(), error.source()))
                        .toList());
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

    public Object missingAuthFlowCookieResponse() {
        return errorResponse(RegistrationOtpResendErrorKeys.REGISTRATION_AUTH_FLOW_INVALID, VALIDATION_FAILED);
    }

    public Object missingCsrfTokenResponse() {
        return errorResponse(RegistrationOtpResendErrorKeys.REGISTRATION_CSRF_INVALID, VALIDATION_FAILED);
    }

    public Object missingIdempotencyKeyResponse() {
        return errorResponse(com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), VALIDATION_FAILED);
    }

    private Map<String, Object> fallbackResponse(ResendRegistrationOtpResult result) {
        boolean success = result.status() == ResendRegistrationOtpResultStatus.RESENT
                || result.status() == ResendRegistrationOtpResultStatus.COMPLETED_REPLAY
                || result.status() == ResendRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("status", apiStatus(result.status()));
        response.put("result", success || exposeResendFailurePayload(result.status()) ? payloadMap(result) : null);
        response.put("messages", List.of());
        response.put("errors", success ? List.of() : errorMaps(result));
        return response;
    }

    private Map<String, Object> payloadMap(ResendRegistrationOtpResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nextStep", result.nextStep());
        payload.put("data", data(result));
        Map<String, Object> pageRef = new LinkedHashMap<>();
        pageRef.put("pageId", RegistrationWorkflowConstants.PAGE_REGISTRATION_OTP_VERIFICATION);
        pageRef.put("variant", null);
        payload.put("pageRef", result.nextStep() == WorkflowNextStep.VERIFY_OTP ? pageRef : null);
        return payload;
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

    private String apiStatus(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case RESENT, COMPLETED_REPLAY, DUPLICATE_IN_PROGRESS -> SUCCESS;
            case MESSAGE_DELIVERY_FAILED -> DOWNSTREAM_ERROR;
            case SYSTEM_FAILED -> SYSTEM_ERROR;
            case COOLDOWN_ACTIVE, SEND_RATE_LIMITED, MAX_RESEND_ATTEMPTS_REACHED -> BUSINESS_FAILED;
            default -> VALIDATION_FAILED;
        };
    }

    private List<Map<String, Object>> errorMaps(ResendRegistrationOtpResult result) {
        if (!result.errors().isEmpty()) {
            return result.errors().stream().map(error -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("code", error.code());
                map.put("target", error.targets() == null || error.targets().isEmpty() ? "PAGE" : error.targets().get(0));
                return map;
            }).toList();
        }
        return List.of(errorMap(defaultErrorCode(result.status())));
    }

    private Map<String, Object> errorMap(String code) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("target", "PAGE");
        return map;
    }

    private Object errorResponse(String code, String status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", status);
        response.put("result", null);
        response.put("messages", List.of());
        response.put("errors", List.of(errorMap(code)));
        return response;
    }

    private String defaultErrorCode(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case INVALID_AUTH_FLOW -> RegistrationOtpResendErrorKeys.REGISTRATION_AUTH_FLOW_INVALID;
            case CSRF_INVALID -> RegistrationOtpResendErrorKeys.REGISTRATION_CSRF_INVALID;
            case IDEMPOTENCY_KEY_REUSED -> RegistrationOtpResendErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED;
            case COOLDOWN_ACTIVE -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_COOLDOWN_ACTIVE;
            case SEND_RATE_LIMITED -> "err.registration.otp.sendRateLimited";
            case MAX_RESEND_ATTEMPTS_REACHED -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS_REACHED;
            case MESSAGE_DELIVERY_FAILED -> RegistrationOtpResendErrorKeys.REGISTRATION_MESSAGE_DELIVERY_FAILED;
            default -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_RESEND_FAILED;
        };
    }
}
