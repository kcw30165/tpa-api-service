package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;

@FunctionalInterface
public interface ForgotPasswordStartProtectedReplayReferenceGenerator {
  ProtectedReplayReference generate();
}
