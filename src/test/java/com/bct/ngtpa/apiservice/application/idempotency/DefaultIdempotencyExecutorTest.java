package com.bct.ngtpa.apiservice.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultIdempotencyExecutorTest {
  private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

  @Test
  void acquiredExecutesCapturesCompletesAndReturnsFirstResponse() {
    FakePort p = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    AtomicInteger calls = new AtomicInteger();
    var result =
        executor(p)
            .execute(
                command(
                    () -> {
                      calls.incrementAndGet();
                      return Mono.just(response());
                    },
                    failure -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT))
            .block();
    assertThat(result.lifecycleOutcome()).isEqualTo(IdempotencyReservationOutcome.ACQUIRED);
    assertThat(result.terminalResponse()).isEqualTo(response());
    assertThat(calls).hasValue(1);
    assertThat(p.completed).isEqualTo(1);
  }

  @Test
  void completedReplayHydratesWithoutExecuting() {
    FakePort p = new FakePort(IdempotencyReservationOutcome.COMPLETED_REPLAY);
    AtomicInteger calls = new AtomicInteger();
    var result =
        executor(p)
            .execute(
                command(
                    () -> {
                      calls.incrementAndGet();
                      return Mono.just(response());
                    },
                    failure -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT))
            .block();
    assertThat(result.lifecycleOutcome()).isEqualTo(IdempotencyReservationOutcome.COMPLETED_REPLAY);
    assertThat(calls).hasValue(0);
  }

  @Test
  void provenNoSideEffectMarksFailedAndPossibleSideEffectMarksUnknown() {
    FakePort safe = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    assertThatThrownBy(
            () ->
                executor(safe)
                    .execute(
                        command(
                            () -> Mono.error(new IllegalStateException("safe")),
                            failure -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT))
                    .block())
        .isInstanceOf(IllegalStateException.class);
    assertThat(safe.failed).isEqualTo(1);
    assertThat(safe.unknown).isZero();
    FakePort uncertain = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    assertThatThrownBy(
            () ->
                executor(uncertain)
                    .execute(
                        command(
                            () -> Mono.error(new IllegalStateException("uncertain")),
                            failure -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT))
                    .block())
        .isInstanceOf(IllegalStateException.class);
    assertThat(uncertain.unknown).isEqualTo(1);
    assertThat(uncertain.failed).isZero();
  }

  @Test
  void classifierFailureAndCompletionFailureFailClosedToUnknown() {
    FakePort classifier = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    assertThatThrownBy(
        () ->
            executor(classifier)
                .execute(
                    command(
                        () -> Mono.error(new IllegalStateException("operation")),
                        failure -> {
                          throw new IllegalArgumentException("classifier");
                        }))
                .block());
    assertThat(classifier.unknown).isEqualTo(1);
    FakePort completion = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    completion.failComplete = true;
    assertThatThrownBy(
        () ->
            executor(completion)
                .execute(
                    command(
                        () -> Mono.just(response()),
                        failure -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT))
                .block());
    assertThat(completion.unknown).isEqualTo(1);
  }

  private static DefaultIdempotencyExecutor executor(FakePort p) {
    return new DefaultIdempotencyExecutor(
        p,
        () -> new IdempotencyReservationToken("reservationToken0123456789abcdefgh"),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static IdempotentExecutionCommand command(
      java.util.function.Supplier<Mono<HydratedIdempotentResponse>> op,
      IdempotencyFailureClassifier classifier) {
    return new IdempotentExecutionCommand(
        operation(),
        fingerprint(),
        Duration.ofMinutes(5),
        op,
        classifier,
        r -> new IdempotentCapturedResponse(r, snapshot()),
        s -> Mono.just(response()));
  }

  private static HydratedIdempotentResponse response() {
    return new HydratedIdempotentResponse(200, "application/json", "{}", null);
  }

  private static IdempotentTerminalResponseSnapshot snapshot() {
    return new IdempotentTerminalResponseSnapshot(1, 200, "application/json", "{}", null, null);
  }

  private static IdempotencyOperationId operation() {
    return new IdempotencyOperationId(
        new IdempotencyCapability("profile"),
        new IdempotencyAction("update-details"),
        new IdempotencyOperationScope("opaqueScope01"),
        new IdempotencyKey("requestKey01"));
  }

  private static IdempotencyFingerprint fingerprint() {
    return new IdempotencyFingerprint(1, "a".repeat(64));
  }

  static final class FakePort implements IdempotencyPort {
    final IdempotencyReservationOutcome outcome;
    int completed;
    int failed;
    int unknown;
    boolean failComplete;

    FakePort(IdempotencyReservationOutcome outcome) {
      this.outcome = outcome;
    }

    public Mono<IdempotencyReservation> reserve(IdempotencyCommands.Reserve c) {
      return Mono.just(
          switch (outcome) {
            case ACQUIRED ->
                new IdempotencyReservation(
                    outcome, IdempotencyStatus.PROCESSING, null, c.reservationToken());
            case COMPLETED_REPLAY ->
                new IdempotencyReservation(outcome, IdempotencyStatus.COMPLETED, snapshot(), null);
            case DUPLICATE_IN_PROGRESS ->
                new IdempotencyReservation(outcome, IdempotencyStatus.PROCESSING, null, null);
            case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
                new IdempotencyReservation(outcome, IdempotencyStatus.PROCESSING, null, null);
            case FAILED_EXISTING ->
                new IdempotencyReservation(outcome, IdempotencyStatus.FAILED, null, null);
            case UNKNOWN_OUTCOME_EXISTING ->
                new IdempotencyReservation(outcome, IdempotencyStatus.UNKNOWN_OUTCOME, null, null);
          });
    }

    public Mono<IdempotencyRecord> complete(IdempotencyCommands.Complete c) {
      if (failComplete) return Mono.error(new IllegalStateException("complete"));
      completed++;
      return Mono.just(
          record(IdempotencyStatus.COMPLETED, c.terminalResponse(), c.reservationToken()));
    }

    public Mono<IdempotencyRecord> fail(IdempotencyCommands.Fail c) {
      failed++;
      return Mono.just(record(IdempotencyStatus.FAILED, null, c.reservationToken()));
    }

    public Mono<IdempotencyRecord> markUnknown(IdempotencyCommands.MarkUnknown c) {
      unknown++;
      return Mono.just(record(IdempotencyStatus.UNKNOWN_OUTCOME, null, c.reservationToken()));
    }

    public Mono<Optional<IdempotencyRecord>> load(IdempotencyOperationId o) {
      return Mono.just(Optional.empty());
    }

    private IdempotencyRecord record(
        IdempotencyStatus s,
        IdempotentTerminalResponseSnapshot snap,
        IdempotencyReservationToken t) {
      return new IdempotencyRecord(
          operation(), fingerprint(), t, s, snap, NOW, NOW, NOW.plusSeconds(300));
    }
  }
}
