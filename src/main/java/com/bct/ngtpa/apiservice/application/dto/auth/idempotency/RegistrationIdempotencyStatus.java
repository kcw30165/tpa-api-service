package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

public enum RegistrationIdempotencyStatus {
  PROCESSING,
  COMPLETED,
  FAILED,
  UNKNOWN_OUTCOME
}
