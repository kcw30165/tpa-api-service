package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record CreateCaptchaChallengeResult(CaptchaChallengeId captchaChallengeId, CaptchaImage image, int expiresInSeconds) {

    public CreateCaptchaChallengeResult {
        Objects.requireNonNull(captchaChallengeId, "captchaChallengeId must not be null");
        Objects.requireNonNull(image, "image must not be null");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("CreateCaptchaChallengeResult expiresInSeconds is invalid");
        }
    }

    @Override
    public String toString() {
        return "CreateCaptchaChallengeResult[captchaChallengeId=<redacted>, image=<redacted>, expiresInSeconds=" + expiresInSeconds + "]";
    }
}