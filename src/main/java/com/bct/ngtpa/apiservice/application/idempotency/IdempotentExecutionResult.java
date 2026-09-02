package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/** Executor result separates lifecycle origin from the original terminal public response. */
public record IdempotentExecutionResult(
    IdempotencyReservationOutcome lifecycleOutcome, HydratedIdempotentResponse terminalResponse) {
  public IdempotentExecutionResult {
    Objects.requireNonNull(lifecycleOutcome, "lifecycle outcome must not be null");
    if ((lifecycleOutcome == IdempotencyReservationOutcome.ACQUIRED
            || lifecycleOutcome == IdempotencyReservationOutcome.COMPLETED_REPLAY)
        && terminalResponse == null) {
      throw new IllegalArgumentException("terminal execution outcome requires response snapshot");
    }
  }
}
