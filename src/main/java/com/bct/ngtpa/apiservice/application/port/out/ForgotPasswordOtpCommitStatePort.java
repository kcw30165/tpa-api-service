package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import java.time.Duration;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface ForgotPasswordOtpCommitStatePort {
    Mono<Boolean> saveIfAbsent(String opaqueCommitRef, ForgotPasswordOtpCommitState state, Duration ttl);

    Mono<Optional<ForgotPasswordOtpCommitState>> load(String opaqueCommitRef);

    Mono<Void> delete(String opaqueCommitRef);
}
