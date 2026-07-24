package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend.LoginOtpReplacementBindingCommand;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface LoginOtpReplacementBindingPort {
    Mono<Void> bind(LoginOtpReplacementBindingCommand command);
}
