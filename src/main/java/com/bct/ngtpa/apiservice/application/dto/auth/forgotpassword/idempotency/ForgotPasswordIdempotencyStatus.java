package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

public enum ForgotPasswordIdempotencyStatus {
  PROCESSING,
  COMPLETED,
  FAILED,
  UNKNOWN_OUTCOME
}
