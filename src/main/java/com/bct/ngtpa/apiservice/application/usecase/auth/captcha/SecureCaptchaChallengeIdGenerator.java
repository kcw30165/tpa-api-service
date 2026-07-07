package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import java.util.UUID;

public final class SecureCaptchaChallengeIdGenerator implements CaptchaChallengeIdGenerator {

    @Override
    public CaptchaChallengeId nextId() {
        return new CaptchaChallengeId("cap_" + UUID.randomUUID().toString().replace("-", ""));
    }
}