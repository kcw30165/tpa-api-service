package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import java.util.Objects;

/**
 * Transient result returned by the Username Recovery REQUEST protected operation.
 *
 * <p>The result separates deterministic terminal responses from failures that must be propagated to
 * the shared executor. A failure classification describes only whether message dispatch may have
 * produced a side effect. It never contains session identity, username, destination, CSRF material,
 * delivery payload, persistence ownership, or replay data.
 */
public record UsernameRecoveryRequestProtectedExecutionResult(
    UsernameRecoveryRequestResult result, FailureBoundary failureBoundary, Throwable failure) {

  public enum FailureBoundary {
    NONE,
    PROVEN_PRE_DISPATCH,
    POSSIBLE_SIDE_EFFECT
  }

  public UsernameRecoveryRequestProtectedExecutionResult {
    Objects.requireNonNull(failureBoundary, "failureBoundary must not be null");
    boolean succeeded = failureBoundary == FailureBoundary.NONE;
    if (succeeded && result == null) {
      throw new IllegalArgumentException("successful protected execution requires a result");
    }
    if (succeeded && failure != null) {
      throw new IllegalArgumentException("successful protected execution must not carry a failure");
    }
    if (!succeeded && result != null) {
      throw new IllegalArgumentException("failed protected execution must not carry a result");
    }
    if (!succeeded && failure == null) {
      throw new IllegalArgumentException("failed protected execution requires a failure");
    }
  }

  public static UsernameRecoveryRequestProtectedExecutionResult completed(
      UsernameRecoveryRequestResult result) {
    return new UsernameRecoveryRequestProtectedExecutionResult(
        Objects.requireNonNull(result, "result must not be null"), FailureBoundary.NONE, null);
  }

  public static UsernameRecoveryRequestProtectedExecutionResult provenPreDispatch(
      Throwable failure) {
    return new UsernameRecoveryRequestProtectedExecutionResult(
        null,
        FailureBoundary.PROVEN_PRE_DISPATCH,
        Objects.requireNonNull(failure, "failure must not be null"));
  }

  public static UsernameRecoveryRequestProtectedExecutionResult possibleSideEffect(
      Throwable failure) {
    return new UsernameRecoveryRequestProtectedExecutionResult(
        null,
        FailureBoundary.POSSIBLE_SIDE_EFFECT,
        Objects.requireNonNull(failure, "failure must not be null"));
  }

  public boolean completed() {
    return failureBoundary == FailureBoundary.NONE;
  }

  @Override
  public String toString() {
    return "UsernameRecoveryRequestProtectedExecutionResult[completed="
        + completed()
        + ", failureBoundary="
        + failureBoundary
        + ", resultOutcome="
        + (result == null ? null : result.outcome())
        + ", failureType="
        + (failure == null ? null : failure.getClass().getSimpleName())
        + "]";
  }
}
