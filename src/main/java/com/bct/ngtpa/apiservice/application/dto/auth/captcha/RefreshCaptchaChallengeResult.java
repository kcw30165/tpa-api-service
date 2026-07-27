package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record RefreshCaptchaChallengeResult(CaptchaChallengeId captchaChallengeId, CaptchaImage image,
        int expiresInSeconds) {

    public RefreshCaptchaChallengeResult {
        Objects.requireNonNull(captchaChallengeId, "captchaChallengeId must not be null");
        Objects.requireNonNull(image, "image must not be null");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("RefreshCaptchaChallengeResult expiresInSeconds is invalid");
        }
    }

    @Override
    public String toString() {
        return "RefreshCaptchaChallengeResult[captchaChallengeId=<redacted>, image=<redacted>, expiresInSeconds="
                + expiresInSeconds + "]";
    }
}