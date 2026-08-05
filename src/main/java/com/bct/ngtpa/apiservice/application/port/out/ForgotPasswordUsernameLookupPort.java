package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordUsernameLookupResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordUsernameLookupCommand;
import reactor.core.publisher.Mono;

public interface ForgotPasswordUsernameLookupPort {
    Mono<ForgotPasswordUsernameLookupResult> lookup(StartForgotPasswordUsernameLookupCommand command);
}