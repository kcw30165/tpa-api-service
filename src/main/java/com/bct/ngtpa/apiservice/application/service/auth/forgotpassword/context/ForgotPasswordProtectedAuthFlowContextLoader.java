package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context;

import reactor.core.publisher.Mono;

public interface ForgotPasswordProtectedAuthFlowContextLoader {
    Mono<ForgotPasswordProtectedAuthFlowContextResult> load(ForgotPasswordProtectedAuthFlowContextCommand command);
}