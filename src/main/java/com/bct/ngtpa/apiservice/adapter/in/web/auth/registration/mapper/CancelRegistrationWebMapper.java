package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.CancelRegistrationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationCancellationPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResultStatus;
import java.util.List;
import java.util.Objects;

public final class CancelRegistrationWebMapper {
    private static final String SOURCE_SERVER = "SERVER";
    private final RegistrationCancellationPageMapper pageMapper;
    public CancelRegistrationWebMapper(
            RegistrationCancellationPageMapper pageMapper) {
        this(pageMapper, null);
    }

    public CancelRegistrationWebMapper(
            RegistrationCancellationPageMapper pageMapper,
            Object ignoredMessages) {
        this.pageMapper = Objects.requireNonNull(pageMapper, "pageMapper must not be null");
    }

    public CancelRegistrationCommand toCommand(
            String sessionId,
            String csrfToken,
            String origin,
            String referer,
            String requestId) {
        return new CancelRegistrationCommand(
                new AuthFlowSessionId(sessionId.trim()),
                trim(csrfToken),
                trim(origin),
                trim(referer),
                trim(requestId));
    }

    public MutationResponse<CancelRegistrationResponse> toResponse(
            CancelRegistrationResult result,
            String language) {
        Objects.requireNonNull(result, "result must not be null");
        if (result.successfulCleanup()) {
            return MutationResponse.success(
                    ApiStatus.SUCCESS,
                    new CancelRegistrationResponse(true, pageMapper.map(language)),
                    List.of());
        }
        ApiStatus status = result.status() == CancelRegistrationResultStatus.CSRF_INVALID
                || result.status() == CancelRegistrationResultStatus.INVALID_AUTH_FLOW
                ? ApiStatus.VALIDATION_FAILED
                : ApiStatus.SYSTEM_ERROR;
        List<ApiError> errors = result.errors().stream()
                .map(error -> new ApiError(
                        error.type().name(),
                        error.code(),
                        null,
                        error.targets(),
                        error.severity().name(),
                        error.source()))
                .toList();
        return MutationResponse.failure(status, null, errors);
    }

    public MutationResponse<CancelRegistrationResponse> missingSession(String language) {
        return MutationResponse.success(
                ApiStatus.SUCCESS,
                new CancelRegistrationResponse(true, pageMapper.map(language)),
                List.of());
    }

    public MutationResponse<CancelRegistrationResponse> missingCsrf(String language) {
        String code = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.CSRF_INVALID.value();
        ApiError error = ApiError.business(
                code,
                null,
                List.of("PAGE"),
                SOURCE_SERVER);
        return MutationResponse.failure(ApiStatus.VALIDATION_FAILED, null, List.of(error));
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
