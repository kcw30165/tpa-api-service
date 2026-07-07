package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;

public interface CaptchaChallengeIdGenerator {

    CaptchaChallengeId nextId();
}