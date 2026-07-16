package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationSelectedTrustRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResultStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RegistrationUsernameCheckWebMapper {
    private static final String SUCCESS = "SUCCESS";
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String DOWNSTREAM_REJECTION = "DOWNSTREAM_REJECTION";
    private static final String DOWNSTREAM_ERROR = "DOWNSTREAM_ERROR";
    private static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    private static final String TARGET_PAGE = "PAGE";

    private static final String REGISTRATION_AUTH_FLOW_INVALID = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.AUTH_FLOW_INVALID.value();
    private static final String REGISTRATION_CSRF_INVALID = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.CSRF_INVALID.value();
    private static final String REGISTRATION_USERNAME_CHECK_REQUEST_INVALID = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.USERNAME_REQUEST_INVALID.value();

    public CheckRegistrationUsernameCommand toCommand(
            RegistrationUsernameCheckRequest request,
            String authFlowSessionId,
            String csrfToken,
            String requestId,
            String acceptLanguage,
            String origin,
            String referer) {
        RegistrationSelectedTrust selectedTrust = null;
        if (request != null && request.selectedTrust() != null) {
            RegistrationSelectedTrustRequest selectedTrustRequest = request.selectedTrust();
            selectedTrust = new RegistrationSelectedTrust(
                    selectedTrustRequest.trustCode(),
                    selectedTrustRequest.accountEnv());
        }
        return new CheckRegistrationUsernameCommand(
                new AuthFlowSessionId(trim(authFlowSessionId)),
                trim(csrfToken),
                selectedTrust,
                request == null ? null : request.username(),
                trimToNull(origin),
                trimToNull(referer),
                trimToNull(acceptLanguage),
                trimToNull(requestId));
    }

    public Object toMutationResponse(CheckRegistrationUsernameResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        boolean success = result.status() == CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE;
        response.put("success", success);
        response.put("status", apiStatus(result.status()));
        response.put("result", resultPayload(nextStep(result)));
        response.put("messages", result.messages() == null ? List.of() : result.messages());
        response.put("errors", success ? List.of() : errorMaps(result));
        return response;
    }

    public Object missingAuthFlowCookieResponse() {
        return errorResponse(REGISTRATION_AUTH_FLOW_INVALID);
    }

    public Object missingCsrfTokenResponse() {
        return errorResponse(REGISTRATION_CSRF_INVALID);
    }

    public Object missingRequestBodyResponse() {
        return errorResponse(REGISTRATION_USERNAME_CHECK_REQUEST_INVALID);
    }

    private Object errorResponse(String code) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", VALIDATION_FAILED);
        response.put("result", resultPayload(WorkflowNextStep.USERNAME_CHECK));
        response.put("messages", List.of());
        response.put("errors", List.of(errorMap(code, TARGET_PAGE)));
        return response;
    }

    private Map<String, Object> resultPayload(WorkflowNextStep nextStep) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nextStep", nextStep);
        result.put("data", null);
        result.put("pageRef", pageRef(nextStep));
        return result;
    }

    private Map<String, Object> pageRef(WorkflowNextStep nextStep) {
        Map<String, Object> pageRef = new LinkedHashMap<>();
        pageRef.put("pageId", switch (nextStep) {
            case PASSWORD_SETUP -> "registration-password-setup";
            case USERNAME_CHECK -> "registration-username-check";
            default -> null;
        });
        pageRef.put("variant", null);
        return pageRef;
    }

    private String apiStatus(CheckRegistrationUsernameResultStatus status) {
        return switch (status) {
            case USERNAME_AVAILABLE -> SUCCESS;
            case DOWNSTREAM_REJECTED -> DOWNSTREAM_REJECTION;
            case DOWNSTREAM_FAILED -> DOWNSTREAM_ERROR;
            case SYSTEM_FAILED -> SYSTEM_ERROR;
            default -> VALIDATION_FAILED;
        };
    }

    private WorkflowNextStep nextStep(CheckRegistrationUsernameResult result) {
        return result.nextStep() == null ? WorkflowNextStep.USERNAME_CHECK : result.nextStep();
    }

    private List<Map<String, Object>> errorMaps(CheckRegistrationUsernameResult result) {
        if (result.errors() == null || result.errors().isEmpty()) {
            return List.of(errorMap(defaultErrorCode(result.status()), TARGET_PAGE));
        }
        return result.errors().stream()
                .map(error -> errorMap(error.code(), firstTarget(error)))
                .toList();
    }

    private String firstTarget(WorkflowErrorDescriptor error) {
        if (error.targets() == null || error.targets().isEmpty()) {
            return TARGET_PAGE;
        }
        String target = error.targets().get(0);
        return target == null || target.isBlank() ? TARGET_PAGE : target;
    }

    private Map<String, Object> errorMap(String code, String target) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("target", target == null || target.isBlank() ? TARGET_PAGE : target);
        return map;
    }

    private String defaultErrorCode(CheckRegistrationUsernameResultStatus status) {
        return switch (status) {
            case INVALID_AUTH_FLOW -> REGISTRATION_AUTH_FLOW_INVALID;
            case CSRF_INVALID -> REGISTRATION_CSRF_INVALID;
            case DOWNSTREAM_FAILED -> com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value();
            case DOWNSTREAM_REJECTED -> "downstream.generic.businessRejected";
            case SYSTEM_FAILED -> com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value();
            case INVALID_STATE -> com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.STATE_INVALID.value();
            default -> REGISTRATION_USERNAME_CHECK_REQUEST_INVALID;
        };
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
