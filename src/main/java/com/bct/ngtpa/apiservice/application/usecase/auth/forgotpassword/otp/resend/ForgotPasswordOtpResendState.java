package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import java.time.Instant;

public record ForgotPasswordOtpResendState(String activeChallengeId, int successfulResendCount,
        Instant latestAcceptedAt, boolean blocked) {
    public ForgotPasswordOtpResendState(String id, int count, Instant at) {
        this(id, count, at, false);
    }

    public ForgotPasswordOtpResendState {
        if (activeChallengeId == null || activeChallengeId.isBlank())
            throw new IllegalArgumentException("active challenge required");
        if (successfulResendCount < 0)
            throw new IllegalArgumentException("count must not be negative");
    }

    public ForgotPasswordOtpResendState transition(ForgotPasswordOtpResendDeliveryOutcome o, String candidate,
            Instant at) {
        return switch (o) {
            case ACCEPTED ->
                new ForgotPasswordOtpResendState(candidate, Math.addExact(successfulResendCount, 1), at, false);
            case FAILED -> this;
            case UNKNOWN_OUTCOME ->
                new ForgotPasswordOtpResendState(activeChallengeId, successfulResendCount, latestAcceptedAt, true);
        };
    }

    @Override
    public String toString() {
        return "ForgotPasswordOtpResendState[activeChallengeId=<redacted>,successfulResendCount=<redacted>,latestAcceptedAt=<redacted>,blocked="
                + blocked + "]";
    }
}
