package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import java.time.*;
import java.util.Objects;

public record ForgotPasswordOtpResendPolicy(int cooldownSeconds, int maxSuccessfulResends) {
    public ForgotPasswordOtpResendPolicy {
        if (cooldownSeconds <= 0)
            throw new IllegalArgumentException("forgot password resend cooldown must be positive");
        if (maxSuccessfulResends <= 0)
            throw new IllegalArgumentException("forgot password max successful resends must be positive");
    }

    public ForgotPasswordOtpResendPolicyResult evaluate(Instant latestAcceptedAt, int successfulResendCount,
            Instant now) {
        Objects.requireNonNull(latestAcceptedAt);
        Objects.requireNonNull(now);
        if (successfulResendCount < 0)
            throw new IllegalArgumentException("successful resend count must not be negative");
        if (successfulResendCount >= maxSuccessfulResends)
            return new ForgotPasswordOtpResendPolicyResult(ForgotPasswordOtpResendPolicyStatus.RESEND_LIMIT_REACHED, 0);
        long remaining = Math.max(0, Duration.between(now, latestAcceptedAt.plusSeconds(cooldownSeconds)).toSeconds());
        return remaining > 0
                ? new ForgotPasswordOtpResendPolicyResult(ForgotPasswordOtpResendPolicyStatus.COOLDOWN_ACTIVE,
                        Math.toIntExact(remaining))
                : new ForgotPasswordOtpResendPolicyResult(ForgotPasswordOtpResendPolicyStatus.ALLOWED, 0);
    }
}
