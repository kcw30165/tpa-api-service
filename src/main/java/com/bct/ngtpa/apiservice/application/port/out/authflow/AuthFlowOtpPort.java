package com.bct.ngtpa.apiservice.application.port.out.authflow;

import reactor.core.publisher.Mono;

/** Scenario-neutral orchestration boundary above the generic OTP challenge core. */
public interface AuthFlowOtpPort<C, CREATE_RESULT, VERIFY_RESULT> {
    Mono<CREATE_RESULT> create(C channel, String destination);
    Mono<CREATE_RESULT> resend(String activeChallengeId, C channel, String destination);
    Mono<VERIFY_RESULT> verify(String activeChallengeId, String submittedCode);
}
