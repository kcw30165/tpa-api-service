package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.GeneratedCaptcha;
import reactor.core.publisher.Mono;

public interface CaptchaImageGeneratorPort {

  Mono<GeneratedCaptcha> generate(CaptchaPolicy policy);
}
