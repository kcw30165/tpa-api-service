package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordResult;
import reactor.core.publisher.Mono;

/** Forgot Password Cancel mutation boundary. */
@FunctionalInterface
public interface CancelForgotPasswordUseCase {
    Mono<CancelForgotPasswordResult> cancel(CancelForgotPasswordCommand command);
}