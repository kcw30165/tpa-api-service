package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import reactor.core.publisher.Mono;

public interface StartForgotPasswordUseCase {
  Mono<StartForgotPasswordResult> start(StartForgotPasswordCommand command);
}
