package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import reactor.core.publisher.Mono;

public interface VerifyRegistrationOtpUseCase {
  Mono<VerifyRegistrationOtpResult> verify(VerifyRegistrationOtpCommand command);
}
