package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.time.Instant;
import java.util.Objects;

public record ConsumeCaptchaChallengeCommand(CaptchaChallengeId captchaChallengeId, Instant consumedAt) {

    public ConsumeCaptchaChallengeCommand {
        Objects.requireNonNull(captchaChallengeId, "captchaChallengeId must not be null");
        Objects.requireNonNull(consumedAt, "consumedAt must not be null");
    }

    @Override
    public String toString() {
        return "ConsumeCaptchaChallengeCommand[captchaChallengeId=<redacted>, consumedAt=" + consumedAt + "]";
    }
}