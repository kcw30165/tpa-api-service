package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

public enum LoginIdempotencyStatus {
  PROCESSING,
  COMPLETED,
  FAILED,
  UNKNOWN_OUTCOME
}
