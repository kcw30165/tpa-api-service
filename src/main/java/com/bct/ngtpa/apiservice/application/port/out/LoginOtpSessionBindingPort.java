package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginOtpSessionBindingCommand;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface LoginOtpSessionBindingPort {
  Mono<Void> bind(LoginOtpSessionBindingCommand command);
}
