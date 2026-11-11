package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.CancelInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancellation;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelNotCancellableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceCancelPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CancelInvestmentChoiceServiceTest {

  private static final String ACTOR_USER_ID = "actor-user-sensitive";
  private static final String ACCOUNT_ENV = "SIT";
  private static final String POLICY_NO = "policy-sensitive";
  private static final String CERT_NO = "cert-sensitive";
  private static final String SNAPSHOT_ACCOUNT_REF = "snapshot-account-sensitive";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_session-sensitive-0123456789");
  private static final String REQUEST_ID = "request-safe";
  private static final String REFERENCE_NO = "260001449";
  private static final String OTHER_REFERENCE_NO = "260001450";
  private static final String ABSENT_REFERENCE_NO = "260001451";
  private static final InvestmentChoiceCancelNotCancellableException NOT_CANCELLABLE =
      new InvestmentChoiceCancelNotCancellableException();
  private static final InvestmentChoiceCancelUnavailableException UNAVAILABLE =
      new InvestmentChoiceCancelUnavailableException();

  private final InvestmentChoiceSnapshotCacheService snapshotCache =
      mock(InvestmentChoiceSnapshotCacheService.class);
  private final InvestmentChoiceCancelPort cancelPort = mock(InvestmentChoiceCancelPort.class);
  private final FailureEventPort failureEvents = mock(FailureEventPort.class);
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(CancelInvestmentChoiceService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void captureUseCaseLogs() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppenderAndCheckSensitiveValues() {
    try {
      assertThat(appender.list).isEmpty();
      String messages =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .collect(java.util.stream.Collectors.joining("\n"));
      assertThat(messages)
          .doesNotContain(
              ACTOR_USER_ID,
              POLICY_NO,
              CERT_NO,
              SNAPSHOT_ACCOUNT_REF,
              SESSION_ID.value(),
              REFERENCE_NO,
              OTHER_REFERENCE_NO,
              ABSENT_REFERENCE_NO);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void dispatchesBeforeRewritingSnapshotAfterCompletion() {
    when(snapshotCache.readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    InvestmentChoiceSnapshotState.normal(
                        snapshot(
                            List.of(REFERENCE_NO, OTHER_REFERENCE_NO, ABSENT_REFERENCE_NO))))));
    when(cancelPort.cancel(any()))
        .thenReturn(Mono.just(new InvestmentChoiceCancellation(REFERENCE_NO)));
    AtomicBoolean writeCompleted = new AtomicBoolean();
    when(snapshotCache.write(eq(SESSION_ID), any(), eq(REQUEST_ID)))
        .thenReturn(Mono.fromRunnable(() -> writeCompleted.set(true)));
    var service = service();

    StepVerifier.create(service.cancel(command(REFERENCE_NO)))
        .assertNext(
            outcome -> {
              assertThat(outcome)
                  .isEqualTo(
                      new InvestmentChoiceCancelOutcome.Cancelled(
                          new InvestmentChoiceCancellation(REFERENCE_NO)));
              assertThat(writeCompleted.get()).isTrue();
            })
        .verifyComplete();

    InOrder order = inOrder(cancelPort, snapshotCache);
    ArgumentCaptor<InvestmentChoiceCancelCommand> cancelCommand =
        ArgumentCaptor.forClass(InvestmentChoiceCancelCommand.class);
    order.verify(cancelPort).cancel(cancelCommand.capture());
    order.verify(snapshotCache).readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    assertThat(cancelCommand.getValue())
        .isEqualTo(
            new InvestmentChoiceCancelCommand(
                ACTOR_USER_ID,
                ActorUserRole.ROLE_USER_MBR,
                ACCOUNT_ENV,
                POLICY_NO,
                CERT_NO,
                REFERENCE_NO));
    ArgumentCaptor<InvestmentChoiceSnapshot> writtenSnapshot =
        ArgumentCaptor.forClass(InvestmentChoiceSnapshot.class);
    order.verify(snapshotCache).write(eq(SESSION_ID), writtenSnapshot.capture(), eq(REQUEST_ID));
    assertThat(writtenSnapshot.getValue().pendingRefNos())
        .containsExactly(OTHER_REFERENCE_NO, ABSENT_REFERENCE_NO);
    verify(snapshotCache, times(1))
        .readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verify(snapshotCache, times(1)).write(eq(SESSION_ID), any(), eq(REQUEST_ID));
    verify(failureEvents, never()).publish(any());
  }

  @Test
  void dispatchesBeforeBestEffortSnapshotCleanupAndSucceedsWhenSnapshotIsMissing() {
    when(cancelPort.cancel(any()))
        .thenReturn(Mono.just(new InvestmentChoiceCancellation(REFERENCE_NO)));
    when(snapshotCache.readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(Mono.just(Optional.empty()));

    StepVerifier.create(service().cancel(command(REFERENCE_NO)))
        .expectNext(
            new InvestmentChoiceCancelOutcome.Cancelled(
                new InvestmentChoiceCancellation(REFERENCE_NO)))
        .verifyComplete();

    InOrder order = inOrder(cancelPort, snapshotCache);
    order.verify(cancelPort).cancel(any());
    order.verify(snapshotCache).readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID);
    verify(snapshotCache, never()).write(any(), any(), any());
    verify(failureEvents, never()).publish(any());
  }

  @Test
  void restrictedSnapshotDoesNotBlockSuccessfulCancellationDuringCleanup() {
    when(cancelPort.cancel(any()))
        .thenReturn(Mono.just(new InvestmentChoiceCancellation(REFERENCE_NO)));
    when(snapshotCache.readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    InvestmentChoiceSnapshotState.restricted(
                        SNAPSHOT_ACCOUNT_REF,
                        POLICY_NO,
                        com.bct.ngtpa.apiservice.application.dto.investmentchoice
                            .InvestmentChoiceScenario.FUNCTION_UNAVAILABLE))));

    StepVerifier.create(service().cancel(command(REFERENCE_NO)))
        .expectNext(
            new InvestmentChoiceCancelOutcome.Cancelled(
                new InvestmentChoiceCancellation(REFERENCE_NO)))
        .verifyComplete();

    verify(snapshotCache, never()).write(any(), any(), any());
    verify(failureEvents, never()).publish(any());
  }

  @Test
  void repeatedExecutionDoesNotRevalidateSnapshotMembership() {
    AtomicReference<InvestmentChoiceSnapshotState> storedSnapshot =
        new AtomicReference<>(
            InvestmentChoiceSnapshotState.normal(
                snapshot(List.of(REFERENCE_NO, OTHER_REFERENCE_NO))));
    when(snapshotCache.readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenAnswer(ignored -> Mono.just(Optional.of(storedSnapshot.get())));
    when(snapshotCache.write(eq(SESSION_ID), any(), eq(REQUEST_ID)))
        .thenAnswer(
            invocation -> {
              storedSnapshot.set(
                  InvestmentChoiceSnapshotState.normal(
                      invocation.getArgument(1, InvestmentChoiceSnapshot.class)));
              return Mono.empty();
            });
    when(cancelPort.cancel(any()))
        .thenReturn(Mono.just(new InvestmentChoiceCancellation(REFERENCE_NO)));
    var service = service();

    StepVerifier.create(service.cancel(command(REFERENCE_NO)))
        .assertNext(
            outcome ->
                assertThat(outcome).isInstanceOf(InvestmentChoiceCancelOutcome.Cancelled.class))
        .verifyComplete();
    StepVerifier.create(service.cancel(command(REFERENCE_NO)))
        .assertNext(
            outcome ->
                assertThat(outcome)
                    .isEqualTo(
                        new InvestmentChoiceCancelOutcome.Cancelled(
                            new InvestmentChoiceCancellation(REFERENCE_NO))))
        .verifyComplete();

    verify(cancelPort, times(2)).cancel(any());
    verify(snapshotCache, times(2)).write(eq(SESSION_ID), any(), eq(REQUEST_ID));
    verify(failureEvents, never()).publish(any());
  }

  @ParameterizedTest
  @MethodSource("knownCancelFailures")
  void propagatesKnownFailuresWithoutPublishingOrWriting(RuntimeException failure) {
    when(snapshotCache.readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    InvestmentChoiceSnapshotState.normal(snapshot(List.of(REFERENCE_NO))))));
    when(cancelPort.cancel(any())).thenReturn(Mono.error(failure));

    StepVerifier.create(service().cancel(command(REFERENCE_NO)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();

    verify(snapshotCache, never()).write(any(), any(), any());
    verify(failureEvents, never()).publish(any());
  }

  static Stream<Arguments> knownCancelFailures() {
    return Stream.of(Arguments.of(NOT_CANCELLABLE), Arguments.of(UNAVAILABLE));
  }

  @Test
  void propagatesOtherFailureWithoutPublishingOrWriting() {
    RuntimeException failure = new IllegalStateException("unexpected failure");
    when(snapshotCache.readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    InvestmentChoiceSnapshotState.normal(snapshot(List.of(REFERENCE_NO))))));
    when(cancelPort.cancel(any())).thenReturn(Mono.error(failure));

    StepVerifier.create(service().cancel(command(REFERENCE_NO)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();

    verify(snapshotCache, never()).write(any(), any(), any());
    verify(failureEvents, never()).publish(any());
  }

  @Test
  void emptyCancelResultPublishesSafeFailureAndDoesNotRewriteSnapshot() {
    when(snapshotCache.readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    InvestmentChoiceSnapshotState.normal(snapshot(List.of(REFERENCE_NO))))));
    when(cancelPort.cancel(any())).thenReturn(Mono.empty());

    StepVerifier.create(service().cancel(command(REFERENCE_NO)))
        .expectError(InvestmentChoiceCancelUnavailableException.class)
        .verify();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    verify(failureEvents, times(1)).publish(event.capture());
    assertThat(event.getValue())
        .isEqualTo(
            new FailureEvent(
                "investment-choice.cancel.empty_result",
                REQUEST_ID,
                "INVESTMENT_CHOICE",
                "CANCEL",
                FailureOrigin.LOCAL_SYSTEM,
                "EMPTY_RESULT",
                "err.investment-choice.cancel.unavailable",
                503,
                false,
                Map.of("reason", "empty")));
    assertThat(event.getValue().toString())
        .doesNotContain(
            ACTOR_USER_ID,
            POLICY_NO,
            CERT_NO,
            SNAPSHOT_ACCOUNT_REF,
            SESSION_ID.value(),
            REFERENCE_NO,
            OTHER_REFERENCE_NO);
    verify(cancelPort, times(1)).cancel(any());
    verify(snapshotCache, never()).write(any(), any(), any());
  }

  @ParameterizedTest
  @MethodSource("snapshotWriteResults")
  void snapshotWriteSkipOrFailureDoesNotChangeCancellationResult(Mono<Void> writeResult) {
    when(snapshotCache.readState(SESSION_ID, SNAPSHOT_ACCOUNT_REF, POLICY_NO, REQUEST_ID))
        .thenReturn(
            Mono.just(
                Optional.of(
                    InvestmentChoiceSnapshotState.normal(snapshot(List.of(REFERENCE_NO))))));
    when(cancelPort.cancel(any()))
        .thenReturn(Mono.just(new InvestmentChoiceCancellation(REFERENCE_NO)));
    when(snapshotCache.write(eq(SESSION_ID), any(), eq(REQUEST_ID))).thenReturn(writeResult);

    StepVerifier.create(service().cancel(command(REFERENCE_NO)))
        .assertNext(
            outcome ->
                assertThat(outcome)
                    .isEqualTo(
                        new InvestmentChoiceCancelOutcome.Cancelled(
                            new InvestmentChoiceCancellation(REFERENCE_NO))))
        .verifyComplete();

    verify(failureEvents, never()).publish(any());
  }

  static Stream<Arguments> snapshotWriteResults() {
    return Stream.of(
        Arguments.of(Mono.empty()),
        Arguments.of(Mono.error(new IllegalStateException("snapshot-write-failed"))));
  }

  private CancelInvestmentChoiceService service() {
    return new CancelInvestmentChoiceService(snapshotCache, cancelPort, failureEvents);
  }

  private static CancelInvestmentChoiceCommand command(String referenceNo) {
    return new CancelInvestmentChoiceCommand(
        ACTOR_USER_ID,
        ActorUserRole.ROLE_USER_MBR,
        ACCOUNT_ENV,
        POLICY_NO,
        CERT_NO,
        SNAPSHOT_ACCOUNT_REF,
        SESSION_ID,
        REQUEST_ID,
        referenceNo,
        new com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest(1, "a".repeat(64)));
  }

  private static InvestmentChoiceSnapshot snapshot(List<String> pendingRefNos) {
    return new InvestmentChoiceSnapshot(
        SNAPSHOT_ACCOUNT_REF, POLICY_NO, 20, List.of(), List.of(), pendingRefNos);
  }
}
