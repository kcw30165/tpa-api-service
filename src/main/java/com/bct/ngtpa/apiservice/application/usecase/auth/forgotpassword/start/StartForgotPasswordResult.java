package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyReplaySnapshot;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import java.util.Objects;

public record StartForgotPasswordResult(
        StartForgotPasswordResultStatus status,
        WorkflowNextStep nextStep,
        String csrfToken,
        String authFlowSessionId,
        List<StartForgotPasswordError> errors,
        ForgotPasswordIdempotencyReplaySnapshot replaySnapshot) {
    public StartForgotPasswordResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(nextStep, "nextStep must not be null");
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    @Override
    public String toString() {
        return "StartForgotPasswordResult[status=" + status + ", nextStep=" + nextStep
                + ", csrfToken=<redacted>, authFlowSessionId=<redacted>, errors=" + errors
                + ", replaySnapshot=<redacted>]";
    }
}