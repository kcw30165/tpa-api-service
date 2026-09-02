package com.bct.ngtpa.apiservice.application.idempotency;

/** Shared persistence lifecycle. COMPLETED includes deterministic unsuccessful responses. */
public enum IdempotencyStatus {
  PROCESSING,
  COMPLETED,
  FAILED,
  UNKNOWN_OUTCOME
}
