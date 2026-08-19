package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface OtpChallengeStatePort {

  Mono<Void> save(OtpChallenge challenge);

  Mono<Optional<OtpChallenge>> findById(String otpChallengeId);

  Mono<Void> update(OtpChallenge challenge);

  Mono<Void> delete(String otpChallengeId);
}
