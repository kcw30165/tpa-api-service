package com.bct.ngtpa.apiservice.application.port.out.authflow;

import reactor.core.publisher.Mono;

/**
 * Scenario-neutral orchestration boundary above the generic OTP challenge core.
 *
 * <p>The method names intentionally retain the established OTP capability
 * vocabulary. Registration, login, and future recovery workflows can implement
 * this shared contract without compatibility constructors or duplicate adapters.</p>
 */
public interface AuthFlowOtpPort<C, CREATE_RESULT, VERIFY_RESULT> {
    Mono<CREATE_RESULT> createOtp(C channel, String destination);

    Mono<CREATE_RESULT> resendOtp(
            String activeChallengeId,
            C channel,
            String destination);

    Mono<VERIFY_RESULT> verifyOtp(
            String activeChallengeId,
            String submittedCode);
}
