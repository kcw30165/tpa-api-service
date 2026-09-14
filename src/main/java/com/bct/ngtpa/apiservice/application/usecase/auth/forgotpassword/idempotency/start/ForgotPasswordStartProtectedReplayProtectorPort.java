package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;

public interface ForgotPasswordStartProtectedReplayProtectorPort {
  ProtectedForgotPasswordStartReplay protect(
      ProtectedReplayReference reference, ForgotPasswordStartProtectedReplayState state);

  ForgotPasswordStartProtectedReplayState unprotect(
      ProtectedReplayReference reference, ProtectedForgotPasswordStartReplay protectedReplay);
}
