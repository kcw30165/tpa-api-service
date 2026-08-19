package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.*;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface VerifyLoginOtpUseCase {
  Mono<VerifyLoginOtpResult> verify(VerifyLoginOtpCommand command);
}
