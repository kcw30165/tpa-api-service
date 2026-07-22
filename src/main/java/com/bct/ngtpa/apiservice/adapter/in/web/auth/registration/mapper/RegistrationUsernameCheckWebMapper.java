package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationSelectedTrustRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationWorkflowResultResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResultStatus;
import java.util.List;

public class RegistrationUsernameCheckWebMapper {
    private static final String SUCCESS = "SUCCESS";
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String BUSINESS_REJECTED = "BUSINESS_REJECTED";
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

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>> toMutationResponse(
            CheckRegistrationUsernameResult result) {
        boolean success = result.status() == CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE;
        RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse> payload = payload(nextStep(result));
        if (success) {
            return MutationResponse.success(ApiStatus.SUCCESS, payload, List.of());
        }
        ApiStatus status = switch (result.status()) {
            case DOWNSTREAM_REJECTED -> ApiStatus.BUSINESS_REJECTED;
            case DOWNSTREAM_FAILED -> ApiStatus.DOWNSTREAM_ERROR;
            case SYSTEM_FAILED -> ApiStatus.SYSTEM_ERROR;
            default -> ApiStatus.VALIDATION_FAILED;
        };
        List<ApiError> errors = result.errors() == null || result.errors().isEmpty()
                ? List.of(apiError(defaultErrorCode(result.status()), TARGET_PAGE))
                : result.errors().stream()
                        .map(error -> apiError(error.code(), firstTarget(error)))
                        .toList();
        return MutationResponse.failure(status, payload, errors);
    }

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>> missingAuthFlowCookieResponse() {
        return errorResponse(REGISTRATION_AUTH_FLOW_INVALID);
    }

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>> missingCsrfTokenResponse() {
        return errorResponse(REGISTRATION_CSRF_INVALID);
    }

    public MutationResponse<RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>> missingRequestBodyResponse() {
        return errorResponse(REGISTRATION_USERNAME_CHECK_REQUEST_INVALID);
    }

    private MutationResponse<RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>> errorResponse(String code) {
        RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse> payload = payload(WorkflowNextStep.USERNAME_CHECK);
        return MutationResponse.failure(
                ApiStatus.VALIDATION_FAILED,
                payload,
                List.of(apiError(code, TARGET_PAGE)));
    }

    private RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse> payload(WorkflowNextStep nextStep) {
        return new RegistrationWorkflowResultResponse<>(nextStep, new RegistrationUsernameCheckResponse());
    }

    private WorkflowNextStep nextStep(CheckRegistrationUsernameResult result) {
        return result.nextStep() == null ? WorkflowNextStep.USERNAME_CHECK : result.nextStep();
    }

    private String firstTarget(WorkflowErrorDescriptor error) {
        if (error.targets() == null || error.targets().isEmpty()) {
            return TARGET_PAGE;
        }
        String target = error.targets().get(0);
        return target == null || target.isBlank() ? TARGET_PAGE : target;
    }

    private ApiError apiError(String code, String target) {
        return new ApiError(
                "BUSINESS",
                code,
                null,
                List.of(target == null || target.isBlank() ? TARGET_PAGE : target),
                "ERROR",
                "SERVER");
    }

    private String defaultErrorCode(CheckRegistrationUsernameResultStatus status) {
        return switch (status) {
            case INVALID_AUTH_FLOW -> REGISTRATION_AUTH_FLOW_INVALID;
            case CSRF_INVALID -> REGISTRATION_CSRF_INVALID;
            case DOWNSTREAM_FAILED -> com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value();
            case DOWNSTREAM_REJECTED -> com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.REGISTRATION_USERNAME_CHECKFAILED.value();
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
