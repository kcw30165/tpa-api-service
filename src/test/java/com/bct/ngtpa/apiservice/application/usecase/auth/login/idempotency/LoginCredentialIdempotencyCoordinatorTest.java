package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginCredentialIdempotencyPort;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginCredentialIdempotencyCoordinatorTest {
  private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

  @Test
  void reservesCredentialSubmissionWithConfiguredTtl() {
    RecordingPort port = new RecordingPort(LoginIdempotencyStartOutcome.STARTED);
    var coordinator =
        new LoginCredentialIdempotencyCoordinator(
            port, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(300));

    StepVerifier.create(
            coordinator.reserve(
                new LoginIdempotencyKey("login_key_opaque"),
                new LoginCredentialRequestHash("a".repeat(64))))
        .assertNext(
            result -> assertThat(result.outcome()).isEqualTo(LoginIdempotencyStartOutcome.STARTED))
        .verifyComplete();
    assertThat(port.lastStart.ttl()).isEqualTo(Duration.ofSeconds(300));
    assertThat(port.lastStart.action()).isEqualTo(LoginIdempotencyAction.CREDENTIALS);
  }

  @Test
  void exposesReplayConflictAndInProgressWithoutExecutingACallback() {
    for (LoginIdempotencyStartOutcome outcome :
        List.of(
            LoginIdempotencyStartOutcome.COMPLETED_REPLAY,
            LoginIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
            LoginIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS,
            LoginIdempotencyStartOutcome.FAILED_EXISTING,
            LoginIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING)) {
      var coordinator =
          new LoginCredentialIdempotencyCoordinator(
              new RecordingPort(outcome),
              Clock.fixed(NOW, ZoneOffset.UTC),
              Duration.ofSeconds(300));
      StepVerifier.create(
              coordinator.reserve(
                  new LoginIdempotencyKey("login_key_opaque"),
                  new LoginCredentialRequestHash("b".repeat(64))))
          .assertNext(result -> assertThat(result.outcome()).isEqualTo(outcome))
          .verifyComplete();
    }
  }

  private static final class RecordingPort implements LoginCredentialIdempotencyPort {
    private final LoginIdempotencyStartOutcome outcome;
    private LoginIdempotencyStartCommand lastStart;

    RecordingPort(LoginIdempotencyStartOutcome outcome) {
      this.outcome = outcome;
    }

    public Mono<LoginIdempotencyStartResult> startProcessing(LoginIdempotencyStartCommand command) {
      lastStart = command;
      var record =
          new LoginIdempotencyRecord(
              command.action(),
              command.idempotencyKey(),
              command.requestHash(),
              LoginIdempotencyStatus.PROCESSING,
              null,
              command.now(),
              command.now(),
              command.expiresAt());
      return Mono.just(new LoginIdempotencyStartResult(outcome, record));
    }

    public Mono<LoginIdempotencyRecord> markCompleted(LoginIdempotencyCompleteCommand c) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<LoginIdempotencyRecord> markFailed(LoginIdempotencyFailCommand c) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<LoginIdempotencyRecord> markUnknownOutcome(
        LoginIdempotencyUnknownOutcomeCommand c) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<Optional<LoginIdempotencyRecord>> load(
        LoginIdempotencyAction a, LoginIdempotencyKey k) {
      return Mono.just(Optional.empty());
    }

    public Mono<Void> delete(LoginIdempotencyAction a, LoginIdempotencyKey k) {
      return Mono.empty();
    }
  }
}
