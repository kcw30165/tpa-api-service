package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import reactor.core.publisher.Mono;

/** Internal protected operation used by the shared OTP Verify coordinator. */
@FunctionalInterface
public interface LoginOtpVerifyProtectedOperation {
  Mono<LoginOtpVerifyProtectedOperationResult> verifyProtected(VerifyLoginOtpCommand command);
}
