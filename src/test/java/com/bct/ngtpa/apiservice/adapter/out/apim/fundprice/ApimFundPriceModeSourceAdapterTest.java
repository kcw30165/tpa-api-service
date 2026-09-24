package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceModeApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceModeApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceModeCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceModeData;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first: ApimFundPriceModeSourceAdapter, its DTOs, and FundPriceRetrievalException do not
 * exist yet.
 *
 * <p>Per .claude/skills/apim-integration/SKILL.md: mocks {@link ApimExchangeExecutor} directly and
 * asserts the exact {@link ApimExchangeRequest} fields. Mirrors the model {@code
 * ApimAccountBalanceSourceAdapter} / {@code ApimGeneralMetadataSourceAdapter} pattern.
 *
 * <p>Byte-exact contract source: docs/design/9-fund-price/fund-price-apim-contract.md (Operation
 * 1: TRPGetFundPriceMode) and fund-price-date-validation-contract.md (config-backed full-date
 * formatter; available dates use the SAME formatter as the request/response full-date field).
 */
class ApimFundPriceModeSourceAdapterTest {

  private static final String PATH = "/ws/NGTPA/v1/TRPGetFundPriceMode";
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
  private final ApimFundPriceModeSourceAdapter adapter =
      new ApimFundPriceModeSourceAdapter(executor, PATH, FORMATTER);

  private FetchFundPriceModeCommand command() {
    return new FetchFundPriceModeCommand(
        "00000008802",
        LocalDate.of(2026, 9, 18),
        "DB",
        ActorUserRole.ROLE_USER_MBR);
  }

  private ApimResponseEnvelope<TrpGetFundPriceModeApimDataItem> envelopeWith(
      String errMessage, List<TrpGetFundPriceModeApimDataItem> data) {
    var body =
        ApimResponseBody.<TrpGetFundPriceModeApimDataItem>builder()
            .errMessage(errMessage)
            .data(data)
            .build();
    return ApimResponseEnvelope.<TrpGetFundPriceModeApimDataItem>builder().response(body).build();
  }

  private TrpGetFundPriceModeApimDataItem validItem() {
    return new TrpGetFundPriceModeApimDataItem(
        "DAILY", "BIDOFFER PRICE", List.of("18/09/2026", "18/05/2026"), 36, null);
  }

  // ----- exact ApimExchangeRequest captured -----------------------------------

