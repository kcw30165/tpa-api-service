package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSendIdempotencyPort;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdempotentLoginOtpResendServiceTest {
  static final ResendLoginOtpCommand C =
      new ResendLoginOtpCommand(
          "afs_secret", "csrf_secret", "idem_key_123", "https://portal", null, "en", "req");

  @Test
  void startedInvokesDelegateAndCompletesSafeReplay() {
    var calls = new AtomicInteger();
    var p = new Port(LoginOtpSendReservationOutcome.STARTED);
    ResendLoginOtpUseCase d =
        c -> {
          calls.incrementAndGet();
          return Mono.just(
              new ResendLoginOtpResult(ResendLoginOtpStatus.RESENT, "****4567", 300, 60, null));
        };
    var s =
        new IdempotentLoginOtpResendService(
            d,
            p,
            new LoginOtpResendRequestHashGenerator(),
            Clock.systemUTC(),
            Duration.ofSeconds(300));
    StepVerifier.create(s.resend(C))
        .assertNext(r -> assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.RESENT))
        .verifyComplete();
    assertThat(calls.get()).isOne();
    assertThat(p.completed.replay().toString())
        .doesNotContain("afs_secret", "csrf_secret", "91234567");
  }

  @Test
  void completedReplayHasNoSideEffects() {
    var p = new Port(LoginOtpSendReservationOutcome.COMPLETED_REPLAY);
    p.replay = new LoginOtpSendReplaySnapshot("****4567", 300, 60);
    var s =
        new IdempotentLoginOtpResendService(
            c -> Mono.error(new AssertionError()),
            p,
            new LoginOtpResendRequestHashGenerator(),
            Clock.systemUTC(),
            Duration.ofSeconds(300));
    StepVerifier.create(s.resend(C))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.COMPLETED_REPLAY);
              assertThat(r.maskedDestination()).isEqualTo("****4567");
            })
        .verifyComplete();
  }

  static final class Port implements LoginOtpSendIdempotencyPort {
    final LoginOtpSendReservationOutcome outcome;
    LoginOtpSendReplaySnapshot replay;
    LoginOtpSendCompleteCommand completed;

    Port(LoginOtpSendReservationOutcome o) {
      outcome = o;
    }

    public Mono<LoginOtpSendReservation> reserve(LoginOtpSendReserveCommand c) {
      assertThat(c.action()).isEqualTo(LoginIdempotencyAction.OTP_RESEND);
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
