package com.bct.ngtpa.apiservice.application.idempotency;

/** Result of the shared atomic reservation operation. */
public enum IdempotencyReservationOutcome {
  ACQUIRED,
  DUPLICATE_IN_PROGRESS,
  COMPLETED_REPLAY,
  KEY_REUSED_WITH_DIFFERENT_REQUEST,
  FAILED_EXISTING,
  UNKNOWN_OUTCOME_EXISTING
}
