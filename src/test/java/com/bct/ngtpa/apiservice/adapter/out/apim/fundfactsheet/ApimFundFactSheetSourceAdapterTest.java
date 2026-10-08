package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto.TrpGetFundFactSheetApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto.TrpGetFundFactSheetApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto.TrpGetFundFactSheetApimRow;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FetchFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetRow;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimFundFactSheetSourceAdapterTest {

  private static final String PATH = "/ws/NGTPA/v1/TRPGetFundFactSheet";
  private static final String NO_FUND = "err.tpa.no.fund.fact.sheet";
  private static final String POLICY_NO = "00000008802";
  private static final String SECRET_NAME = "Secret Fund Name";
  private static final String SECRET_LINK = "https://example.test/secret-link.pdf";

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachLogAppender() {
    logger = (Logger) LoggerFactory.getLogger(ApimFundFactSheetSourceAdapter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(appender);
    appender.stop();
  }

  // ----- request ---------------------------------------------------------------------------

  @Test
  void buildsTheExactExchangeRequestAndCallsTheExecutorOnce() {
    var executor = executorReturning(envelope("", List.of(item(row(1, "A", "B", "C", "D")))));

    adapter(executor).fetch(command(LocalDate.of(2026, 5, 5))).block();

    assertThat(executor.calls()).isEqualTo(1);
    var exchange = executor.seen();
    assertThat(exchange.path()).isEqualTo(PATH);
    assertThat(exchange.request())
        .isEqualTo(new TrpGetFundFactSheetApimRequest(POLICY_NO, "05/05/2026"));
    assertThat(exchange.requestType()).isEqualTo(TrpGetFundFactSheetApimRequest.class);
    assertThat(exchange.responseDataType()).isEqualTo(TrpGetFundFactSheetApimDataItem.class);
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(exchange.credentialContext().accountEnv()).isEqualTo("JP");
    assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  @Test
  void formatsTheReferenceDateAsDdMmUuuu() {
    assertThat(referenceDateSentFor(LocalDate.of(2026, 5, 5))).isEqualTo("05/05/2026");
    assertThat(referenceDateSentFor(LocalDate.of(2028, 2, 29))).isEqualTo("29/02/2028");
    assertThat(referenceDateSentFor(LocalDate.of(2026, 12, 1))).isEqualTo("01/12/2026");
  }

  // ----- success shapes ----------------------------------------------------------------------

  @Test
  void flattensRowsAcrossDataItemsInDownstreamOrder() {
    var data =
        envelope(
            "",
            List.of(
                item(row(30, "C", "", "", ""), row(10, "A", "", "", "")),
                item(row(20, "B", "", "", ""))));

    StepVerifier.create(adapter(executorReturning(data)).fetch(command(LocalDate.of(2026, 5, 5))))
        .assertNext(
            result ->
                assertThat(result.rows()).extracting(RawFundFactSheetRow::seq).containsExactly(30, 10, 20))
        .verifyComplete();
  }

  @Test
  void rowFieldsAndAMissingSeqPassThroughUnchanged() {
    var data = envelope("", List.of(item(row(null, null, "", "https://example.test/e.pdf", null))));

    StepVerifier.create(adapter(executorReturning(data)).fetch(command(LocalDate.of(2026, 5, 5))))
        .assertNext(
            result -> {
              assertThat(result.rows()).hasSize(1);
              var row = result.rows().get(0);
              assertThat(row.seq()).isNull();
              assertThat(row.nameEng()).isNull();
              assertThat(row.nameChi()).isEqualTo("");
              assertThat(row.linkEng()).isEqualTo("https://example.test/e.pdf");
              assertThat(row.linkChi()).isNull();
            })
        .verifyComplete();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void theNoFundCodeWithoutDataReturnsZeroRows() {
    assertZeroRows(envelope(NO_FUND, null));
  }

  @Test
  void theNoFundCodeWithDataPresentAlsoReturnsZeroRows() {
    assertZeroRows(envelope(NO_FUND, List.of(item(row(1, "A", "", "", "")))));
  }

  @Test
  void anEmptyListInsideANonNullItemContributesZeroRows() {
    assertZeroRows(envelope("", List.of(item(), item())));

    var data = envelope("", List.of(item(), item(row(7, "A", "", "", ""))));
    StepVerifier.create(adapter(executorReturning(data)).fetch(command(LocalDate.of(2026, 5, 5))))
        .assertNext(result -> assertThat(result.rows()).hasSize(1))
        .verifyComplete();
  }

  // ----- system failures without an adapter log ------------------------------------------------

  @Test
  void anEmptyErrMessageWithEmptyDataIsASystemFailure() {
    assertSystemFailureWithoutLog(envelope("", List.of()));
  }

  @Test
  void anEmptyErrMessageWithMissingDataIsASystemFailure() {
    assertSystemFailureWithoutLog(envelope("", null));
  }

  @Test
  void everyOtherDownstreamCodeIsASystemFailureAndTheNoFundCodeIsMatchedExactly() {
    assertSystemFailureWithoutLog(envelope("err.scheme.invalid", null));
    assertSystemFailureWithoutLog(envelope("err.tpa.something.else", List.of(item(row(1, "A", "", "", "")))));
    assertSystemFailureWithoutLog(envelope(" ", null));
    assertSystemFailureWithoutLog(envelope("ERR.TPA.NO.FUND.FACT.SHEET", null));
    assertSystemFailureWithoutLog(envelope(NO_FUND + " ", null));
  }

  @Test
  void aMissingResponseIsASystemFailure() {
    assertSystemFailureWithoutLog(new ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem>());
  }

  @Test
  void aMissingErrMessageIsASystemFailure() {
    assertSystemFailureWithoutLog(envelope(null, List.of(item(row(1, "A", "", "", "")))));
  }

  @Test
  void aTransportErrorBecomesASystemFailureWithoutRawText() {
    var executor = new StubExecutor(() -> Mono.error(new IllegalStateException("connection refused: 10.0.0.1")));

    StepVerifier.create(adapter(executor).fetch(command(LocalDate.of(2026, 5, 5))))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FundFactSheetRetrievalException.class);
              assertThat(error.getMessage()).doesNotContain("10.0.0.1");
              assertThat(error.getCause()).isNull();
            })
        .verify();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void theDomainExceptionPassesThroughUnchanged() {
    var original = FundFactSheetRetrievalException.systemFailure();
    var executor = new StubExecutor(() -> Mono.error(original));

    StepVerifier.create(adapter(executor).fetch(command(LocalDate.of(2026, 5, 5))))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  @Test
  void aSynchronousFailureInsideTheExecutorBecomesASystemFailure() {
    ApimExchangeExecutor throwing =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
            throw new IllegalStateException("boom");
          }
        };

    StepVerifier.create(adapter(throwing).fetch(command(LocalDate.of(2026, 5, 5))))
        .expectErrorSatisfies(
            error -> assertThat(error).isInstanceOf(FundFactSheetRetrievalException.class))
        .verify();
  }

  // ----- structural nulls: one value-free adapter event each ----------------------------------

  @Test
  void aNullDataItemIsASystemFailureAndLogsOneValueFreeEvent() {
    var data =
        envelope(
            "",
            Arrays.asList(item(row(10010, SECRET_NAME, SECRET_NAME, SECRET_LINK, SECRET_LINK)), null));

    assertSystemFailureWithOneEvent(data, "event=fund-fact-sheet.downstream-null-item");
  }

  @Test
  void aNullRowListIsASystemFailureAndLogsOneValueFreeEvent() {
    var data =
        envelope(
            "",
            List.of(
                item(row(10010, SECRET_NAME, SECRET_NAME, SECRET_LINK, SECRET_LINK)),
                new TrpGetFundFactSheetApimDataItem(null)));

    assertSystemFailureWithOneEvent(data, "event=fund-fact-sheet.downstream-null-list");
  }

  @Test
  void aNullRowIsASystemFailureAndLogsOneValueFreeEvent() {
    var data =
        envelope(
            "",
            List.of(
                new TrpGetFundFactSheetApimDataItem(
                    Arrays.asList(
                        row(10010, SECRET_NAME, SECRET_NAME, SECRET_LINK, SECRET_LINK), null))));

    assertSystemFailureWithOneEvent(data, "event=fund-fact-sheet.downstream-null-row");
  }

  // ----- construction ---------------------------------------------------------------------------

  @Test
  void aBlankOrNullPathIsRejected() {
    var executor = executorReturning(envelope(NO_FUND, null));

    assertThatThrownBy(() -> new ApimFundFactSheetSourceAdapter(executor, (String) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
    assertThatThrownBy(() -> new ApimFundFactSheetSourceAdapter(executor, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
  }

  @Test
  void aNullExecutorIsRejected() {
    assertThatThrownBy(() -> new ApimFundFactSheetSourceAdapter(null, PATH))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("executor");
  }

  @Test
  void aNullCommandIsRejected() {
    var adapter = adapter(executorReturning(envelope(NO_FUND, null)));

    assertThatThrownBy(() -> adapter.fetch(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("command");
  }

  @Test
  void theProductionConstructorTakesThePathFromTheProperties() {
    var properties = new FundFactSheetApimProperties();
    properties.setPath(PATH);
    var executor = executorReturning(envelope(NO_FUND, null));

    new ApimFundFactSheetSourceAdapter(executor, properties).fetch(command(LocalDate.of(2026, 5, 5))).block();

    assertThat(executor.seen().path()).isEqualTo(PATH);
  }

  @Test
  void theProductionConstructorRejectsAnUnsetConfiguredPath() {
    var executor = executorReturning(envelope(NO_FUND, null));

    assertThatThrownBy(() -> new ApimFundFactSheetSourceAdapter(executor, new FundFactSheetApimProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
  }

  // ----- helpers ---------------------------------------------------------------------------------

  private void assertZeroRows(ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem> data) {
    StepVerifier.create(adapter(executorReturning(data)).fetch(command(LocalDate.of(2026, 5, 5))))
        .assertNext(result -> assertThat(result.rows()).isEmpty())
        .verifyComplete();
  }

  private void assertSystemFailureWithoutLog(ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem> data) {
    StepVerifier.create(adapter(executorReturning(data)).fetch(command(LocalDate.of(2026, 5, 5))))
        .expectErrorSatisfies(
            error -> assertThat(error).isInstanceOf(FundFactSheetRetrievalException.class))
        .verify();
    assertThat(appender.list).isEmpty();
  }

  private void assertSystemFailureWithOneEvent(
      ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem> data, String expectedEvent) {
    StepVerifier.create(adapter(executorReturning(data)).fetch(command(LocalDate.of(2026, 5, 5))))
        .expectErrorSatisfies(
            error -> assertThat(error).isInstanceOf(FundFactSheetRetrievalException.class))
        .verify();

    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage()).contains(expectedEvent);
    assertThat(event.getFormattedMessage())
        .doesNotContain(POLICY_NO, SECRET_NAME, SECRET_LINK, "10010", "https://");
  }

  private static String referenceDateSentFor(LocalDate date) {
    var executor = executorReturning(envelope(NO_FUND, null));
    adapter(executor).fetch(command(date)).block();
    return ((TrpGetFundFactSheetApimRequest) executor.seen().request()).referenceDate();
  }

  private static ApimFundFactSheetSourceAdapter adapter(ApimExchangeExecutor executor) {
    return new ApimFundFactSheetSourceAdapter(executor, PATH);
  }

  private static FetchFundFactSheetCommand command(LocalDate date) {
    return new FetchFundFactSheetCommand(POLICY_NO, date, "JP", ActorUserRole.ROLE_USER_MBR);
  }

  private static StubExecutor executorReturning(
      ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem> envelope) {
    return new StubExecutor(() -> Mono.just(envelope));
  }

  private static ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem> envelope(
      String errMessage, List<TrpGetFundFactSheetApimDataItem> data) {
    return new ApimResponseEnvelope<>(new ApimResponseBody<>(errMessage, data));
  }

  private static TrpGetFundFactSheetApimDataItem item(TrpGetFundFactSheetApimRow... rows) {
    return new TrpGetFundFactSheetApimDataItem(Arrays.asList(rows));
  }

  private static TrpGetFundFactSheetApimRow row(
      Integer seq, String nameEng, String nameChi, String linkEng, String linkChi) {
    return new TrpGetFundFactSheetApimRow(seq, nameEng, nameChi, linkEng, linkChi);
  }

  /** Test double that records the exchange request and answers with a prepared signal. */
  private static final class StubExecutor implements ApimExchangeExecutor {
    private final Supplier<Mono<?>> response;
    private final AtomicInteger calls = new AtomicInteger();
    private ApimExchangeRequest<?, ?> seen;

    StubExecutor(Supplier<Mono<?>> response) {
      this.response = response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
      calls.incrementAndGet();
      seen = request;
      return (Mono<ApimResponseEnvelope<D>>) response.get();
    }

    int calls() {
      return calls.get();
    }

    ApimExchangeRequest<?, ?> seen() {
      return seen;
    }
  }
}
