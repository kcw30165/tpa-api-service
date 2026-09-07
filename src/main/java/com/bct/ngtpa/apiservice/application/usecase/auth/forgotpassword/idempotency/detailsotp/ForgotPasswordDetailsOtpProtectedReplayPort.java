package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import reactor.core.publisher.Mono;

/** Resolves separately protected, authoritative DETAILS_AND_OTP replay continuity. */
public interface ForgotPasswordDetailsOtpProtectedReplayPort {
  Mono<ForgotPasswordDetailsOtpResult> resolve(ProtectedReplayReference reference);
}
