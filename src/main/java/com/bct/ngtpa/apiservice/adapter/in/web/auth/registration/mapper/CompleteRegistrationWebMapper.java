package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.CompleteRegistrationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.CompleteRegistrationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationActionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUnregisteredTrustResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationSuccessPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowConstants;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class CompleteRegistrationWebMapper {
    private static final String SOURCE_SERVER = "SERVER";
    private final Duration idempotencyTtl;
    private final RegistrationSuccessPageMapper successPageMapper;
    public CompleteRegistrationWebMapper(
            Duration idempotencyTtl,
            RegistrationSuccessPageMapper successPageMapper) {
        this(idempotencyTtl, successPageMapper, null);
    }

    public CompleteRegistrationWebMapper(
            Duration idempotencyTtl,
            RegistrationSuccessPageMapper successPageMapper,
            Object ignoredMessages) {
        this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl);
        this.successPageMapper = Objects.requireNonNull(successPageMapper);
    }

    public CompleteRegistrationCommand toCommand(
            CompleteRegistrationRequest request,
            String sessionId,
            String csrfToken,
            String idempotencyKey,
            String origin,
            String referer,
            String language,
            String requestId) {
        return new CompleteRegistrationCommand(
                new AuthFlowSessionId(sessionId.trim()),
                csrfToken.trim(),
                new RegistrationIdempotencyKey(idempotencyKey.trim()),
                request == null ? null : request.password(),
                request == null ? null : request.confirmPassword(),
                trim(origin),
                trim(referer),
                trim(language),
                trim(requestId),
                idempotencyTtl);
    }

        public MutationResponse<RegistrationWorkflowResultResponse<CompleteRegistrationResponse>> toResponse(
            CompleteRegistrationResult result,
            String language) {
        boolean success = result.registered()
                || result.status() == CompleteRegistrationResultStatus.COMPLETED_REPLAY;
                RegistrationWorkflowResultResponse<CompleteRegistrationResponse> payload = payload(result, language, success);
        if (success) {
            return MutationResponse.success(ApiStatus.SUCCESS, payload, List.of());
        }
        ApiStatus status = switch (result.status()) {
            case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID, INVALID_STATE,
                    IDEMPOTENCY_KEY_REUSED -> ApiStatus.VALIDATION_FAILED;
            case DOWNSTREAM_REJECTED -> ApiStatus.BUSINESS_REJECTED;
            case DOWNSTREAM_FAILED, CONTINUATION_LOOKUP_FAILED -> ApiStatus.DOWNSTREAM_ERROR;
            default -> ApiStatus.SYSTEM_ERROR;
        };
        List<ApiError> errors = result.errors().stream()
                .map(error -> new ApiError(
                        error.type().name(),
                        error.code(),
                        null,
                        error.targets(),
                        error.severity().name(),
                        error.source()))
                .toList();
        return MutationResponse.failure(status, payload, errors);
    }

    public MutationResponse<RegistrationWorkflowResultResponse<CompleteRegistrationResponse>> missing(String code, String target, String language) {
        ApiError error = ApiError.field(code, null, List.of(target), SOURCE_SERVER);
        return MutationResponse.failure(
                ApiStatus.VALIDATION_FAILED,
                new RegistrationWorkflowResultResponse<>(
                        WorkflowNextStep.PASSWORD_SETUP,
                        new CompleteRegistrationResponse(List.of(), List.of())),
                List.of(error));
    }

    private RegistrationWorkflowResultResponse<CompleteRegistrationResponse> payload(
            CompleteRegistrationResult result,
            String language,
            boolean success) {
        var trusts = result.remainingTrusts().stream()
                .map(trust -> new RegistrationUnregisteredTrustResponse(
                        trust.accountEnv(), trust.trustCode()))
                .toList();
                CompleteRegistrationResponse data = new CompleteRegistrationResponse(trusts, List.of());
        if (!success) {
            return new RegistrationWorkflowResultResponse<>(result.nextStep(), data);
        }
        String variant = trusts.isEmpty()
                ? RegistrationWorkflowConstants.SUCCESS_VARIANT_NO_REMAINING_TRUSTS
                : RegistrationWorkflowConstants.SUCCESS_VARIANT_HAS_REMAINING_TRUSTS;
        return new RegistrationWorkflowResultResponse<>(
                result.nextStep(),
                new CompleteRegistrationResponse(
                        trusts,
                        successPageMapper.map(
                                        RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS,
                                        variant,
                                        language)
                                .actions()
                                .stream()
                                .map(action -> new RegistrationActionResponse(
                                        action.id(),
                                        action.type(),
                                        action.nextStep()))
                                .toList()));
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
