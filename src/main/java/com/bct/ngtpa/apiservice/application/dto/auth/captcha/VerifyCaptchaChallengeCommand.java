package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record VerifyCaptchaChallengeCommand(CaptchaChallengeId captchaChallengeId, String answer,
        String correlationId) {

    public VerifyCaptchaChallengeCommand {
        Objects.requireNonNull(captchaChallengeId, "captchaChallengeId must not be null");
        Objects.requireNonNull(answer, "answer must not be null");
        if (answer.isBlank()) {
            throw new IllegalArgumentException("VerifyCaptchaChallengeCommand answer is invalid");
        }
    }

    @Override
    public String toString() {
        return "VerifyCaptchaChallengeCommand[captchaChallengeId=<redacted>, answer=<redacted>, correlationId=<redacted>]";
    }
}