package com.bct.ngtpa.apiservice.application.port.in;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;import reactor.core.publisher.Mono;
public interface StartLoginUseCase { Mono<StartLoginResult> start(StartLoginCommand command); }
