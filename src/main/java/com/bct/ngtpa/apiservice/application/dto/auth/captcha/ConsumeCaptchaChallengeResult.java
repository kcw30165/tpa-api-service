package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record ConsumeCaptchaChallengeResult(CaptchaConsumeOutcome outcome, CaptchaChallengeId captchaChallengeId) {

    public ConsumeCaptchaChallengeResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    @Override
    public String toString() {
        return "ConsumeCaptchaChallengeResult[outcome=" + outcome + ", captchaChallengeId=<redacted>]";
    }
}