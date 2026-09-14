package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/**
 * Safe reservation result. Ownership proof is present only for ACQUIRED; duplicate outcomes never
 * expose the stored reservation token or persistence record.
 */
public record IdempotencyReservation(
    IdempotencyReservationOutcome outcome,
    IdempotencyStatus existingStatus,
    IdempotentTerminalResponseSnapshot terminalResponse,
    IdempotencyReservationToken reservationToken) {
  public IdempotencyReservation {
    Objects.requireNonNull(outcome, "reservation outcome must not be null");
    if (outcome == IdempotencyReservationOutcome.ACQUIRED) {
      Objects.requireNonNull(reservationToken, "acquired reservation requires ownership token");
      if (existingStatus != IdempotencyStatus.PROCESSING || terminalResponse != null) {
        throw new IllegalArgumentException("acquired reservation requires processing state only");
      }
    } else if (reservationToken != null) {
      throw new IllegalArgumentException("non-acquired reservation cannot expose ownership token");
    }
    if (outcome == IdempotencyReservationOutcome.COMPLETED_REPLAY) {
      if (existingStatus != IdempotencyStatus.COMPLETED || terminalResponse == null) {
        throw new IllegalArgumentException("completed replay requires terminal response");
      }
    } else if (terminalResponse != null) {
      throw new IllegalArgumentException("only completed replay can expose terminal response");
    }
  }
}
