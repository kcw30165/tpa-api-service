package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;

public final class LoginStartWebMapper {
    public StartLoginCommand toCommand(LoginStartRequest request, String idempotencyKey, String requestId,
            String language) {
        if (request == null)
            throw new IllegalArgumentException("login request required");
        return new StartLoginCommand(request.username(), request.password(), language, requestId, idempotencyKey);
    }

    public MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>> toMutationResponse(
            StartLoginResult result, String language) {
        if (result == null)
            return failure(ApiStatus.SYSTEM_ERROR, LoginErrorKey.SESSION_CREATION_FAILED.value(), "SYSTEM");
        return switch (result.status()) {
            case STARTED, COMPLETED_REPLAY -> MutationResponse.success(
                    ApiStatus.SUCCESS,
                    new LoginWorkflowResultResponse<>(WorkflowNextStep.SELECT_OTP_DESTINATION,
                            new LoginStartDataResponse(result.csrfToken(), result.destinations().stream()
                                    .map(d -> new LoginDestinationResponse(d.destinationId(), d.channel(),
                                            d.maskedDestination()))
                                    .toList(),
                                    result.expiresInSeconds(), List.of(), result.failCount())),
                    List.of());
            case INCORRECT_PASSWORD ->
                failure(ApiStatus.BUSINESS_REJECTED, LoginErrorKey.INCORRECT_PASSWORD.value(), "DOWNSTREAM");
            case INCORRECT_USERNAME ->
                failure(ApiStatus.BUSINESS_REJECTED, LoginErrorKey.INCORRECT_USERNAME.value(), "DOWNSTREAM");
            case INVALID_ACCOUNT ->
                failure(ApiStatus.BUSINESS_REJECTED, LoginErrorKey.INVALID_ACCOUNT.value(), "DOWNSTREAM");
            case ACCOUNT_LOCKED ->
                failure(ApiStatus.BUSINESS_REJECTED, LoginErrorKey.ACCOUNT_LOCKED.value(), "DOWNSTREAM");
            case DUPLICATE_IN_PROGRESS ->
                failure(ApiStatus.BUSINESS_REJECTED, SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(), "BFF");
            case KEY_REUSED ->
                failure(ApiStatus.VALIDATION_FAILED, SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(), "BFF");
            case FAILED_EXISTING ->
                failure(ApiStatus.BUSINESS_REJECTED, LoginErrorKey.CREDENTIALS_INVALID.value(), "BFF");
            case UNKNOWN_OUTCOME ->
                failure(ApiStatus.SYSTEM_ERROR, LoginErrorKey.SESSION_CREATION_FAILED.value(), "BFF");
            case UNAVAILABLE ->
                failure(ApiStatus.DOWNSTREAM_ERROR, LoginErrorKey.CREDENTIALS_UNAVAILABLE.value(), "DOWNSTREAM");
        };
    }

    public MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>> missingRequestId() {
        return MutationResponse.failure(
                ApiStatus.VALIDATION_FAILED,
                List.of(ApiError.form(
                        ErrorCodes.REQUEST_ID_MISSING,
                        null,
                        List.of("X-Request-Id"),
                        "BFF")));
    }

    public MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>> systemUnavailable() {
        return failure(
                ApiStatus.SYSTEM_ERROR,
                SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
                "BFF");
    }

    public MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>> missingIdempotencyKey() {
        return failure(ApiStatus.VALIDATION_FAILED, SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), "BFF");
    }

    public MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>> invalidRequest() {
        return failure(ApiStatus.VALIDATION_FAILED, LoginErrorKey.REQUEST_INVALID.value(), "BFF");
    }

    private MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>> failure(ApiStatus status, String code,
            String source) {
        return MutationResponse.failure(status, List.of(ApiError.form(code, null, List.of(), source)));
    }
}
