package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyResponseSnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import java.util.Objects;

public record ResendRegistrationOtpResult(
    ResendRegistrationOtpResultStatus status,
    WorkflowNextStep nextStep,
    String maskedDestination,
    int expiresInSeconds,
    int resendCooldownSeconds,
    int remainingResendAttempts,
    Integer retryAfterSeconds,
    List<WorkflowMessageDescriptor> messages,
    List<WorkflowErrorDescriptor> errors,
    RegistrationIdempotencyStartOutcome idempotencyOutcome,
    RegistrationIdempotencyResponseSnapshot replaySnapshot) {

  public ResendRegistrationOtpResult(
      ResendRegistrationOtpResultStatus status,
      WorkflowNextStep nextStep,
      String maskedDestination,
      int expiresInSeconds,
      int resendCooldownSeconds,
      int remainingResendAttempts,
      List<WorkflowMessageDescriptor> messages,
      List<WorkflowErrorDescriptor> errors,
      RegistrationIdempotencyStartOutcome idempotencyOutcome,
      RegistrationIdempotencyResponseSnapshot replaySnapshot) {
    this(
        status,
        nextStep,
        maskedDestination,
        expiresInSeconds,
        resendCooldownSeconds,
        remainingResendAttempts,
        null,
        messages,
        errors,
        idempotencyOutcome,
        replaySnapshot);
  }

  public ResendRegistrationOtpResult {
    Objects.requireNonNull(status, "status must not be null");
    nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
    messages = messages == null ? List.of() : List.copyOf(messages);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  @Override
  public String toString() {
    return "ResendRegistrationOtpResult[status="
        + status
        + ", nextStep="
        + nextStep
        + ", maskedDestination="
        + maskedDestination
        + ", expiresInSeconds="
        + expiresInSeconds
        + ", resendCooldownSeconds="
        + resendCooldownSeconds
        + ", remainingResendAttempts="
        + remainingResendAttempts
        + ", retryAfterSeconds="
        + retryAfterSeconds
        + ", messageCount="
        + messages.size()
        + ", errorCount="
        + errors.size()
        + "]";
  }
}
