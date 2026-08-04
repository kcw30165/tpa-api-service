package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import java.util.Objects;

public final class ForgotPasswordOtpResendEligibilityValidator {
    private final ForgotPasswordOtpResendPolicy policy;

    public ForgotPasswordOtpResendEligibilityValidator(ForgotPasswordOtpResendPolicy p) {
        policy = Objects.requireNonNull(p);
    }

    public ForgotPasswordOtpResendEligibilityResult validate(ForgotPasswordOtpResendEligibilityInput i) {
        if (i == null || i.scenario() != AuthFlowScenario.FORGOT_PASSWORD || i.status() != AuthFlowStatus.OTP_SENT
                || i.now() == null)
            return out(ForgotPasswordOtpResendEligibilityStatus.SESSION_INVALID, i, 0);
        if (i.protectedPhase() == ForgotPasswordProtectedPhase.OTP_DELIVERY_UNKNOWN)
            return out(ForgotPasswordOtpResendEligibilityStatus.UNKNOWN_OUTCOME_BLOCKED, i, 0);
        if (i.protectedPhase() != ForgotPasswordProtectedPhase.OTP_SENT)
            return out(ForgotPasswordOtpResendEligibilityStatus.INVALID_PHASE, i, 0);
        OtpChallenge c = i.activeChallenge();
        if (c == null)
            return out(ForgotPasswordOtpResendEligibilityStatus.NO_ACTIVE_CHALLENGE, i, 0);
        if (c.scenario() != OtpScenario.FORGOT_PASSWORD || !Objects.equals(c.workflowRef(), i.sessionId()))
            return out(ForgotPasswordOtpResendEligibilityStatus.CHALLENGE_BINDING_INVALID, i, 0);
        var p = policy.evaluate(latestAcceptedAt(c), i.successfulResendCount(), i.now());
        return switch (p.status()) {
            case ALLOWED -> out(ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE, i, 0);
            case COOLDOWN_ACTIVE ->
                out(ForgotPasswordOtpResendEligibilityStatus.COOLDOWN_ACTIVE, i, p.retryAfterSeconds());
            case RESEND_LIMIT_REACHED -> out(ForgotPasswordOtpResendEligibilityStatus.RESEND_LIMIT_REACHED, i, 0);
        };
    }

    private java.time.Instant latestAcceptedAt(OtpChallenge challenge) {
        if (challenge.lastSentAt() != null) {
            return challenge.lastSentAt();
        }
        if (challenge.createdAt() != null) {
            return challenge.createdAt();
        }
        throw new IllegalStateException("Forgot password resend timing unavailable");
    }

    private ForgotPasswordOtpResendEligibilityResult out(ForgotPasswordOtpResendEligibilityStatus s,
            ForgotPasswordOtpResendEligibilityInput i, int retry) {
        return new ForgotPasswordOtpResendEligibilityResult(s,
                i == null || i.activeChallenge() == null ? null : i.activeChallenge().lastSentAt(), retry,
                i == null ? 0 : i.successfulResendCount());
    }
}
