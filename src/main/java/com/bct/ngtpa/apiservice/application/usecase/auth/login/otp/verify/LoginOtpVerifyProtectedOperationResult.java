package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.util.Objects;

/** Internal protected-operation outcome for shared OTP Verify execution. */
public record LoginOtpVerifyProtectedOperationResult(
    VerifyLoginOtpResult result, ProtectedReplayReference protectedReplayReference) {

  public LoginOtpVerifyProtectedOperationResult {
    Objects.requireNonNull(result, "result must not be null");
    if (result.status() == VerifyLoginOtpStatus.ACCOUNTS_READY
        && protectedReplayReference == null) {
      throw new IllegalArgumentException("OTP Verify success requires protected replay reference");
    }
    if (result.status() != VerifyLoginOtpStatus.ACCOUNTS_READY
        && protectedReplayReference != null) {
      throw new IllegalArgumentException("OTP Verify rejection cannot carry protected replay");
    }
  }
}
