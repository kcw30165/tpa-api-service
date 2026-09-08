package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import java.util.Objects;

/** Idempotency-neutral COMPLETE execution classification returned to the shared coordinator. */
public record ForgotPasswordCompleteProtectedExecutionResult(
    Outcome outcome, CompleteForgotPasswordResult result) {
  public enum Outcome {
    TERMINAL,
    TECHNICAL_FAILURE,
    UNKNOWN_OUTCOME
  }

  public ForgotPasswordCompleteProtectedExecutionResult {
    Objects.requireNonNull(outcome, "protected execution outcome must not be null");
    Objects.requireNonNull(result, "protected execution result must not be null");
    if (outcome == Outcome.UNKNOWN_OUTCOME
        && result.status() != CompleteForgotPasswordStatus.UNKNOWN_OUTCOME) {
      throw new IllegalArgumentException("unknown execution requires unknown public result");
    }
    if (outcome == Outcome.TECHNICAL_FAILURE
        && result.status() != CompleteForgotPasswordStatus.SYSTEM_FAILED) {
      throw new IllegalArgumentException("technical failure requires system-failed public result");
    }
    if (outcome == Outcome.TERMINAL
        && (result.status() == CompleteForgotPasswordStatus.UNKNOWN_OUTCOME
            || result.status() == CompleteForgotPasswordStatus.SYSTEM_FAILED)) {
      throw new IllegalArgumentException("terminal execution requires capturable public result");
    }
  }

  public static ForgotPasswordCompleteProtectedExecutionResult terminal(
      CompleteForgotPasswordResult result) {
    return new ForgotPasswordCompleteProtectedExecutionResult(Outcome.TERMINAL, result);
  }

  public static ForgotPasswordCompleteProtectedExecutionResult technicalFailure(
      CompleteForgotPasswordResult result) {
    return new ForgotPasswordCompleteProtectedExecutionResult(Outcome.TECHNICAL_FAILURE, result);
  }

  public static ForgotPasswordCompleteProtectedExecutionResult unknownOutcome(
      CompleteForgotPasswordResult result) {
    return new ForgotPasswordCompleteProtectedExecutionResult(Outcome.UNKNOWN_OUTCOME, result);
  }
}
