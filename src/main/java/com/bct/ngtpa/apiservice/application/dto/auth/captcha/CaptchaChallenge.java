package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;

public record CaptchaChallenge(
        CaptchaChallengeId captchaChallengeId,
        CaptchaAnswerHash answerHash,
        CaptchaStatus status,
        int failedAttemptCount,
        int maxAttempts,
        Instant createdAt,
        Instant expiresAt,
        Instant verifiedAt,
        Instant consumedAt,
        Instant refreshedAt,
        Instant lockedAt) {

    private static final EnumSet<CaptchaStatus> HASH_REQUIRED_STATUSES = EnumSet.of(CaptchaStatus.CREATED,
            CaptchaStatus.VERIFIED, CaptchaStatus.LOCKED);

    public CaptchaChallenge {
        Objects.requireNonNull(captchaChallengeId, "captchaChallengeId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (HASH_REQUIRED_STATUSES.contains(status) && answerHash == null) {
            throw new IllegalArgumentException("CaptchaChallenge answerHash is required");
        }
        if (failedAttemptCount < 0) {
            throw new IllegalArgumentException("CaptchaChallenge failedAttemptCount is invalid");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("CaptchaChallenge maxAttempts is invalid");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("CaptchaChallenge expiry is invalid");
        }
    }

    @Override
    public String toString() {
        return "CaptchaChallenge[captchaChallengeId=<redacted>, answerHash=<redacted>, status=" + status
                + ", failedAttemptCount=" + failedAttemptCount
                + ", maxAttempts=" + maxAttempts
                + ", createdAt=" + createdAt
                + ", expiresAt=" + expiresAt
                + ", verifiedAt=" + verifiedAt
                + ", consumedAt=" + consumedAt
                + ", refreshedAt=" + refreshedAt
                + ", lockedAt=" + lockedAt + "]";
    }
}