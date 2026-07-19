package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUnregisteredTrustResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowConstants;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RegistrationOtpVerifyWebMapper {
    private static final String SUCCESS = "SUCCESS";
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String BUSINESS_REJECTED = "BUSINESS_REJECTED";
    private static final String DOWNSTREAM_ERROR = "DOWNSTREAM_ERROR";
    private static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    private final Object workflowMutationResponseMapper;

    public RegistrationOtpVerifyWebMapper(Object workflowMutationResponseMapper) {
        this.workflowMutationResponseMapper = workflowMutationResponseMapper;
    }

    public Object toMutationResponse(VerifyRegistrationOtpResult result) {
        return fallbackResponse(result);
    }

    public Object missingAuthFlowCookieResponse() { return errorResponse(RegistrationOtpVerifyErrorKeys.REGISTRATION_AUTH_FLOW_INVALID, VALIDATION_FAILED); }
    public Object missingCsrfTokenResponse() { return errorResponse(RegistrationOtpVerifyErrorKeys.REGISTRATION_CSRF_INVALID, VALIDATION_FAILED); }
    public Object missingIdempotencyKeyResponse() { return errorResponse(com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), VALIDATION_FAILED); }
    public Object missingCodeResponse() { return errorResponse(RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID, VALIDATION_FAILED); }

    private Map<String, Object> fallbackResponse(VerifyRegistrationOtpResult result) {
        boolean success = result.status() == VerifyRegistrationOtpResultStatus.VERIFIED
                || result.status() == VerifyRegistrationOtpResultStatus.COMPLETED_REPLAY
                || result.status() == VerifyRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("status", apiStatus(result.status()));
        response.put("result", success || exposeOtpFailurePayload(result.status()) ? payloadMap(result) : null);
        response.put("messages", List.of());
        response.put("errors", success ? List.of() : errorMaps(result));
        return response;
    }

    private Map<String, Object> payloadMap(VerifyRegistrationOtpResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nextStep", result.nextStep());
        payload.put("data", data(result));
        payload.put("pageRef", pageRef(result.nextStep()));
        return payload;
    }

    private RegistrationOtpVerifyResponse data(VerifyRegistrationOtpResult result) {
        if (result.status() == VerifyRegistrationOtpResultStatus.VERIFIED) {
            return new RegistrationOtpVerifyResponse(
                    result.unregisteredTrusts().stream()
                            .map(trust -> new RegistrationUnregisteredTrustResponse(
                                    trust.accountEnv(), trust.trustCode()))
                            .toList(),
                    null);
        }
        if (exposeOtpFailurePayload(result.status())) {
            return new RegistrationOtpVerifyResponse(List.of(), result.remainingVerifyAttempts());
        }
        return null;
    }

    private boolean exposeOtpFailurePayload(VerifyRegistrationOtpResultStatus status) {
        return status == VerifyRegistrationOtpResultStatus.OTP_INVALID
                || status == VerifyRegistrationOtpResultStatus.OTP_EXPIRED
                || status == VerifyRegistrationOtpResultStatus.OTP_LOCKED;
    }

    private Map<String, Object> pageRef(WorkflowNextStep nextStep) {
        String pageId = switch (nextStep) {
            case USERNAME_CHECK -> RegistrationWorkflowConstants.PAGE_REGISTRATION_USERNAME_CHECK;
            case SELECT_TRUST -> RegistrationWorkflowConstants.PAGE_REGISTRATION_TRUST_SELECTION;
            case VERIFY_OTP -> RegistrationWorkflowConstants.PAGE_REGISTRATION_OTP_VERIFICATION;
            default -> null;
        };
        if (pageId == null) { return null; }
        Map<String, Object> pageRef = new LinkedHashMap<>();
        pageRef.put("pageId", pageId);
        pageRef.put("variant", null);
        return pageRef;
    }

    private String apiStatus(VerifyRegistrationOtpResultStatus status) {
        return switch (status) {
            case VERIFIED, COMPLETED_REPLAY, DUPLICATE_IN_PROGRESS -> SUCCESS;
            case OTP_EXPIRED, OTP_LOCKED, NO_ELIGIBLE_TRUSTS -> BUSINESS_REJECTED;
            case TRUST_LOOKUP_FAILED -> DOWNSTREAM_ERROR;
            case SYSTEM_FAILED -> SYSTEM_ERROR;
            default -> VALIDATION_FAILED;
        };
    }

    private List<Map<String, Object>> errorMaps(VerifyRegistrationOtpResult result) {
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

    private Object errorResponse(String code, String status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", status);
        response.put("result", null);
        response.put("messages", List.of());
        response.put("errors", List.of(errorMap(code)));
        return response;
    }

    private Map<String, Object> errorMap(String code) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("target", "PAGE");
        return map;
    }

    private String defaultErrorCode(VerifyRegistrationOtpResultStatus status) {
        return switch (status) {
            case INVALID_AUTH_FLOW -> RegistrationOtpVerifyErrorKeys.REGISTRATION_AUTH_FLOW_INVALID;
            case CSRF_INVALID -> RegistrationOtpVerifyErrorKeys.REGISTRATION_CSRF_INVALID;
            case IDEMPOTENCY_KEY_REUSED -> RegistrationOtpVerifyErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED;
            case OTP_EXPIRED -> RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_EXPIRED;
            case OTP_LOCKED -> RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS_REACHED;
            case NO_ELIGIBLE_TRUSTS -> RegistrationOtpVerifyErrorKeys.REGISTRATION_NO_ELIGIBLE_TRUSTS;
            case TRUST_LOOKUP_FAILED -> RegistrationOtpVerifyErrorKeys.REGISTRATION_TRUST_LOOKUP_FAILED;
            default -> RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_INVALID;
        };
    }
}
