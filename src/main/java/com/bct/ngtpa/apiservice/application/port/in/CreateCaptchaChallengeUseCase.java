package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import reactor.core.publisher.Mono;

public interface CreateCaptchaChallengeUseCase {

    Mono<CreateCaptchaChallengeResult> create(CreateCaptchaChallengeCommand command);
}