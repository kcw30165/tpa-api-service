package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordResult;
import reactor.core.publisher.Mono;

public interface CompleteForgotPasswordUseCase {
    Mono<CompleteForgotPasswordResult> complete(CompleteForgotPasswordCommand command);
}