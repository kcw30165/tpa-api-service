package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend.*;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ResendLoginOtpUseCase {
    Mono<ResendLoginOtpResult> resend(ResendLoginOtpCommand command);
}
