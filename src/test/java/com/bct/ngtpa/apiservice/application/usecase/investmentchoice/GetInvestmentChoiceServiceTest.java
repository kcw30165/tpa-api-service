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
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetailsCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.time.LocalDate;
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

class GetInvestmentChoiceServiceTest {

  private static final String ACCOUNT_ENV = "SIT";
  private static final String ACCOUNT_REF = "account-ref-sensitive";
  private static final String REQUEST_ID = "request-safe";
  private static final String SUCCESS_POLICY_NO = "118";
  private static final String SUCCESS_CERT_NO = "3";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final ActorUserRole ROLE = ActorUserRole.ROLE_USER_MBR;
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 15);

  private final ReferenceDatePort referenceDatePort = mock(ReferenceDatePort.class);
  private final InvestmentChoiceDetailsPort detailsPort = mock(InvestmentChoiceDetailsPort.class);
  private final InvestmentChoiceSnapshotCacheService snapshotCache =
      mock(InvestmentChoiceSnapshotCacheService.class);
  private final FailureEventPort failures = mock(FailureEventPort.class);
  private final AccountIdentifierFormatter formatter = new AccountIdentifierFormatter(11, 4, '0');
  private final Logger logger = (Logger) LoggerFactory.getLogger(GetInvestmentChoiceService.class);
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

  private GetInvestmentChoiceUseCase useCase() {
    return new GetInvestmentChoiceService(
        referenceDatePort, detailsPort, formatter, snapshotCache, failures);
  }

  private static GetInvestmentChoiceCommand command(String policyNo, String certNo) {
    return new GetInvestmentChoiceCommand(
        ROLE, ACCOUNT_ENV, policyNo, certNo, ACCOUNT_REF, SESSION_ID, REQUEST_ID);
  }

  private static InvestmentChoiceDetails details() {
    return new InvestmentChoiceDetails(
        25,
        List.of(
            new InvestmentChoiceSource("BO", 1, true), new InvestmentChoiceSource("EE", 2, false)),
        List.of(
            new InvestmentChoiceFund("FUN01", "Fund One", "基金一", "1", true),
            new InvestmentChoiceFund("FUN02", "Fund Two", "基金二", "2", false)),
        List.of(),
        List.of());
  }

  private static InvestmentChoiceDetails emptyDetails() {
    return new InvestmentChoiceDetails(5, List.of(), List.of(), List.of(), List.of());
  }

  private void arrangeDetails(InvestmentChoiceDetails value) {
    when(referenceDatePort.resolveReferenceDate(ACCOUNT_ENV)).thenReturn(Mono.just(REFERENCE_DATE));
    when(detailsPort.load(any())).thenReturn(Mono.just(value));
  }

  private void assertNoSensitiveLogValues() {
    String messages =
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + right);
    assertThat(messages)
        .doesNotContain(
            ACCOUNT_REF,
            SESSION_ID.value(),
            "123456789012",
            "12345",
            "CERT",
            "FUN01",
            "FUN02",
            "BO",
            "EE",
            "25");
  }

  @Test
  void resolvesDetailsFormatsAccountAndWritesSnapshotBeforeReturningUnmodifiedDetails() {
    var details = details();
    arrangeDetails(details);
    var writeCompleted = new AtomicBoolean();
    when(snapshotCache.write(any(), any(), any()))
        .thenReturn(Mono.fromRunnable(() -> writeCompleted.set(true)));

    InOrder order = inOrder(referenceDatePort, detailsPort, snapshotCache);
    StepVerifier.create(useCase().get(command(SUCCESS_POLICY_NO, SUCCESS_CERT_NO)))
        .assertNext(
            result -> {
              assertThat(result.accountNumber()).isEqualTo("00000000118-0003");
              assertThat(result.details()).isEqualTo(details);
              assertThat(writeCompleted).isTrue();
            })
        .verifyComplete();

    order.verify(referenceDatePort).resolveReferenceDate(ACCOUNT_ENV);
    var detailsCommand = ArgumentCaptor.forClass(InvestmentChoiceDetailsCommand.class);
    order.verify(detailsPort).load(detailsCommand.capture());
    assertThat(detailsCommand.getValue())
        .isEqualTo(
            new InvestmentChoiceDetailsCommand(
                ROLE, ACCOUNT_ENV, SUCCESS_POLICY_NO, SUCCESS_CERT_NO, REFERENCE_DATE));
    var snapshot = ArgumentCaptor.forClass(InvestmentChoiceSnapshot.class);
    order.verify(snapshotCache).write(eq(SESSION_ID), snapshot.capture(), eq(REQUEST_ID));
    assertThat(snapshot.getValue())
        .isEqualTo(
            new InvestmentChoiceSnapshot(
                ACCOUNT_REF,
                SUCCESS_POLICY_NO,
                25,
                List.of(
                    new InvestmentChoiceSnapshot.SwitchSource("BO", true),
                    new InvestmentChoiceSnapshot.SwitchSource("EE", false)),
                List.of(
                    new InvestmentChoiceSnapshot.Fund("FUN01", true),
                    new InvestmentChoiceSnapshot.Fund("FUN02", false))));
    verifyNoInteractions(failures);
    assertNoSensitiveLogValues();
  }

  @ParameterizedTest
  @MethodSource("invalidIdentifiers")
  void formatterFailurePublishesOneSafeEventAndDoesNotWriteSnapshot(
      String policyNo, String certNo) {
    arrangeDetails(details());

    StepVerifier.create(useCase().get(command(policyNo, certNo)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(InvestmentChoiceDetailsRetrievalException.class);
              assertThat(error.getMessage()).doesNotContain(policyNo, certNo, ACCOUNT_REF);
              assertThat(error.getCause()).isNull();
            })
        .verify();

    var event = ArgumentCaptor.forClass(FailureEvent.class);
    verify(failures, times(1)).publish(event.capture());
    assertThat(event.getValue().requestId()).isEqualTo(REQUEST_ID);
    assertThat(event.getValue().capability()).isEqualTo("INVESTMENT_CHOICE");
    assertThat(event.getValue().operation()).isEqualTo("GetInvestmentChoice");
    assertThat(event.getValue().event()).isEqualTo("investment-choice.retrieval.local_failure");
    assertThat(event.getValue().origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(event.getValue().publicCode())
        .isEqualTo("err.investment-choice.retrieval.unavailable");
    assertThat(event.getValue().httpStatus()).isEqualTo(503);
    assertThat(event.getValue().retryable()).isTrue();
    assertThat(event.getValue().safeDimensions()).containsOnlyKeys("reason");
    assertThat(event.getValue().toString())
        .doesNotContain(
            policyNo, certNo, ACCOUNT_REF, SESSION_ID.value(), "FUN01", "FUN02", "BO", "EE", "25");
    verify(snapshotCache, never()).write(any(), any(), any());
    assertNoSensitiveLogValues();
  }

  private static Stream<Arguments> invalidIdentifiers() {
    return Stream.of(
        Arguments.of("123456789012", "4"),
        Arguments.of(SUCCESS_POLICY_NO, "12345"),
        Arguments.of(SUCCESS_POLICY_NO, "CERT"));
  }

  @Test
  void detailsRetrievalFailurePropagatesWithoutEventOrSnapshotWrite() {
    var failure = InvestmentChoiceDetailsRetrievalException.systemFailure();
    when(referenceDatePort.resolveReferenceDate(ACCOUNT_ENV)).thenReturn(Mono.just(REFERENCE_DATE));
    when(detailsPort.load(any())).thenReturn(Mono.error(failure));

    StepVerifier.create(useCase().get(command(SUCCESS_POLICY_NO, SUCCESS_CERT_NO)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();

    verifyNoInteractions(failures);
    verify(snapshotCache, never()).write(any(), any(), any());
    assertNoSensitiveLogValues();
  }

  @Test
  void referenceDateFailureMapsToRetrievalFailureAndSkipsLaterStages() {
    when(referenceDatePort.resolveReferenceDate(ACCOUNT_ENV))
        .thenReturn(Mono.error(new IllegalStateException("reference-date-sensitive")));

    StepVerifier.create(useCase().get(command(SUCCESS_POLICY_NO, SUCCESS_CERT_NO)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(InvestmentChoiceDetailsRetrievalException.class);
              assertThat(error.getMessage()).doesNotContain("reference-date-sensitive");
              assertThat(error.getCause()).isNull();
            })
        .verify();

    verifyNoInteractions(detailsPort, snapshotCache, failures);
    assertNoSensitiveLogValues();
  }

  @Test
  void skippedSnapshotWriteDoesNotChangeSuccessfulResult() {
    var details = details();
    arrangeDetails(details);
    when(snapshotCache.write(any(), any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(useCase().get(command(SUCCESS_POLICY_NO, SUCCESS_CERT_NO)))
        .assertNext(
            result -> {
              assertThat(result.accountNumber()).isEqualTo("00000000118-0003");
              assertThat(result.details()).isEqualTo(details);
            })
        .verifyComplete();
    verify(snapshotCache).write(eq(SESSION_ID), any(), eq(REQUEST_ID));
    assertNoSensitiveLogValues();
  }

  @Test
  void failedSnapshotWriteDoesNotChangeSuccessfulResult() {
    var details = details();
    arrangeDetails(details);
    when(snapshotCache.write(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("snapshot-write-sensitive")));

    StepVerifier.create(useCase().get(command(SUCCESS_POLICY_NO, SUCCESS_CERT_NO)))
        .assertNext(
            result -> {
              assertThat(result.accountNumber()).isEqualTo("00000000118-0003");
              assertThat(result.details()).isEqualTo(details);
            })
        .verifyComplete();
    verify(snapshotCache).write(eq(SESSION_ID), any(), eq(REQUEST_ID));
    assertNoSensitiveLogValues();
  }

  @Test
  void emptyHistoryPendingSourcesAndFundsAreValidAndWritten() {
    var details = emptyDetails();
    arrangeDetails(details);
    when(snapshotCache.write(any(), any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(useCase().get(command(SUCCESS_POLICY_NO, SUCCESS_CERT_NO)))
        .assertNext(result -> assertThat(result.details()).isEqualTo(details))
        .verifyComplete();

    var snapshot = ArgumentCaptor.forClass(InvestmentChoiceSnapshot.class);
    verify(snapshotCache).write(eq(SESSION_ID), snapshot.capture(), eq(REQUEST_ID));
    assertThat(snapshot.getValue().sources()).isEmpty();
    assertThat(snapshot.getValue().funds()).isEmpty();
    assertNoSensitiveLogValues();
  }
}
