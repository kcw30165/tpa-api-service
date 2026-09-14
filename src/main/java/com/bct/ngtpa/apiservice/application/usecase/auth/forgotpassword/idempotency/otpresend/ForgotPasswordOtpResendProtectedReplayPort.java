package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import reactor.core.publisher.Mono;

/** Resolves separately protected OTP_RESEND continuity from an opaque reference. */
public interface ForgotPasswordOtpResendProtectedReplayPort {
  Mono<ResendForgotPasswordOtpResult> resolve(ProtectedReplayReference reference);
}
