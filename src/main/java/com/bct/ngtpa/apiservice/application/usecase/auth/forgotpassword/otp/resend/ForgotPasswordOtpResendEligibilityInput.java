package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import java.time.Instant;

public record ForgotPasswordOtpResendEligibilityInput(AuthFlowScenario scenario, AuthFlowStatus status,
        String sessionId, OtpChallenge activeChallenge, ForgotPasswordProtectedPhase protectedPhase,
        int successfulResendCount, Instant now) {
}
