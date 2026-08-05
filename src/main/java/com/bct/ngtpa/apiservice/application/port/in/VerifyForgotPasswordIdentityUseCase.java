package com.bct.ngtpa.apiservice.application.port.in;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.*;import reactor.core.publisher.Mono;
public interface VerifyForgotPasswordIdentityUseCase { Mono<VerifyForgotPasswordIdentityResult> verify(VerifyForgotPasswordIdentityCommand command); }
