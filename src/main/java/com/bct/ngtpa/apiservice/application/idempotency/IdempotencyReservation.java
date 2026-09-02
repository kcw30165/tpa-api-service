package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/** Reservation result with the existing or newly created shared record. */
public record IdempotencyReservation(
    IdempotencyReservationOutcome outcome, IdempotencyRecord record) {
  public IdempotencyReservation {
    Objects.requireNonNull(outcome, "reservation outcome must not be null");
    Objects.requireNonNull(record, "reservation record must not be null");
    if (outcome == IdempotencyReservationOutcome.COMPLETED_REPLAY
        && record.status() != IdempotencyStatus.COMPLETED) {
      throw new IllegalArgumentException("completed replay requires completed record");
    }
  }
}
