package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import reactor.core.publisher.Mono;

public interface ForgotPasswordCompletionPort {
    Mono<ForgotPasswordCompletionResult> complete(ForgotPasswordCompletionCommand command);
}