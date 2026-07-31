package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import java.time.*;
import java.util.Objects;

public record ForgotPasswordOtpCommitRetentionPolicy(Duration expiredReplayGrace, Duration maximumRetention) {
    public ForgotPasswordOtpCommitRetentionPolicy {
        Objects.requireNonNull(expiredReplayGrace);
        Objects.requireNonNull(maximumRetention);
        if (expiredReplayGrace.isNegative() || expiredReplayGrace.isZero()
                || maximumRetention.compareTo(expiredReplayGrace) < 0)
            throw new IllegalArgumentException("invalid commit retention policy");
    }

    public Duration ttl(Instant now, Instant challengeExpiry, Instant idempotencyExpiry) {
        Instant end = challengeExpiry.plus(expiredReplayGrace).isAfter(idempotencyExpiry)
                ? challengeExpiry.plus(expiredReplayGrace)
                : idempotencyExpiry;
        Duration ttl = Duration.between(now, end);
        if (ttl.isNegative() || ttl.isZero())
            throw new IllegalArgumentException("commit retention already elapsed");
        if (ttl.compareTo(maximumRetention) > 0)
            throw new IllegalArgumentException("commit retention exceeds bound");
        return ttl;
    }
}
