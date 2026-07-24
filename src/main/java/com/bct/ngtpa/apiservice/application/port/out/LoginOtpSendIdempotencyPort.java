package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.idempotency.*;
import reactor.core.publisher.Mono;

public interface LoginOtpSendIdempotencyPort {
    Mono<LoginOtpSendReservation> reserve(LoginOtpSendReserveCommand command);

    Mono<Void> complete(LoginOtpSendCompleteCommand command);

    Mono<Void> fail(LoginOtpSendTerminalCommand command);

    Mono<Void> unknown(LoginOtpSendTerminalCommand command);
}
