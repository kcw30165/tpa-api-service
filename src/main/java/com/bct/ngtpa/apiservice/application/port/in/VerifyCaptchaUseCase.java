package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeResult;
import reactor.core.publisher.Mono;

public interface VerifyCaptchaUseCase {

  Mono<VerifyCaptchaChallengeResult> verify(VerifyCaptchaChallengeCommand command);
}
