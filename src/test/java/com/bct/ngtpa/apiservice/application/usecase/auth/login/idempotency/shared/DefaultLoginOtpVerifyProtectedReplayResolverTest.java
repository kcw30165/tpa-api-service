package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.RecoverCommittedLoginResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultLoginOtpVerifyProtectedReplayResolverTest {
  private static final String HANDOFF = "0123456789abcdef0123456789abcdef";
  private static final AuthenticatedSessionId SESSION =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void delegatesExactOpaqueHandoffAndRestoresAccountsReady() {
    AtomicInteger recoveries = new AtomicInteger();
    var resolver =
        new DefaultLoginOtpVerifyProtectedReplayResolver(
            command -> {
              recoveries.incrementAndGet();
              assertThat(command.handoffReference().value()).isEqualTo(HANDOFF);
              assertThat(command.correlationId()).isEqualTo("req-1");
              return Mono.just(
                  new RecoverCommittedLoginResult(
                      true, List.of(account()), SESSION, "fresh_authenticated_csrf"));
            });

    StepVerifier.create(resolver.resolve(new ProtectedReplayReference(HANDOFF), "req-1"))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(VerifyLoginOtpStatus.ACCOUNTS_READY);
              assertThat(result.clearAuthFlowCookie()).isTrue();
              assertThat(result.accounts()).containsExactly(account());
              assertThat(result.authenticatedSessionId()).isEqualTo(SESSION);
              assertThat(result.authenticatedCsrfToken()).isEqualTo("fresh_authenticated_csrf");
            })
        .verifyComplete();
    assertThat(recoveries.get()).isEqualTo(1);
  }

  @Test
  void malformedReferenceFailsBeforeRecovery() {
    AtomicInteger recoveries = new AtomicInteger();
    var resolver =
        new DefaultLoginOtpVerifyProtectedReplayResolver(
            command -> {
              recoveries.incrementAndGet();
              return Mono.just(RecoverCommittedLoginResult.failed());
            });

    StepVerifier.create(resolver.resolve(null, "req"))
        .expectError(IllegalArgumentException.class)
        .verify();
    StepVerifier.create(
            resolver.resolve(new ProtectedReplayReference("opaque-ref-12345678"), "req"))
        .expectError(IllegalArgumentException.class)
        .verify();
    assertThat(recoveries.get()).isZero();
  }

  @Test
  void failedProtectedRecoveryFailsClosed() {
    var resolver =
        new DefaultLoginOtpVerifyProtectedReplayResolver(
            command -> Mono.just(RecoverCommittedLoginResult.failed()));
    StepVerifier.create(resolver.resolve(new ProtectedReplayReference(HANDOFF), "req"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  private LoginSessionAccount account() {
    return new LoginSessionAccount(
        new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
        "DB",
        "12",
        "00000000012",
        "1",
        "0001",
        "TR",
        "OE",
        null,
        null);
  }
}
