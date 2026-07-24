package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.in.SendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSendIdempotencyPort;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdempotentLoginOtpSendServiceTest {
    static final SendLoginOtpCommand COMMAND = new SendLoginOtpCommand("afs_secret", "csrf_secret", "dst_secret",
            "idem_key_123", "https://portal", null, "en", "req");

    @Test
    void onlyStartedReservationInvokesDelegateAndStoresSafeReplay() {
        var calls = new AtomicInteger();
        var port = new MemoryPort(LoginOtpSendReservationOutcome.STARTED);
        SendLoginOtpUseCase delegate = c -> {
            calls.incrementAndGet();
            return Mono.just(new SendLoginOtpResult(SendLoginOtpStatus.SENT, "****4567", 300, 60, null));
        };
        var service = new IdempotentLoginOtpSendService(delegate, port, new LoginOtpSendRequestHashGenerator(),
                Clock.systemUTC(), Duration.ofSeconds(300));
        StepVerifier.create(service.send(COMMAND))
                .assertNext(r -> assertThat(r.status()).isEqualTo(SendLoginOtpStatus.SENT)).verifyComplete();
        assertThat(calls.get()).isOne();
        assertThat(port.completed.toString()).doesNotContain("afs_secret", "csrf_secret", "dst_secret", "91234567");
    }

    @Test
    void completedReplayHasNoSideEffects() {
        var port = new MemoryPort(LoginOtpSendReservationOutcome.COMPLETED_REPLAY);
        port.replay = new LoginOtpSendReplaySnapshot("****4567", 300, 60);
        var service = new IdempotentLoginOtpSendService(c -> Mono.error(new AssertionError("delegate must not run")),
                port, new LoginOtpSendRequestHashGenerator(), Clock.systemUTC(), Duration.ofSeconds(300));
        StepVerifier.create(service.send(COMMAND)).assertNext(r -> {
            assertThat(r.status()).isEqualTo(SendLoginOtpStatus.COMPLETED_REPLAY);
            assertThat(r.maskedDestination()).isEqualTo("****4567");
        }).verifyComplete();
    }

    @Test
    void mapsNonStartedReservationsWithoutSideEffects() {
        var expected = java.util.Map.of(LoginOtpSendReservationOutcome.DUPLICATE_IN_PROGRESS,
                SendLoginOtpStatus.DUPLICATE_IN_PROGRESS, LoginOtpSendReservationOutcome.KEY_REUSED,
                SendLoginOtpStatus.KEY_REUSED, LoginOtpSendReservationOutcome.FAILED_EXISTING,
                SendLoginOtpStatus.FAILED_EXISTING, LoginOtpSendReservationOutcome.UNKNOWN_OUTCOME,
                SendLoginOtpStatus.UNKNOWN_OUTCOME);
        expected.forEach((outcome, status) -> {
            var service = new IdempotentLoginOtpSendService(c -> Mono.error(new AssertionError()),
                    new MemoryPort(outcome), new LoginOtpSendRequestHashGenerator(), Clock.systemUTC(),
                    Duration.ofSeconds(300));
            assertThat(service.send(COMMAND).block().status()).isEqualTo(status);
        });
    }

    @Test
    void requestHashContainsNoRecoverableSensitiveInput() {
        var hash = new LoginOtpSendRequestHashGenerator().generate(COMMAND);
        assertThat(hash.value()).matches("^[a-f0-9]{64}$");
        assertThat(hash.toString()).doesNotContain("afs_secret", "csrf_secret", "dst_secret");
    }

    static final class MemoryPort implements LoginOtpSendIdempotencyPort {
        final LoginOtpSendReservationOutcome outcome;
        LoginOtpSendReplaySnapshot replay;
        LoginOtpSendCompleteCommand completed;

        MemoryPort(LoginOtpSendReservationOutcome o) {
            outcome = o;
        }

        public Mono<LoginOtpSendReservation> reserve(LoginOtpSendReserveCommand c) {
            return Mono.just(new LoginOtpSendReservation(outcome, replay));
        }

        public Mono<Void> complete(LoginOtpSendCompleteCommand c) {
            completed = c;
            return Mono.empty();
        }

        public Mono<Void> fail(LoginOtpSendTerminalCommand c) {
            return Mono.empty();
        }

        public Mono<Void> unknown(LoginOtpSendTerminalCommand c) {
            return Mono.empty();
        }
    }
}
