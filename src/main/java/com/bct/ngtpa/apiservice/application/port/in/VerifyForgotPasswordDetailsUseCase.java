package com.bct.ngtpa.apiservice.application.port.in;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.*;
import reactor.core.publisher.Mono;
public interface VerifyForgotPasswordDetailsUseCase { Mono<ForgotPasswordDetailsResult> verify(ForgotPasswordDetailsCommand command); }
