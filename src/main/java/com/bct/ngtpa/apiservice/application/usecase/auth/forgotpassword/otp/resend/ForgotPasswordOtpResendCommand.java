package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import java.time.Instant;

public record ForgotPasswordOtpResendCommand(
    SessionLockHandle mutationLock,
    AuthFlowSession lockReloadedSession,
    OtpChallenge authoritativeChallenge,
    ForgotPasswordProtectedPhase protectedPhase,
    int successfulResendCount,
    String idempotencyKey,
    Instant now) {}
