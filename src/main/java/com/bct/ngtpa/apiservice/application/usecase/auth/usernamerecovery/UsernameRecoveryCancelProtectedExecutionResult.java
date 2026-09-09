package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import java.util.Objects;

/** Transient result from the lifecycle-neutral Username Recovery CANCEL protected operation. */
public record UsernameRecoveryCancelProtectedExecutionResult(
    UsernameRecoveryCancelResult result, FailureBoundary failureBoundary, Throwable failure) {
  public enum FailureBoundary {
    NONE,
    PROVEN_PRE_DISPATCH,
    POSSIBLE_SIDE_EFFECT
  }

  public UsernameRecoveryCancelProtectedExecutionResult {
    Objects.requireNonNull(failureBoundary, "failureBoundary must not be null");
    boolean completed = failureBoundary == FailureBoundary.NONE;
    if (completed && result == null)
      throw new IllegalArgumentException("completed CANCEL requires result");
    if (completed && failure != null)
      throw new IllegalArgumentException("completed CANCEL cannot carry failure");
    if (!completed && result != null)
      throw new IllegalArgumentException("failed CANCEL cannot carry result");
    if (!completed && failure == null)
      throw new IllegalArgumentException("failed CANCEL requires failure");
  }

  public static UsernameRecoveryCancelProtectedExecutionResult completed(
      UsernameRecoveryCancelResult result) {
    return new UsernameRecoveryCancelProtectedExecutionResult(
        Objects.requireNonNull(result), FailureBoundary.NONE, null);
  }

  public static UsernameRecoveryCancelProtectedExecutionResult provenPreDispatch(
      Throwable failure) {
    return new UsernameRecoveryCancelProtectedExecutionResult(
        null, FailureBoundary.PROVEN_PRE_DISPATCH, Objects.requireNonNull(failure));
  }

  public static UsernameRecoveryCancelProtectedExecutionResult possibleSideEffect(
      Throwable failure) {
    return new UsernameRecoveryCancelProtectedExecutionResult(
        null, FailureBoundary.POSSIBLE_SIDE_EFFECT, Objects.requireNonNull(failure));
  }

  public boolean completed() {
    return failureBoundary == FailureBoundary.NONE;
  }

  @Override
  public String toString() {
    return "UsernameRecoveryCancelProtectedExecutionResult[completed="
        + completed()
        + ", resultOutcome="
        + (result == null ? null : result.outcome())
        + ", failureBoundary="
        + failureBoundary
        + ", failureType="
        + (failure == null ? null : failure.getClass().getSimpleName())
        + "]";
  }
}
