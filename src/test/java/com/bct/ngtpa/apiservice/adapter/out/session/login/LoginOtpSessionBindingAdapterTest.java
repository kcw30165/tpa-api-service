package com.bct.ngtpa.apiservice.adapter.out.session.login;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.*;
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

class LoginOtpSessionBindingAdapterTest {
    @Test
    void bindsChallengeThenMarksOtpSent() {
        var current = new AtomicReference<>(new AuthFlowSession(new AuthFlowSessionId("afs_opaque"),
                AuthFlowScenario.LOGIN, AuthFlowStatus.STARTED, "hash", null, AuthFlowSessionData.empty(),
                Instant.EPOCH, Instant.EPOCH, Instant.now().plusSeconds(300)));
        AuthFlowSessionPort port = new AuthFlowSessionPort() {
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
        var lifecycle = new AuthFlowSessionLifecycleService(port, c -> null, new SessionTtlCalculator(),
                Clock.systemUTC());
        StepVerifier.create(new LoginOtpSessionBindingAdapter(lifecycle)
                .bind(new LoginOtpSessionBindingCommand("afs_opaque", "otp_opaque", "req"))).verifyComplete();
        assertThat(current.get().activeOtpChallengeId()).isEqualTo("otp_opaque");
        assertThat(current.get().status()).isEqualTo(AuthFlowStatus.OTP_SENT);
    }
}
