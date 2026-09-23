package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlFund;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.FetchAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link ApimAccountGainLossSourceAdapter}. The public contract
 * ({@code fetch(FetchAccountGainLossCommand) -> Mono<RawAccountGainLossData>}) is independently
 * confirmed via the already-passing GetAccountGainLossServiceTest (rounds 125/126), which mocks the
 * same {@code AccountGainLossSourcePort} interface this adapter implements. All DTO/collaborator
 * types verified verbatim (rounds 127-128).
 */
class ApimAccountGainLossSourceAdapterTest {

  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
  private final AccountGainLossApimProperties properties = new AccountGainLossApimProperties();

  {
    properties.setPath("TRPGetInvestGL");
  }

  private ApimAccountGainLossSourceAdapter newAdapter() {
    return new ApimAccountGainLossSourceAdapter(executor, properties);
  }

  // new FetchAccountGainLossCommand(
  //     command.period(),
  //     command.currency(),
  //     command.accountEnv(),
  //     command.policyNo(),
  //     command.certNo(),
  //     command.actorUserId(),
  //     command.actorUserRole(),
  //     date))
  private FetchAccountGainLossCommand command() {
    return new FetchAccountGainLossCommand(
        AccountGainLossPeriod.YEAR_TO_DATE,
        "HKD",
        "env1",
        "policy1",
        "cert1",
        "actor1",
        ActorUserRole.ROLE_USER_MBR,
        LocalDate.of(2026, 1, 1));
  }

  private static TrpGetInvestGlFund fund(String code, int sequence, String gainLoss) {
    return new TrpGetInvestGlFund(
        code, "English " + code, "Chinese " + code, sequence, new BigDecimal(gainLoss));
  }

  @SuppressWarnings("unchecked")
  private ApimResponseEnvelope<TrpGetInvestGlApimDataItem> envelopeWith(
      String errMessage, List<TrpGetInvestGlApimDataItem> data) {
    return ApimResponseEnvelope.<TrpGetInvestGlApimDataItem>builder()
        .response(
            ApimResponseBody.<TrpGetInvestGlApimDataItem>builder()
                .errMessage(errMessage)
                .data(data)
                .build())
        .build();
  }

  @SuppressWarnings("unchecked")
  private void givenExecutorReturns(ApimResponseEnvelope<TrpGetInvestGlApimDataItem> envelope) {
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.just(envelope));
  }

  @SuppressWarnings("unchecked")
  private void givenExecutorErrors(Throwable error) {
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.error(error));
  }

  // ----- success path -----------------------------------------------------------

  @Test
  void fetchSucceedsWithValidSingleDataItem() {
    TrpGetInvestGlApimDataItem item =
        new TrpGetInvestGlApimDataItem(
            LocalDate.of(2026, 1, 1), List.of(fund("A", 1, "10.00"), fund("B", 2, "5.00")));
    givenExecutorReturns(envelopeWith("", List.of(item)));
    StepVerifier.create(newAdapter().fetch(command()))
        .assertNext(
            data -> {
              assertThat(data.priceDate()).isEqualTo(LocalDate.of(2026, 1, 1));
              assertThat(data.funds()).hasSize(2);
            })
        .verifyComplete();
  }

  // ----- APIM error responses -----------------------------------------------------

  @Test
  void fetchWithApimBusinessErrorMapsToSystemFailure() {
    givenExecutorReturns(envelopeWith("Some downstream business error", null));
    StepVerifier.create(newAdapter().fetch(command()))
        .expectErrorSatisfies(
            ex -> {
              assertThat(ex).isInstanceOf(AccountGainLossRetrievalException.class);
              assertThat(((AccountGainLossRetrievalException) ex).reason())
                  .isEqualTo(AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE);
            })
        .verify();
  }

  @Test
  void fetchWithEmptyDataArrayMapsToSystemFailure() {
    givenExecutorReturns(envelopeWith("", List.of()));
    StepVerifier.create(newAdapter().fetch(command()))
        .expectError(AccountGainLossRetrievalException.class)
        .verify();
  }

  @Test
  void fetchWithMultipleDataItemsMapsToSystemFailure() {
    TrpGetInvestGlApimDataItem item1 =
        new TrpGetInvestGlApimDataItem(LocalDate.of(2026, 1, 1), List.of());
    TrpGetInvestGlApimDataItem item2 =
        new TrpGetInvestGlApimDataItem(LocalDate.of(2026, 1, 2), List.of());
    givenExecutorReturns(envelopeWith("", List.of(item1, item2)));
    StepVerifier.create(newAdapter().fetch(command()))
        .expectError(AccountGainLossRetrievalException.class)
        .verify();
  }

  @Test
  void fetchWithInvalidFundContentMapsToSystemFailure() {
    TrpGetInvestGlFund invalidFund =
        new TrpGetInvestGlFund("A", null, null, 1, new BigDecimal("1.00"));
    TrpGetInvestGlApimDataItem item =
        new TrpGetInvestGlApimDataItem(LocalDate.of(2026, 1, 1), List.of(invalidFund));
    givenExecutorReturns(envelopeWith("", List.of(item)));
    StepVerifier.create(newAdapter().fetch(command()))
        .expectError(AccountGainLossRetrievalException.class)
        .verify();
  }

  // ----- transport / unexpected errors ---------------------------------------------

  @Test
  void fetchTransportErrorMapsToSystemFailure() {
    givenExecutorErrors(new IllegalStateException("boom"));
    StepVerifier.create(newAdapter().fetch(command()))
        .expectErrorSatisfies(
            ex ->
                assertThat(((AccountGainLossRetrievalException) ex).reason())
                    .isEqualTo(AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE))
        .verify();
  }
}
