package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.*;
import reactor.core.publisher.Mono;

public interface ForgotPasswordOtpSessionBindingPort {
  Mono<ForgotPasswordOtpBindingResult> apply(ForgotPasswordOtpBindingCommand command);
}
