package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeResult;
import reactor.core.publisher.Mono;

public interface RefreshCaptchaUseCase {

    Mono<RefreshCaptchaChallengeResult> refresh(RefreshCaptchaChallengeCommand command);
}