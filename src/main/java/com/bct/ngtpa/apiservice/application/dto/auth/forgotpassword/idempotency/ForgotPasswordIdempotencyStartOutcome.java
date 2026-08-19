package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

public enum ForgotPasswordIdempotencyStartOutcome {
  STARTED,
  DUPLICATE_IN_PROGRESS,
  COMPLETED_REPLAY,
  FAILED_EXISTING,
  UNKNOWN_OUTCOME_EXISTING,
  KEY_REUSED_WITH_DIFFERENT_REQUEST
}
