package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.*;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.*;
import java.time.Duration;
import java.util.List;

public class RegistrationStartWebMapper {
    private final Duration defaultIdempotencyTtl;
    public RegistrationStartWebMapper(Duration defaultIdempotencyTtl) { this.defaultIdempotencyTtl = defaultIdempotencyTtl; }
    public StartRegistrationCommand toCommand(RegistrationStartRequest request, String idempotencyKey, String correlationId, String acceptLanguage) {
        return new StartRegistrationCommand(new RegistrationIdempotencyKey(idempotencyKey), request.identityType(), request.identityValue(), request.dateOfBirth(), request.email(), request.mobile(), request.captchaChallengeId(), acceptLanguage, correlationId, defaultIdempotencyTtl);
    }
    public RegistrationStartResponse toResponse(StartRegistrationResult result) {
        if (result == null || result.status() != StartRegistrationResultStatus.STARTED && result.status() != StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS) return null;
        return new RegistrationStartResponse(result.nextStep(), result.maskedDestination(), result.otpExpiresInSeconds() > 0 ? result.otpExpiresInSeconds() : null, result.resendCooldownSeconds() > 0 ? result.resendCooldownSeconds() : null, result.remainingResendAttempts() > 0 ? result.remainingResendAttempts() : null, result.csrfToken());
    }
    public SimpleMutationResponse<RegistrationStartResponse> toMutationResponse(StartRegistrationResult result) {
        boolean success = result.status() == StartRegistrationResultStatus.STARTED || result.status() == StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS || result.status() == StartRegistrationResultStatus.COMPLETED_REPLAY;
        return new SimpleMutationResponse<>(success, success ? "SUCCESS" : "VALIDATION_FAILED", toResponse(result), result.messages(), result.errors().stream().map(error -> new SimpleWebError(error.errorKey().value(), error.target())).toList());
    }
    public SimpleMutationResponse<RegistrationStartResponse> missingIdempotencyKeyResponse() {
        return new SimpleMutationResponse<>(false, "VALIDATION_FAILED", null, List.of(), List.of(new SimpleWebError(RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_MISSING.value(), "Idempotency-Key")));
    }
}
