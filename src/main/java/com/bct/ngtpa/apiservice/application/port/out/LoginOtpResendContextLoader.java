package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend.*;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface LoginOtpResendContextLoader {
    Mono<LoginOtpResendContextResult> load(LoginOtpResendContextCommand command);
}
