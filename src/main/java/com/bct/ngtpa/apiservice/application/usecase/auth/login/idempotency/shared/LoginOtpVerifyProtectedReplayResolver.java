package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpResult;
import reactor.core.publisher.Mono;

/** Resolves an opaque OTP Verify replay reference through committed-login recovery only. */
@FunctionalInterface
public interface LoginOtpVerifyProtectedReplayResolver {
  Mono<VerifyLoginOtpResult> resolve(
      ProtectedReplayReference protectedReplayReference, String correlationId);
}
