package com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto.TrpGetFundCmpOptApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto.TrpGetFundCmpOptApimFundItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto.TrpGetFundCmpOptApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first: ApimFundComparisonOptionsSourceAdapter, its DTOs, and the new
 * FundComparisonOptionsSourcePort do not exist yet.
 *
 * <p>Mirrors ApimFundPriceModeSourceAdapterTest's exact structure (script 028 evidence): mocks
 * {@link ApimExchangeExecutor} directly, asserts the exact {@link ApimExchangeRequest} fields, and
 * tests the generic-failure mapping for every unusable envelope/err-message/empty-data shape, per
 * docs/design/13-fund-comparison/03-downstream-integration.md section 3.1 and
 * 04-validation-and-error-handling.md section 4.4. This adapter returns the RAW, unparsed
 * time-range string (normalization is FundComparisonTimeRangeParser's responsibility, built in
 * Milestone 1 and invoked later by the use case) -- it does NOT validate or reject based on
 * time-range content itself, only on envelope/ err-message/structural-data shape.
 */
class ApimFundComparisonOptionsSourceAdapterTest {

  private static final String PATH = "/ws/NGTPA/v1/TRPGetFundCmpOpt";

  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
  private final ApimFundComparisonOptionsSourceAdapter adapter =
      new ApimFundComparisonOptionsSourceAdapter(executor, PATH);

  private FetchFundComparisonOptionsCommand command() {
    return new FetchFundComparisonOptionsCommand("00000008802", "DB", ActorUserRole.ROLE_USER_MBR);
  }

  private ApimResponseEnvelope<TrpGetFundCmpOptApimDataItem> envelopeWith(
      String errMessage, List<TrpGetFundCmpOptApimDataItem> data) {
    var body =
        ApimResponseBody.<TrpGetFundCmpOptApimDataItem>builder()
            .errMessage(errMessage)
            .data(data)
            .build();
    return ApimResponseEnvelope.<TrpGetFundCmpOptApimDataItem>builder().response(body).build();
  }

  private TrpGetFundCmpOptApimDataItem validItem() {
    return new TrpGetFundCmpOptApimDataItem(
        "10D,1M,3M,6M,1Y,YTD,3Y",
        List.of(
            new TrpGetFundCmpOptApimFundItem(
                "FAEU",
                "Fidelity Advantage Portfolio Fund - Asia Pacific Equity Fund",
                "富達優越投資組合 - 亞太股票基金")));
  }

