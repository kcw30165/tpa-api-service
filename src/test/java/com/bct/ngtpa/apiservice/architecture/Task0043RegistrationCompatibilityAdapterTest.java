package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.registration.compatibility.RegistrationAuthFlowMessageCompatibilityAdapter;
import com.bct.ngtpa.apiservice.adapter.out.registration.compatibility.RegistrationAuthFlowOtpCompatibilityAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerificationResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class Task0043RegistrationCompatibilityAdapterTest {

    @Test
    void otpCompatibilityAdapterDelegatesWithoutAddingLifecycleLogic() {
        AtomicInteger creates = new AtomicInteger();
        AtomicInteger resends = new AtomicInteger();
        AtomicInteger verifies = new AtomicInteger();
        RegistrationOtpPort delegate = new RegistrationOtpPort() {
            @Override
            public Mono<RegistrationOtpCreationResult> createOtp(RegistrationContactType type, String destination) {
                creates.incrementAndGet();
                return Mono.empty();
            }
            @Override
            public Mono<RegistrationOtpCreationResult> resendOtp(String challengeId, RegistrationContactType type, String destination) {
                resends.incrementAndGet();
                return Mono.empty();
            }
            @Override
            public Mono<RegistrationOtpVerificationResult> verifyOtp(String challengeId, String code) {
                verifies.incrementAndGet();
                return Mono.just(RegistrationOtpVerificationResult.invalid(4));
            }
        };
        var adapter = new RegistrationAuthFlowOtpCompatibilityAdapter(delegate);

        StepVerifier.create(adapter.create(RegistrationContactType.EMAIL, "member@example.com")).verifyComplete();
        StepVerifier.create(adapter.resend("challenge", RegistrationContactType.EMAIL, "member@example.com")).verifyComplete();
        StepVerifier.create(adapter.verify("challenge", "123456"))
                .assertNext(result -> assertThat(result.remainingAttempts()).isEqualTo(4))
                .verifyComplete();

        assertThat(creates).hasValue(1);
        assertThat(resends).hasValue(1);
        assertThat(verifies).hasValue(1);
    }

    @Test
    void messageCompatibilityAdapterDelegatesExactlyOnce() {
        AtomicInteger deliveries = new AtomicInteger();
        RegistrationMessagePort delegate = (type, recipient, code, ttl, language, correlationId) -> {
            deliveries.incrementAndGet();
            return Mono.just(true);
        };
        var adapter = new RegistrationAuthFlowMessageCompatibilityAdapter(delegate);

        StepVerifier.create(adapter.deliver(
                        RegistrationContactType.MOBILE,
                        "91234567",
                        "123456",
                        300,
                        "en",
                        "request-1"))
                .expectNext(true)
                .verifyComplete();
        assertThat(deliveries).hasValue(1);
    }
}
