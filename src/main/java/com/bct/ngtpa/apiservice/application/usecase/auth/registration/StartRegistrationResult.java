package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyResponseSnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import java.util.Objects;

public record StartRegistrationResult(
    StartRegistrationResultStatus status,
    WorkflowNextStep nextStep,
    String maskedDestination,
    int otpExpiresInSeconds,
    int resendCooldownSeconds,
    int remainingResendAttempts,
    String csrfToken,
    String authFlowSessionId,
    List<String> messages,
    List<StartRegistrationError> errors,
    RegistrationIdempotencyStartOutcome idempotencyOutcome,
    RegistrationIdempotencyResponseSnapshot replaySnapshot) {
  public StartRegistrationResult {
    Objects.requireNonNull(status, "status must not be null");
    nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
    messages = messages == null ? List.of() : List.copyOf(messages);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  @Override
  public String toString() {
    return "StartRegistrationResult[status="
        + status
        + ", nextStep="
        + nextStep
        + ", maskedDestination="
        + maskedDestination
        + ", otpExpiresInSeconds="
        + otpExpiresInSeconds
        + ", resendCooldownSeconds="
        + resendCooldownSeconds
        + ", remainingResendAttempts="
        + remainingResendAttempts
        + ", csrfToken=<redacted>, authFlowSessionId=<redacted>, messageCount="
        + messages.size()
        + ", errorCount="
        + errors.size()
        + ", idempotencyOutcome="
        + idempotencyOutcome
        + ", replaySnapshot=<redacted>]";
  }
}