  // ----- exact ApimExchangeRequest captured -----------------------------------
  @SuppressWarnings("unchecked")
  @Test
  void buildsTheExactApimExchangeRequest() {
    ArgumentCaptor<ApimExchangeRequest<TrpGetFundCmpOptApimRequest, ?>> captor =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    doReturn(Mono.just(envelopeWith("", List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command())).expectNextCount(1).verifyComplete();

    org.mockito.Mockito.verify(executor).postEnvelope(captor.capture());
    var request = captor.getValue();
    assertThat(request.path()).isEqualTo(PATH);
    assertThat(request.request()).isEqualTo(new TrpGetFundCmpOptApimRequest("00000008802"));
    assertThat(request.requestType()).isEqualTo(TrpGetFundCmpOptApimRequest.class);
    assertThat(request.responseDataType()).isEqualTo(TrpGetFundCmpOptApimDataItem.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(request.credentialContext())
        .isEqualTo(new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, "DB"));
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  // ----- success mapping: raw time-range and fund list passed through as-is ----
  @Test
  void successMapsToRawFundComparisonOptionsDataPreservingRawContent() {
    doReturn(Mono.just(envelopeWith("", List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .assertNext(
            result -> {
              assertThat(result.rawTimeRange()).isEqualTo("10D,1M,3M,6M,1Y,YTD,3Y");
              assertThat(result.funds()).hasSize(1);
              var fund = result.funds().get(0);
              assertThat(fund.fundCode()).isEqualTo("FAEU");
              assertThat(fund.fundName())
                  .isEqualTo("Fidelity Advantage Portfolio Fund - Asia Pacific Equity Fund");
              assertThat(fund.chinFundName()).isEqualTo("富達優越投資組合 - 亞太股票基金");
            })
        .verifyComplete();
  }

  @Test
  void blankChinFundNameIsPreservedVerbatimNotDefaulted() {
    var item =
        new TrpGetFundCmpOptApimDataItem(
            "10D,1M", List.of(new TrpGetFundCmpOptApimFundItem("FBAU", "Some English Name", "")));
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .assertNext(result -> assertThat(result.funds().get(0).chinFundName()).isEmpty())
        .verifyComplete();
  }

  // ----- generic error mapping: every nonblank err-message -> systemFailure ----
  @Test
  void nonblankErrMessageMapsToGenericSystemFailure() {
    doReturn(Mono.just(envelopeWith("err.tpa.some.error", List.of(validItem()))))
        .when(executor)
        .postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  // ----- content validation: invalid downstream contracts fail closed ---------
  @Test
  void nullResponseFailsClosed() {
    var envelopeWithNullResponse =
        ApimResponseEnvelope.<TrpGetFundCmpOptApimDataItem>builder().response(null).build();
    doReturn(Mono.just(envelopeWithNullResponse)).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void zeroDataItemsFailsClosed() {
    doReturn(Mono.just(envelopeWith("", List.of()))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void multipleDataItemsFailsClosed() {
    doReturn(Mono.just(envelopeWith("", List.of(validItem(), validItem()))))
        .when(executor)
        .postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void nullTimeRangeFailsClosed() {
    var item = new TrpGetFundCmpOptApimDataItem(null, List.of(validItem().fundList().get(0)));
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void blankTimeRangeFailsClosed() {
    var item = new TrpGetFundCmpOptApimDataItem("   ", List.of(validItem().fundList().get(0)));
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void nullFundListFailsClosed() {
    var item = new TrpGetFundCmpOptApimDataItem("10D,1M", null);
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void emptyFundListFailsClosed() {
    var item = new TrpGetFundCmpOptApimDataItem("10D,1M", List.of());
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void blankFundCodeFailsClosed() {
    var item =
        new TrpGetFundCmpOptApimDataItem(
            "10D,1M", List.of(new TrpGetFundCmpOptApimFundItem("  ", "Some Name", "")));
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void blankFundNameFailsClosed() {
    var item =
        new TrpGetFundCmpOptApimDataItem(
            "10D,1M", List.of(new TrpGetFundCmpOptApimFundItem("FAEU", "  ", "")));
    doReturn(Mono.just(envelopeWith("", List.of(item)))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  // ----- transport/executor failure never leaks raw exception -----------------
  @Test
  void executorErrorNeverLeaksRawException() {
    doReturn(Mono.error(new IllegalStateException("connection refused: 10.0.0.1:443")))
        .when(executor)
        .postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FundComparisonRetrievalException.class);
              assertThat(error.getMessage()).doesNotContain("10.0.0.1", "connection refused");
            })
        .verify();
  }

  // ---- coverage closure (script 050): the production 2-arg (executor, properties)
  // constructor, blank-path rejection, and null-errMessage/null-data handling were
  // previously uncovered ----
  @Test
  void productionConstructorDelegatesToPackagePrivateConstructor() {
    var properties = new FundComparisonOptionsApimProperties();
    properties.setPath(PATH);
    var adapterViaProductionConstructor =
        new ApimFundComparisonOptionsSourceAdapter(executor, properties);

    doReturn(Mono.just(envelopeWith("", List.of(validItem())))).when(executor).postEnvelope(any());

    StepVerifier.create(adapterViaProductionConstructor.fetchOptions(command()))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void blankPathInPackagePrivateConstructorThrows() {
    assertThatThrownBy(() -> new ApimFundComparisonOptionsSourceAdapter(executor, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
  }

  @Test
  void explicitNullErrMessageIsTreatedAsSuccessJustLikeEmptyString() {
    // response.getErrMessage() returning null (as opposed to "") takes the false side
    // of `errMessage != null && !errMessage.isEmpty()` via short-circuit -- distinct
    // from the nonblank-string branch already covered by
    // nonblankErrMessageMapsToGenericSystemFailure.
    doReturn(Mono.just(envelopeWith(null, List.of(validItem()))))
        .when(executor)
        .postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command())).expectNextCount(1).verifyComplete();
  }

  @Test
  void nullDataFailsClosedJustLikeEmptyOrOversizedData() {
    doReturn(Mono.just(envelopeWith("", null))).when(executor).postEnvelope(any());

    StepVerifier.create(adapter.fetchOptions(command()))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }
}
