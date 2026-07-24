package com.bct.ngtpa.apiservice.application.port.in;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.*;import reactor.core.publisher.Mono;
@FunctionalInterface public interface SendLoginOtpUseCase {Mono<SendLoginOtpResult> send(SendLoginOtpCommand command);}
