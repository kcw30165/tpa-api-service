package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record VerifyCaptchaChallengeResult(
        CaptchaVerificationOutcome outcome,
        boolean verified,
        CaptchaChallengeId captchaChallengeId,
        int remainingAttempts) {

    public VerifyCaptchaChallengeResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (remainingAttempts < 0) {
            throw new IllegalArgumentException("VerifyCaptchaChallengeResult remainingAttempts is invalid");
        }
        if (verified && captchaChallengeId == null) {
            throw new IllegalArgumentException("VerifyCaptchaChallengeResult captchaChallengeId is required when verified");
        }
    }

    @Override
    public String toString() {
        return "VerifyCaptchaChallengeResult[outcome=" + outcome + ", verified=" + verified + ", captchaChallengeId=<redacted>, remainingAttempts=" + remainingAttempts + "]";
    }
}