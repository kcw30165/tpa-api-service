package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.*;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.*;
import java.time.Duration;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class RegistrationStartWebMapper {
    private final Duration defaultIdempotencyTtl;
    private final ErrorMessageResolver errorMessageResolver;

    public RegistrationStartWebMapper(Duration defaultIdempotencyTtl) {
        this(defaultIdempotencyTtl, null);
    }

    public RegistrationStartWebMapper(Duration defaultIdempotencyTtl, ErrorMessageResolver errorMessageResolver) {
        this.defaultIdempotencyTtl = defaultIdempotencyTtl;
        this.errorMessageResolver = errorMessageResolver;
    }

    public StartRegistrationCommand toCommand(RegistrationStartRequest request, String idempotencyKey,
            String correlationId, String acceptLanguage) {
        return new StartRegistrationCommand(new RegistrationIdempotencyKey(idempotencyKey), request.identityType(),
                request.identityValue(), request.dateOfBirth(), request.email(), request.mobile(),
                request.captchaChallengeId(), acceptLanguage, correlationId, defaultIdempotencyTtl);
    }

    public RegistrationStartResponse toResponse(StartRegistrationResult result) {
        if (result == null || result.status() != StartRegistrationResultStatus.STARTED
                && result.status() != StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS
                && result.status() != StartRegistrationResultStatus.COMPLETED_REPLAY)
            return null;
        return new RegistrationStartResponse(result.nextStep(), result.maskedDestination(),
                result.otpExpiresInSeconds() > 0 ? result.otpExpiresInSeconds() : null,
                result.resendCooldownSeconds() > 0 ? result.resendCooldownSeconds() : null,
                result.remainingResendAttempts() > 0 ? result.remainingResendAttempts() : null, result.csrfToken());
    }

    public SimpleMutationResponse<RegistrationStartResponse> toMutationResponse(StartRegistrationResult result) {
        boolean success = result.status() == StartRegistrationResultStatus.STARTED
                || result.status() == StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS
                || result.status() == StartRegistrationResultStatus.COMPLETED_REPLAY;
        return new SimpleMutationResponse<>(success, success ? "SUCCESS" : "VALIDATION_FAILED", toResponse(result),
                result.messages(), result.errors().stream().map(error -> toWebError(error, null)).toList());
    }

    public SimpleMutationResponse<RegistrationStartResponse> missingIdempotencyKeyResponse() {
        return new SimpleMutationResponse<>(false, "VALIDATION_FAILED", null, List.of(),
                List.of(new SimpleWebError(RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_MISSING.value(),
                        "Idempotency-Key")));
    }

    public SimpleMutationResponse<RegistrationStartResponse> toMutationResponse(StartRegistrationResult result,
            String acceptLanguage) {
        boolean success = isSuccess(result.status());
        logFatalIfNeeded(result);

        return new SimpleMutationResponse<>(
                success,
                toApiStatus(result.status()),
                success ? toResponse(result) : null,
                result.messages(),
                result.errors().stream()
                        .map(error -> toWebError(error, acceptLanguage))
                        .toList());
    }

    public SimpleMutationResponse<RegistrationStartResponse> missingIdempotencyKeyResponse(String acceptLanguage) {
        var code = RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_MISSING.value();
        return new SimpleMutationResponse<>(false, "VALIDATION_FAILED", null, List.of(),
                List.of(new SimpleWebError(code, "Idempotency-Key", resolveMessage(code, acceptLanguage))));
    }

    private SimpleWebError toWebError(StartRegistrationError error, String acceptLanguage) {
        String code = error.errorKey().value();
        return new SimpleWebError(code, error.target(),
                error.message() != null ? error.message() : resolveMessage(code, acceptLanguage));
    }

    private String resolveMessage(String code, String acceptLanguage) {
        if (errorMessageResolver == null || code == null || code.isBlank()) {
            return null;
        }
        return errorMessageResolver.resolve(code, acceptLanguage, null, null, null);
    }

    private boolean isSuccess(StartRegistrationResultStatus status) {
        return status == StartRegistrationResultStatus.STARTED
                || status == StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS
                || status == StartRegistrationResultStatus.COMPLETED_REPLAY;
    }

    private String toApiStatus(StartRegistrationResultStatus status) {
        if (isSuccess(status)) {
            return "SUCCESS";
        }

        if (status == StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED) {
            return "DOWNSTREAM_ERROR";
        }

        if (status == StartRegistrationResultStatus.SYSTEM_FAILED) {
            return "SYSTEM_ERROR";
        }

        return "VALIDATION_FAILED";
    }

    private void logFatalIfNeeded(StartRegistrationResult result) {
        if (result == null || result.status() == null) {
            return;
        }

        if (result.status() != StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED
                && result.status() != StartRegistrationResultStatus.SYSTEM_FAILED) {
            return;
        }

        log.error(
                "event=registration.start.failure severity=FATAL status={} errorCodes={}",
                result.status(),
                result.errors().stream()
                        .map(error -> error.errorKey().value())
                        .toList());
    }
}
