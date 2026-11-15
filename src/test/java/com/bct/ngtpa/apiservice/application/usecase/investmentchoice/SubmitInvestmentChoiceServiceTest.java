package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetailsCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRow;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshotState;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmission;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmitOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.SubmitInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionRejectedException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnavailableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnknownOutcomeException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceInstructionPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

class SubmitInvestmentChoiceServiceTest {

  private static final String ACTOR_USER_ID = "actor-user-sensitive";
  private static final ActorUserRole ACTOR_USER_ROLE = ActorUserRole.ROLE_USER_MBR;
  private static final String ACCOUNT_ENV = "SIT";
  private static final String POLICY_NO = "policy-sensitive";
  private static final String CERT_NO = "cert-sensitive";
  private static final String SNAPSHOT_ACCOUNT_REF = "snapshot-account-ref-sensitive";
  private static final String PENDING_REFERENCE_NO = "260001449";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_session-sensitive-0123456789");
  private static final String REQUEST_ID = "request-safe";
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 6);
  private static final LocalDate HK_TODAY = LocalDate.of(2026, 11, 6);
  private static final Instant HK_BOUNDARY_INSTANT = Instant.parse("2026-11-05T16:30:00Z");
  private static final InvestmentChoiceSubmission ADAPTER_SUBMISSION =
      new InvestmentChoiceSubmission("adapter-ref-sensitive", HK_TODAY, LocalTime.of(9, 15));
  private static final InvestmentChoiceInstructionRequest VALID_REQUEST =
      request(
          List.of("BO", "EE"),
          new InvestmentChoiceInstructionRequest.Allocation("FBAL", 60),
          new InvestmentChoiceInstructionRequest.Allocation("FHKM", 40));
  private static final List<InvestmentChoiceInstructionRow> EXPANDED_ROWS =
      List.of(
          new InvestmentChoiceInstructionRow("BO", "FBAL", 60),
          new InvestmentChoiceInstructionRow("BO", "FHKM", 40),
          new InvestmentChoiceInstructionRow("EE", "FBAL", 60),
          new InvestmentChoiceInstructionRow("EE", "FHKM", 40));

  private final ReferenceDatePort referenceDatePort = mock(ReferenceDatePort.class);
  private final InvestmentChoiceDetailsPort detailsPort = mock(InvestmentChoiceDetailsPort.class);
  private final InvestmentChoiceInstructionPort instructionPort =
      mock(InvestmentChoiceInstructionPort.class);
  private final InvestmentChoiceSnapshotCacheService snapshotCache =
      mock(InvestmentChoiceSnapshotCacheService.class);
  private final FailureEventPort failures = mock(FailureEventPort.class);
  private final Clock clock = Clock.fixed(HK_BOUNDARY_INSTANT, ZoneOffset.UTC);
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(SubmitInvestmentChoiceService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachLogger() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachLogger() {
    logger.detachAppender(appender);
    appender.stop();
  }

  private SubmitInvestmentChoiceService useCase() {
    return new SubmitInvestmentChoiceService(
        referenceDatePort, detailsPort, instructionPort, snapshotCache, clock, failures);
  }

  private static SubmitInvestmentChoiceCommand command(InvestmentChoiceInstructionRequest request) {
    return new SubmitInvestmentChoiceCommand(
        ACTOR_USER_ID,
        ACTOR_USER_ROLE,
        ACCOUNT_ENV,
        POLICY_NO,
        CERT_NO,
        SNAPSHOT_ACCOUNT_REF,
        SESSION_ID,
        REQUEST_ID,
        request,
        new com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest(1, "a".repeat(64)));
  }

  private static InvestmentChoiceInstructionRequest request(
      List<String> switchSources, InvestmentChoiceInstructionRequest.Allocation... allocations) {
    return new InvestmentChoiceInstructionRequest(switchSources, List.of(allocations));
  }

  private static InvestmentChoiceDetails details(List<InvestmentChoicePending> pending) {
    return new InvestmentChoiceDetails.Normal(20, List.of(), List.of(), List.of(), pending);
  }

  private static InvestmentChoicePending pending(
      String referenceNo, LocalDate submitDate, List<InvestmentChoicePendingDetail> rows) {
    return new InvestmentChoicePending(referenceNo, submitDate, LocalTime.of(9, 15), rows);
  }

  private static List<InvestmentChoicePendingDetail> matchingPendingRows() {
    return List.of(
        new InvestmentChoicePendingDetail("EE", "FHKM", 40),
        new InvestmentChoicePendingDetail("BO", "FBAL", 60),
        new InvestmentChoicePendingDetail("EE", "FBAL", 60),
        new InvestmentChoicePendingDetail("BO", "FHKM", 40));
  }

  private void arrangeUnknownOutcomeWithDetails(InvestmentChoiceDetails details) {
    when(instructionPort.submit(any()))
        .thenReturn(Mono.error(new InvestmentChoiceSubmissionUnknownOutcomeException()));
    when(referenceDatePort.resolveReferenceDate(ACCOUNT_ENV)).thenReturn(Mono.just(REFERENCE_DATE));
    when(detailsPort.load(any())).thenReturn(Mono.just(details));
  }

  private void assertNoSensitiveLogValues() {
    String messages =
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + right);
    assertThat(messages)
        .doesNotContain(
            ACTOR_USER_ID,
            POLICY_NO,
            CERT_NO,
            SNAPSHOT_ACCOUNT_REF,
            SESSION_ID.value(),
            "FBAL",
            "FHKM",
            "60",
            "40",
            "adapter-ref-sensitive",
            "pending-ref-sensitive",
            PENDING_REFERENCE_NO);
  }

  @Test
  void submitsExpandedRowsAndReturnsTheAdapterSubmissionWithoutReconciling() {
    when(instructionPort.submit(any())).thenReturn(Mono.just(ADAPTER_SUBMISSION));

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .assertNext(
            outcome ->
                assertThat(outcome)
                    .isEqualTo(new InvestmentChoiceSubmitOutcome.Submitted(ADAPTER_SUBMISSION)))
        .verifyComplete();

    InOrder order = inOrder(instructionPort);
    var instruction = ArgumentCaptor.forClass(InvestmentChoiceInstructionCommand.class);
    order.verify(instructionPort).submit(instruction.capture());
    assertThat(instruction.getValue())
        .isEqualTo(
            new InvestmentChoiceInstructionCommand(
                ACTOR_USER_ID, ACTOR_USER_ROLE, ACCOUNT_ENV, POLICY_NO, CERT_NO, EXPANDED_ROWS));
    verifyNoInteractions(snapshotCache, referenceDatePort, detailsPort);
    verifyNoInteractions(failures);
    assertThat(appender.list).isEmpty();
    assertNoSensitiveLogValues();
  }

  @Test
  void executionDispatchesWithoutReadingOrRevalidatingTheSnapshot() {
    when(instructionPort.submit(any())).thenReturn(Mono.just(ADAPTER_SUBMISSION));

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .expectNext(new InvestmentChoiceSubmitOutcome.Submitted(ADAPTER_SUBMISSION))
        .verifyComplete();

    verify(instructionPort).submit(any());
    verifyNoInteractions(snapshotCache, referenceDatePort, detailsPort);
  }

  @Test
  void rejectedSubmissionBecomesUnavailableWithoutReconciliationOrUseCaseEvent() {
    when(instructionPort.submit(any()))
        .thenReturn(Mono.error(new InvestmentChoiceSubmissionRejectedException()));

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .expectError(InvestmentChoiceSubmissionUnavailableException.class)
        .verify();

    verify(instructionPort).submit(any());
    verifyNoInteractions(referenceDatePort, detailsPort);
    assertThat(appender.list).isEmpty();
    assertNoSensitiveLogValues();
  }

  @Test
  void unknownOutcomeMatchesTheExactPendingSetAndRefreshesSnapshotBeforeSuccess() {
    arrangeUnknownOutcomeWithDetails(
        details(List.of(pending(PENDING_REFERENCE_NO, HK_TODAY, matchingPendingRows()))));
    var refreshed = new AtomicBoolean();
    when(snapshotCache.writeState(eq(SESSION_ID), any(), eq(REQUEST_ID)))
        .thenReturn(Mono.fromRunnable(() -> refreshed.set(true)));

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .assertNext(
            outcome -> {
              assertThat(outcome)
                  .isEqualTo(
                      new InvestmentChoiceSubmitOutcome.Submitted(
                          new InvestmentChoiceSubmission(
                              PENDING_REFERENCE_NO, HK_TODAY, LocalTime.of(9, 15))));
              assertThat(refreshed).isTrue();
            })
        .verifyComplete();

    var refreshedSnapshot = ArgumentCaptor.forClass(InvestmentChoiceSnapshotState.class);
    verify(snapshotCache, times(1))
        .writeState(eq(SESSION_ID), refreshedSnapshot.capture(), eq(REQUEST_ID));
    verifyNoInteractions(failures);
    assertThat(refreshedSnapshot.getValue().normalSnapshot().orElseThrow().pendingRefNos())
        .containsExactly(PENDING_REFERENCE_NO);
    verify(referenceDatePort).resolveReferenceDate(ACCOUNT_ENV);
    var detailsCommand = ArgumentCaptor.forClass(InvestmentChoiceDetailsCommand.class);
    verify(detailsPort).load(detailsCommand.capture());
    assertThat(detailsCommand.getValue())
        .isEqualTo(
            new InvestmentChoiceDetailsCommand(
                ACTOR_USER_ROLE, ACCOUNT_ENV, POLICY_NO, CERT_NO, REFERENCE_DATE));
    assertThat(appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
        .containsExactly("event=investment-choice.submission.reconciled requestId=" + REQUEST_ID);
    assertNoSensitiveLogValues();
  }

  @ParameterizedTest
  @MethodSource("unreconciledCandidates")
  void unknownOutcomeWithoutExactlyOneTodaysExactCandidateIsUnavailable(
      String scenario, List<InvestmentChoicePending> pending) {
    arrangeUnknownOutcomeWithDetails(details(pending));
    when(snapshotCache.writeState(any(), any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .expectError(InvestmentChoiceSubmissionUnavailableException.class)
        .verify();

    verify(snapshotCache, never()).read(any(), any(), any(), any());
    verify(snapshotCache, never()).write(any(), any(), any());
    verify(detailsPort).load(any());
    assertThat(appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
        .as(scenario)
        .containsExactly("event=investment-choice.submission.unreconciled requestId=" + REQUEST_ID);
    assertNoSensitiveLogValues();
  }

  @Test
  void restrictedReconciliationCannotEstablishAPendingInstruction() {
    when(instructionPort.submit(any()))
        .thenReturn(Mono.error(new InvestmentChoiceSubmissionUnknownOutcomeException()));
    when(referenceDatePort.resolveReferenceDate(ACCOUNT_ENV)).thenReturn(Mono.just(REFERENCE_DATE));
    when(detailsPort.load(any()))
        .thenReturn(
            Mono.just(
                new InvestmentChoiceDetails.Restricted(
                    com.bct.ngtpa.apiservice.application.dto.investmentchoice
                        .InvestmentChoiceScenario.MEMBER_INELIGIBLE,
                    List.of(),
                    List.of(),
                    List.of())));

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .expectError(InvestmentChoiceSubmissionUnavailableException.class)
        .verify();

    verify(snapshotCache).writeRestrictedState(any(), any(), any());
    verify(snapshotCache, never()).writeState(any(), any(), any());
    assertThat(appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
        .containsExactly("event=investment-choice.submission.unreconciled requestId=" + REQUEST_ID);
    assertNoSensitiveLogValues();
  }

  private static Stream<Arguments> unreconciledCandidates() {
    return Stream.of(
        Arguments.of("zero candidates", List.<InvestmentChoicePending>of()),
        Arguments.of(
            "two candidates",
            List.of(
                pending("pending-one", HK_TODAY, matchingPendingRows()),
                pending("pending-two", HK_TODAY, matchingPendingRows()))),
        Arguments.of(
            "yesterday",
            List.of(pending("pending-yesterday", HK_TODAY.minusDays(1), matchingPendingRows()))),
        Arguments.of(
            "different percentage",
            List.of(
                pending(
                    "pending-different-percentage",
                    HK_TODAY,
                    List.of(
                        new InvestmentChoicePendingDetail("BO", "FBAL", 59),
                        new InvestmentChoicePendingDetail("BO", "FHKM", 41),
                        new InvestmentChoicePendingDetail("EE", "FBAL", 60),
                        new InvestmentChoicePendingDetail("EE", "FHKM", 40))))),
        Arguments.of(
            "missing row",
            List.of(pending("pending-missing-row", HK_TODAY, matchingPendingRows().subList(0, 3)))),
        Arguments.of(
            "extra row",
            List.of(
                pending(
                    "pending-extra-row",
                    HK_TODAY,
                    Stream.concat(
                            matchingPendingRows().stream(),
                            Stream.of(new InvestmentChoicePendingDetail("BO", "FBAL", 59)))
                        .toList()))));
  }

  @Test
  void hongKongDayBoundaryMatchesNovemberSixAndRejectsNovemberFive() {
    var yesterday = pending("pending-nov-five", LocalDate.of(2026, 11, 5), matchingPendingRows());
    var today = pending("pending-nov-six", LocalDate.of(2026, 11, 6), matchingPendingRows());
    arrangeUnknownOutcomeWithDetails(details(List.of(yesterday, today)));
    when(snapshotCache.writeState(any(), any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .assertNext(
            outcome ->
                assertThat(outcome)
                    .isEqualTo(
                        new InvestmentChoiceSubmitOutcome.Submitted(
                            new InvestmentChoiceSubmission(
                                "pending-nov-six",
                                LocalDate.of(2026, 11, 6),
                                LocalTime.of(9, 15)))))
        .verifyComplete();

    assertThat(appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
        .containsExactly("event=investment-choice.submission.reconciled requestId=" + REQUEST_ID);
    assertNoSensitiveLogValues();
  }

  @Test
  void reconciliationReferenceDateFailureBecomesUnavailable() {
    when(instructionPort.submit(any()))
        .thenReturn(Mono.error(new InvestmentChoiceSubmissionUnknownOutcomeException()));
    when(referenceDatePort.resolveReferenceDate(ACCOUNT_ENV))
        .thenReturn(Mono.error(new IllegalStateException("reference-date-sensitive")));

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(InvestmentChoiceSubmissionUnavailableException.class);
              assertThat(error.getMessage()).doesNotContain("reference-date-sensitive");
              assertThat(error.getCause()).isNull();
            })
        .verify();

    verifyNoInteractions(detailsPort);
    verify(snapshotCache, never()).write(any(), any(), any());
    assertThat(appender.list).isEmpty();
    var captured = ArgumentCaptor.forClass(FailureEvent.class);
    verify(failures, times(1)).publish(captured.capture());
    assertThat(captured.getValue())
        .isEqualTo(
            new FailureEvent(
                "investment-choice.submission.reference_date_failed",
                REQUEST_ID,
                "INVESTMENT_CHOICE",
                "REFERENCE_DATE",
                FailureOrigin.LOCAL_SYSTEM,
                null,
                "err.investment-choice.submission.unavailable",
                503,
                false,
                java.util.Map.of("reason", "REFERENCE_DATE_RESOLUTION_FAILED")));
    assertThat(captured.getValue().toString())
        .doesNotContain(
            ACTOR_USER_ID,
            POLICY_NO,
            CERT_NO,
            SNAPSHOT_ACCOUNT_REF,
            SESSION_ID.value(),
            "reference-date-sensitive");
  }

  @Test
  void reconciliationDetailsFailureBecomesUnavailable() {
    when(instructionPort.submit(any()))
        .thenReturn(Mono.error(new InvestmentChoiceSubmissionUnknownOutcomeException()));
    when(referenceDatePort.resolveReferenceDate(ACCOUNT_ENV)).thenReturn(Mono.just(REFERENCE_DATE));
    when(detailsPort.load(any()))
        .thenReturn(Mono.error(InvestmentChoiceDetailsRetrievalException.systemFailure()));

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .expectError(InvestmentChoiceSubmissionUnavailableException.class)
        .verify();

    verify(detailsPort).load(any());
    verify(snapshotCache, never()).write(any(), any(), any());
    assertThat(appender.list).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("writeResults")
  void failedOrSkippedRefreshDoesNotChangeReconciledSuccess(Mono<Void> writeResult) {
    arrangeUnknownOutcomeWithDetails(
        details(List.of(pending("pending-ref-sensitive", HK_TODAY, matchingPendingRows()))));
    when(snapshotCache.writeState(any(), any(), any())).thenReturn(writeResult);

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .expectNextMatches(
            outcome ->
                outcome.equals(
                    new InvestmentChoiceSubmitOutcome.Submitted(
                        new InvestmentChoiceSubmission(
                            "pending-ref-sensitive", HK_TODAY, LocalTime.of(9, 15)))))
        .verifyComplete();

    verify(snapshotCache).writeState(eq(SESSION_ID), any(), eq(REQUEST_ID));
    assertNoSensitiveLogValues();
  }

  private static Stream<Arguments> writeResults() {
    return Stream.of(
        Arguments.of(Mono.empty()),
        Arguments.of(Mono.error(new IllegalStateException("snapshot-write-sensitive"))));
  }

  @Test
  void anyUnexpectedInstructionFailureIsReconciledAsUnknownOutcome() {
    when(instructionPort.submit(any()))
        .thenReturn(Mono.error(new IllegalStateException("unexpected-sensitive")));
    when(referenceDatePort.resolveReferenceDate(ACCOUNT_ENV)).thenReturn(Mono.just(REFERENCE_DATE));
    when(detailsPort.load(any()))
        .thenReturn(
            Mono.just(
                details(
                    List.of(pending("pending-ref-sensitive", HK_TODAY, matchingPendingRows())))));
    when(snapshotCache.writeState(any(), any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(useCase().submit(command(VALID_REQUEST)))
        .expectNextCount(1)
        .verifyComplete();

    verify(detailsPort).load(any());
    assertNoSensitiveLogValues();
  }
}
