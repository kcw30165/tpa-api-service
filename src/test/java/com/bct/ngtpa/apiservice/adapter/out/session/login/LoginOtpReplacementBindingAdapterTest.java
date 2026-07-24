package com.bct.ngtpa.apiservice.adapter.out.session.login;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.session.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpReplacementBindingAdapterTest {
    @Test
    void replacesOnlyExpectedActiveChallengeAndPreservesOtpSent() {
        var current = new AtomicReference<>(new AuthFlowSession(new AuthFlowSessionId("afs"), AuthFlowScenario.LOGIN,
                AuthFlowStatus.OTP_SENT, "hash", "old", AuthFlowSessionData.empty(), Instant.EPOCH, Instant.EPOCH,
                Instant.now().plusSeconds(300)));
        AuthFlowSessionPort p = new AuthFlowSessionPort() {
            public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand c) {
                return Mono.error(new UnsupportedOperationException());
            }

            public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
                return Mono.just(Optional.of(current.get()));
            }

            public Mono<AuthFlowSession> update(AuthFlowSession s) {
                current.set(s);
                return Mono.just(s);
            }

            public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant n) {
                return Mono.just(current.get());
            }

            public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant n) {
                return Mono.just(current.get());
            }

            public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant n) {
                return Mono.just(current.get());
            }

            public Mono<Void> delete(AuthFlowSessionId id) {
                return Mono.empty();
            }
        };
        var lifecycle = new AuthFlowSessionLifecycleService(p, c -> null, new SessionTtlCalculator(),
                Clock.systemUTC());
        StepVerifier.create(new LoginOtpReplacementBindingAdapter(lifecycle)
                .bind(new LoginOtpReplacementBindingCommand("afs", "old", "new", "req"))).verifyComplete();
        assertThat(current.get().activeOtpChallengeId()).isEqualTo("new");
        assertThat(current.get().status()).isEqualTo(AuthFlowStatus.OTP_SENT);
    }
}
