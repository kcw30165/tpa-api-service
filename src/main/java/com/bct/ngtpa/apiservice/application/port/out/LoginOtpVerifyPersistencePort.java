package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import reactor.core.publisher.Mono;

public interface LoginOtpVerifyPersistencePort {
    Mono<LoginOtpVerifyPersistenceResult> verify(RevisionedLoginOtpVerifyCommand command);
}
