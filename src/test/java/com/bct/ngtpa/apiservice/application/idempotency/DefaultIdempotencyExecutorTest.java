package com.bct.ngtpa.apiservice.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
  void unknownOutcomeExistingDoesNotExecuteOrMutatePersistence() {
    FakePort port = new FakePort(IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING);
    AtomicInteger protectedCalls = new AtomicInteger();
    AtomicInteger classifierCalls = new AtomicInteger();

    IdempotentExecutionResult result =
        executor(port)
            .execute(
                command(
                    () -> {
                      protectedCalls.incrementAndGet();
                      return Mono.just(response());
                    },
                    failure -> {
                      classifierCalls.incrementAndGet();
                      return IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT;
                    }))
            .block();

    assertThat(result.lifecycleOutcome())
        .isEqualTo(IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING);
    assertThat(protectedCalls).hasValue(0);
    assertThat(classifierCalls).hasValue(0);
    assertThat(port.reserveCalls).isOne();
    assertThat(port.completed).isZero();
    assertThat(port.failed).isZero();
    assertThat(port.unknown).isZero();
  }

  @Test
  void replayCompletedLoadsAndHydratesMatchingRecordWithoutReserving() {
    FakePort port = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    port.loadedRecord =
        idempotencyRecord(
            operation(),
            fingerprint(),
            IdempotencyStatus.COMPLETED,
            snapshot(),
            NOW.plusSeconds(300));
    AtomicInteger hydrationCalls = new AtomicInteger();

    Optional<IdempotentExecutionResult> result =
        executor(port)
            .replayCompleted(
                operation(),
                fingerprint(),
                terminal -> {
                  hydrationCalls.incrementAndGet();
                  assertThat(terminal).isEqualTo(snapshot());
                  return Mono.just(response());
                })
            .block();

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().lifecycleOutcome())
        .isEqualTo(IdempotencyReservationOutcome.COMPLETED_REPLAY);
    assertThat(result.orElseThrow().terminalResponse()).isEqualTo(response());
    assertThat(port.loadCalls).isOne();
    assertThat(hydrationCalls).hasValue(1);
    assertNoLifecycleMutation(port);
  }

  @Test
  void replayCompletedDoesNotReserveWhenRecordIsMissingOrExpired() {
    FakePort missing = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    assertThat(
            executor(missing)
                .replayCompleted(operation(), fingerprint(), terminal -> Mono.just(response()))
                .block())
        .isEmpty();
    assertNoLifecycleMutation(missing);

    FakePort expired = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    expired.loadedRecord =
        idempotencyRecord(operation(), fingerprint(), IdempotencyStatus.COMPLETED, snapshot(), NOW);
    assertThat(
            executor(expired)
                .replayCompleted(operation(), fingerprint(), terminal -> Mono.just(response()))
                .block())
        .isEmpty();
    assertNoLifecycleMutation(expired);
  }

  @Test
  void replayCompletedRejectsProcessingFailedAndUnknownOutcomeRecords() {
    for (IdempotencyStatus status :
        List.of(
            IdempotencyStatus.PROCESSING,
            IdempotencyStatus.FAILED,
            IdempotencyStatus.UNKNOWN_OUTCOME)) {
      FakePort port = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
      port.loadedRecord =
          idempotencyRecord(
              operation(), fingerprint(), status, null, NOW.plusSeconds(300));

      assertThat(
              executor(port)
                  .replayCompleted(
                      operation(),
                      fingerprint(),
                      terminal -> {
                        throw new AssertionError("ineligible record must not be hydrated");
                      })
                  .block())
          .as("status %s", status)
          .isEmpty();
      assertNoLifecycleMutation(port);
    }
  }

  @Test
  void replayCompletedRejectsOperationIdentityAndFingerprintMismatch() {
    FakePort differentOperation = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    differentOperation.loadedRecord =
        idempotencyRecord(
            new IdempotencyOperationId(
                operation().capability(),
                operation().action(),
                new IdempotencyOperationScope("otherScope01"),
                operation().key()),
            fingerprint(),
            IdempotencyStatus.COMPLETED,
            snapshot(),
            NOW.plusSeconds(300));
    assertThat(
            executor(differentOperation)
                .replayCompleted(
                    operation(),
                    fingerprint(),
                    terminal -> {
                      throw new AssertionError("mismatched operation must not be hydrated");
                    })
                .block())
        .isEmpty();
    assertNoLifecycleMutation(differentOperation);

    FakePort differentFingerprint = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    differentFingerprint.loadedRecord =
        idempotencyRecord(
            operation(),
            new IdempotencyFingerprint(1, "b".repeat(64)),
            IdempotencyStatus.COMPLETED,
            snapshot(),
            NOW.plusSeconds(300));
    assertThat(
            executor(differentFingerprint)
                .replayCompleted(
                    operation(),
                    fingerprint(),
                    terminal -> {
                      throw new AssertionError("mismatched fingerprint must not be hydrated");
                    })
                .block())
        .isEmpty();
    assertNoLifecycleMutation(differentFingerprint);
  }

  @Test
  void replayCompletedPropagatesStorageFailureWithoutMutation() {
    FakePort port = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    IllegalStateException failure = new IllegalStateException("storage failure");
    port.loadFailure = failure;

    assertThatThrownBy(
            () ->
                executor(port)
                    .replayCompleted(operation(), fingerprint(), terminal -> Mono.just(response()))
                    .block())
        .isSameAs(failure);
    assertNoLifecycleMutation(port);
  }

  @Test
  void replayCompletedPropagatesHydrationFailureWithoutMutation() {
    FakePort port = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    port.loadedRecord =
        idempotencyRecord(
            operation(),
            fingerprint(),
            IdempotencyStatus.COMPLETED,
            snapshot(),
            NOW.plusSeconds(300));
    IllegalArgumentException failure = new IllegalArgumentException("decode failure");

    assertThatThrownBy(
            () ->
                executor(port)
                    .replayCompleted(operation(), fingerprint(), terminal -> Mono.error(failure))
                    .block())
        .isSameAs(failure);
    assertNoLifecycleMutation(port);
  }

  @Test
  void replayCompletedTreatsEmptyHydrationAsCodecFailure() {
    FakePort port = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    port.loadedRecord =
        idempotencyRecord(
            operation(),
            fingerprint(),
            IdempotencyStatus.COMPLETED,
            snapshot(),
            NOW.plusSeconds(300));

    assertThatThrownBy(
            () ->
                executor(port)
                    .replayCompleted(operation(), fingerprint(), terminal -> Mono.empty())
                    .block())
        .isInstanceOf(IdempotencyExecutionException.class)
        .hasMessage("Shared idempotency replay hydration failed");
    assertNoLifecycleMutation(port);
  }

  @Test
  void replayCompletedRejectsEmptyLoadPublisher() {
    FakePort port = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    port.emptyLoadPublisher = true;

    assertThatThrownBy(
            () ->
                executor(port)
                    .replayCompleted(operation(), fingerprint(), terminal -> Mono.just(response()))
                    .block())
        .isInstanceOf(IdempotencyExecutionException.class)
        .hasMessage("Shared idempotency load returned no result");
    assertNoLifecycleMutation(port);
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
                        failure -> {
                          completion.classifierInvocations++;
                          return IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
                        }))
                .block());
    assertThat(completion.classifierInvocations).isZero();
    assertThat(completion.unknown).isEqualTo(1);
    assertThat(completion.failed).isZero();
  }

  @Test
  void asynchronousCaptureFailureAndEmptyCaptureBecomeUnknownOutcome() {
    FakePort failed = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    var failedCommand =
        new IdempotentExecutionCommand(
            operation(),
            fingerprint(),
            Duration.ofMinutes(5),
            () -> Mono.just(response()),
            failure -> {
              failed.classifierInvocations++;
              return IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
            },
            response -> Mono.error(new IllegalStateException("async capture")),
            snapshot -> Mono.just(response()));
    assertThatThrownBy(() -> executor(failed).execute(failedCommand).block())
        .isInstanceOf(IllegalStateException.class);
    assertThat(failed.classifierInvocations).isZero();
    assertThat(failed.unknown).isEqualTo(1);
    assertThat(failed.failed).isZero();

    FakePort empty = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    var emptyCommand =
        new IdempotentExecutionCommand(
            operation(),
            fingerprint(),
            Duration.ofMinutes(5),
            () -> Mono.just(response()),
            failure -> {
              empty.classifierInvocations++;
              return IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
            },
            response -> Mono.empty(),
            snapshot -> Mono.just(response()));
    assertThatThrownBy(() -> executor(empty).execute(emptyCommand).block())
        .isInstanceOf(IdempotencyExecutionException.class);
    assertThat(empty.classifierInvocations).isZero();
    assertThat(empty.unknown).isEqualTo(1);
    assertThat(empty.failed).isZero();
  }

  @Test
  void failedUnknownPersistenceNeverFallsThroughToFailedTransition() {
    FakePort port = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    port.failMarkUnknown = true;
    var command =
        new IdempotentExecutionCommand(
            operation(),
            fingerprint(),
            Duration.ofMinutes(5),
            () -> Mono.just(response()),
            failure -> {
              port.classifierInvocations++;
              return IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
            },
            response -> Mono.error(new IllegalStateException("capture failure")),
            snapshot -> Mono.just(response()));

    assertThatThrownBy(() -> executor(port).execute(command).block())
        .isInstanceOf(IdempotencyExecutionException.class)
        .hasMessage("Shared idempotency unknown-outcome persistence failed");
    assertThat(port.unknown).isEqualTo(1);
    assertThat(port.failed).isZero();
    assertThat(port.classifierInvocations).isZero();
  }

  @Test
  void protectedOperationFailureUsesClassifierExactlyOnce() {
    FakePort port = new FakePort(IdempotencyReservationOutcome.ACQUIRED);
    var command =
        command(
            () -> Mono.error(new IllegalStateException("protected failure")),
            failure -> {
              port.classifierInvocations++;
              return IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
            });

    assertThatThrownBy(() -> executor(port).execute(command).block())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("protected failure");
    assertThat(port.classifierInvocations).isEqualTo(1);
    assertThat(port.failed).isEqualTo(1);
    assertThat(port.unknown).isZero();
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
        r -> Mono.just(new IdempotentCapturedResponse(r, snapshot())),
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

  private static IdempotencyRecord idempotencyRecord(
      IdempotencyOperationId operationId,
      IdempotencyFingerprint fingerprint,
      IdempotencyStatus status,
      IdempotentTerminalResponseSnapshot terminal,
      Instant expiresAt) {
    return new IdempotencyRecord(
        operationId,
        fingerprint,
        new IdempotencyReservationToken("reservationToken0123456789abcdefgh"),
        status,
        terminal,
        NOW.minusSeconds(60),
        NOW.minusSeconds(30),
        expiresAt);
  }

  private static void assertNoLifecycleMutation(FakePort port) {
    assertThat(port.reserveCalls).isZero();
    assertThat(port.completed).isZero();
    assertThat(port.failed).isZero();
    assertThat(port.unknown).isZero();
  }

  static final class FakePort implements IdempotencyPort {
    final IdempotencyReservationOutcome outcome;
    int completed;
    int failed;
    int unknown;
    int classifierInvocations;
    int loadCalls;
    int reserveCalls;
    boolean failComplete;
    boolean failMarkUnknown;
    boolean emptyLoadPublisher;
    Throwable loadFailure;
    IdempotencyRecord loadedRecord;

    FakePort(IdempotencyReservationOutcome outcome) {
      this.outcome = outcome;
    }

    public Mono<IdempotencyReservation> reserve(IdempotencyCommands.Reserve c) {
      reserveCalls++;
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
      if (failMarkUnknown) {
        return Mono.error(new IllegalStateException("unknown persistence"));
      }
      return Mono.just(record(IdempotencyStatus.UNKNOWN_OUTCOME, null, c.reservationToken()));
    }

    public Mono<Optional<IdempotencyRecord>> load(IdempotencyOperationId o) {
      loadCalls++;
      if (loadFailure != null) return Mono.error(loadFailure);
      if (emptyLoadPublisher) return Mono.empty();
      return Mono.just(Optional.ofNullable(loadedRecord));
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
