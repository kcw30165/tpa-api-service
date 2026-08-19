package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import reactor.core.publisher.Mono;

public interface ResendRegistrationOtpUseCase {
  Mono<ResendRegistrationOtpResult> resend(ResendRegistrationOtpCommand command);
}