  @SuppressWarnings("unchecked")
  @Test
  void buildsTheExactApimExchangeRequest() {
    ArgumentCaptor<ApimExchangeRequest<TrpGetFundPriceModeApimRequest, TrpGetFundPriceModeApimDataItem>>
        captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    doReturn(Mono.just(envelopeWith("", List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command())).expectNextCount(1).verifyComplete();

    org.mockito.Mockito.verify(executor).postEnvelope(captor.capture());
    var request = captor.getValue();

    assertThat(request.path()).isEqualTo(PATH);
    assertThat(request.request())
        .isEqualTo(new TrpGetFundPriceModeApimRequest("00000008802", "18/09/2026"));
    assertThat(request.requestType()).isEqualTo(TrpGetFundPriceModeApimRequest.class);
    assertThat(request.responseDataType()).isEqualTo(TrpGetFundPriceModeApimDataItem.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(request.credentialContext())
        .isEqualTo(new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, "DB"));
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  // ----- success mapping -------------------------------------------------------

  @Test
  void successMapsToRawFundPriceModeDataPreservingDateOrder() {
    doReturn(Mono.just(envelopeWith("", List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .assertNext(
            result -> {
              assertThat(result.mode()).isEqualTo("DAILY");
              assertThat(result.fundPriceType()).isEqualTo("BIDOFFER PRICE");
              assertThat(result.availableFundPriceDates())
                  .containsExactly(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 5, 18));
              assertThat(result.maximumFundPriceHistoryMonths()).isEqualTo(36);
            })
        .verifyComplete();
  }

  @Test
  void unitPriceFundPriceTypeIsAccepted() {
    var item =
        new TrpGetFundPriceModeApimDataItem("MONTHLY", "UNIT PRICE", List.of("18/09/2026"), 12, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .assertNext(result -> assertThat(result.fundPriceType()).isEqualTo("UNIT PRICE"))
        .verifyComplete();
  }

  // ----- generic error mapping: every nonblank err-message -> systemFailure ----

  @Test
  void nonblankErrMessageMapsToGenericSystemFailure() {
    doReturn(Mono.just(envelopeWith("err.tpa.invalid.date", List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void unknownErrMessageAlsoMapsToTheSameGenericSystemFailure() {
    doReturn(Mono.just(envelopeWith("some-unmapped-downstream-error", List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  // ----- content validation: invalid downstream contracts fail closed ---------

  @Test
  void zeroDataItemsFailsClosed() {
    doReturn(Mono.just(envelopeWith("", List.of()))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void multipleDataItemsFailsClosed() {
    doReturn(Mono.just(envelopeWith("", List.of(validItem(), validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void blankModeFailsClosed() {
    var item = new TrpGetFundPriceModeApimDataItem("", "BIDOFFER PRICE", List.of("18/09/2026"), 36, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void unsupportedModeValueFailsClosed() {
    // Contract: Java has approved interpretation only for DAILY and MONTHLY. Java must not
    // invent WEEKLY behavior -- an unrecognized mode is an invalid downstream contract.
    var item =
        new TrpGetFundPriceModeApimDataItem("WEEKLY", "BIDOFFER PRICE", List.of("18/09/2026"), 36, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void blankFundPriceTypeFailsClosed() {
    var item = new TrpGetFundPriceModeApimDataItem("DAILY", "", List.of("18/09/2026"), 36, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void unsupportedFundPriceTypeFailsClosed() {
    var item =
        new TrpGetFundPriceModeApimDataItem("DAILY", "SOMETHING_ELSE", List.of("18/09/2026"), 36, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void zeroMaximumHistoryMonthsFailsClosed() {
    var item =
        new TrpGetFundPriceModeApimDataItem("DAILY", "BIDOFFER PRICE", List.of("18/09/2026"), 0, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void negativeMaximumHistoryMonthsFailsClosed() {
    var item =
        new TrpGetFundPriceModeApimDataItem("DAILY", "BIDOFFER PRICE", List.of("18/09/2026"), -1, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void unparseableAvailableDateFailsClosed() {
    var item =
        new TrpGetFundPriceModeApimDataItem(
            "DAILY", "BIDOFFER PRICE", List.of("2026-09-18"), 36, null); // wrong format on purpose
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void blankAvailableDateEntryFailsClosed() {
    var item =
        new TrpGetFundPriceModeApimDataItem(
            "DAILY", "BIDOFFER PRICE", java.util.Arrays.asList("18/09/2026", ""), 36, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void emptyAvailableDatesListFailsClosed() {
    var item = new TrpGetFundPriceModeApimDataItem("DAILY", "BIDOFFER PRICE", List.of(), 36, null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }

  // ----- transport/executor failure never leaks raw exception ------------------

  @Test
  void executorErrorNeverLeaksRawException() {
    doReturn(Mono.error(new IllegalStateException("connection refused: 10.0.0.1:443"))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FundPriceRetrievalException.class);
              assertThat(error.getMessage()).doesNotContain("10.0.0.1", "connection refused");
            })
        .verify();
  }

  // ---- coverage closure (script 030/031): the production 3-arg constructor,
  // blank-path construction, and explicit-null errMessage were previously uncovered ----

  @Test
  void productionConstructorDelegatesToPackagePrivateConstructor() {
    var properties = new FundPriceModeApimProperties();
    properties.setPath(PATH);
    var adapterViaProductionConstructor =
        new ApimFundPriceModeSourceAdapter(executor, properties, FORMATTER);

    doReturn(Mono.just(envelopeWith("", List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapterViaProductionConstructor.fetchMode(command()))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void blankPathInPackagePrivateConstructorThrows() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new ApimFundPriceModeSourceAdapter(executor, "   ", FORMATTER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
  }

  @Test
  void explicitNullErrMessageIsTreatedAsSuccessJustLikeEmptyString() {
    // response.getErrMessage() returning null (as opposed to "") takes the false side of
    // `errMessage != null && !errMessage.isEmpty()` via short-circuit -- distinct from the
    // nonblank-string branch already covered by nonblankErrMessageMapsToGenericSystemFailure.
    doReturn(Mono.just(envelopeWith(null, List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command())).expectNextCount(1).verifyComplete();
  }

  @Test
  void envelopeWithNullResponseNeverLeaksAsANullPointerException() {
    // Mono.justOrEmpty(null) is Mono.empty() (Reactive Streams forbids onNext(null)),
    // so it can never represent "an envelope reaches flatMap with a null value" -- the
    // scenario this test actually needs is a NON-null envelope whose `response` field
    // is null, which IS representable (ApimResponseEnvelope has no null-guard on that
    // field) and is exactly what the adapter's own null-check defends against.
    var envelopeWithNullResponse =
        ApimResponseEnvelope.<TrpGetFundPriceModeApimDataItem>builder().response(null).build();
    doReturn(Mono.just(envelopeWithNullResponse)).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchMode(command()))
        .expectErrorMatches(FundPriceRetrievalException.class::isInstance)
        .verify();
  }
}
