package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem.FundInfoEntry;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem.FundPriceEntry;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceCommand;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first tests for {@code ApimFundPriceSourceAdapter}, the final piece of TRPGetFundPrice
 * fetching. Mirrors {@code ApimFundPriceModeSourceAdapterTest}'s exact style: mock {@link
 * ApimExchangeExecutor}, capture the built {@link ApimExchangeRequest}, assert on its from-date/
 * to-date/err-message-mapping behavior.
 *
 * <p>Confirmed design under test: the command carries ONE unmanipulated FE date plus {@code
 * mode}; THIS ADAPTER (not the command, not any use case) decides how to expand that date into
 * the downstream request's from-date/to-date -- DAILY: both equal the date; MONTHLY: first/last
 * calendar day of the date's month.
 */
class ApimFundPriceSourceAdapterTest {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final String PATH = "/ws/NGTPA/v1/TRPGetFundPrice";

  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);

  private ApimFundPriceSourceAdapter newAdapter() {
    return new ApimFundPriceSourceAdapter(executor, PATH, FORMATTER);
  }

  private FetchFundPriceCommand command(String mode, LocalDate date) {
    return new FetchFundPriceCommand("00000008802", date, mode, "DB", ActorUserRole.ROLE_USER_MBR);
  }

  private TrpGetFundPriceApimDataItem validItem() {
    return new TrpGetFundPriceApimDataItem(
        List.of(new FundPriceEntry("DUSM", "2026-05-05", new BigDecimal("1"), new BigDecimal("1"))),
        List.of(new FundInfoEntry("DUSM", "USD Fund", "USD Fund CN", 1, "HKD", "1")));
  }

  @SuppressWarnings("unchecked")
  private void stubSuccess(TrpGetFundPriceApimDataItem item) {
    var envelope = mock(ApimResponseEnvelope.class);
    var response = mock(com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody.class);
    when(response.getErrMessage()).thenReturn("");
    when(response.getData()).thenReturn(List.of(item));
    when(envelope.getResponse()).thenReturn(response);
    when(executor.postEnvelope(any())).thenReturn(Mono.just(envelope));
  }

  // ----- mode-dependent date expansion (the core new behavior) -----------------

  @Test
  void dailyModeSetsFromDateAndToDateBothEqualToTheCommandDate() {
    stubSuccess(validItem());
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);

    newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))).block();

    verify(executor).postEnvelope(captor.capture());
    var request = (com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest)
        captor.getValue().request();
    assertThat(request.fromDate()).isEqualTo("05/05/2026");
    assertThat(request.toDate()).isEqualTo("05/05/2026");
  }

  @Test
  void monthlyModeExpandsToFirstAndLastCalendarDayOfTheCommandDatesMonth() {
    stubSuccess(validItem());
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);

    newAdapter().fetchPrice(command("MONTHLY", LocalDate.of(2026, 2, 15))).block();

    verify(executor).postEnvelope(captor.capture());
    var request = (com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest)
        captor.getValue().request();
    assertThat(request.fromDate()).isEqualTo("01/02/2026");
    assertThat(request.toDate()).isEqualTo("28/02/2026"); // 2026 is not a leap year
  }

  @Test
  void monthlyModeHandlesLeapYearFebruaryCorrectly() {
    stubSuccess(validItem());
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);

    newAdapter().fetchPrice(command("MONTHLY", LocalDate.of(2028, 2, 10))).block();

    verify(executor).postEnvelope(captor.capture());
    var request = (com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest)
        captor.getValue().request();
    assertThat(request.toDate()).isEqualTo("29/02/2028");
  }

  @Test
  void policyNoIsPassedThroughFromTheCommand() {
    stubSuccess(validItem());
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);

    newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))).block();

    verify(executor).postEnvelope(captor.capture());
    var request = (com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest)
        captor.getValue().request();
    assertThat(request.policyNo()).isEqualTo("00000008802");
  }

  // ----- successful reconciliation delegation -----------------------------------

  @Test
  void successfulResponseDelegatesToTheValidatorAndReturnsReconciledData() {
    stubSuccess(validItem());

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .assertNext(
            result -> {
              assertThat(result.rows()).hasSize(1);
              assertThat(result.rows().get(0).fundCode()).isEqualTo("DUSM");
            })
        .verifyComplete();
  }

  // ----- error mapping (mirrors ApimFundPriceModeSourceAdapterTest's style) -----

  @SuppressWarnings("unchecked")
  @Test
  void wrongDataItemCountMapsToSystemFailure() {
    // Replaces the original nullEnvelopeMapsToSystemFailure test (script 099 fix): Mono.just(T)
    // throws NullPointerException by contract when T is null, so that test never actually
    // reached the adapter code under test -- and a Mono can never EMIT null through flatMap
    // in the first place, making the adapter's own envelope==null check unreachable dead code
    // (removed in the same fix). This test exercises a genuinely reachable failure path
    // instead: response.getData() returning something other than exactly one item.
    var envelope = mock(ApimResponseEnvelope.class);
    var response = mock(com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody.class);
    when(response.getErrMessage()).thenReturn("");
    when(response.getData()).thenReturn(List.of());
    when(envelope.getResponse()).thenReturn(response);
    when(executor.postEnvelope(any())).thenReturn(Mono.just(envelope));

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .expectError(FundPriceRetrievalException.class)
        .verify();
  }

  @SuppressWarnings("unchecked")
  @Test
  void nullResponseInsideEnvelopeMapsToSystemFailure() {
    // Confirms the still-real, still-reachable check: a non-null envelope whose getResponse()
    // itself returns null.
    var envelope = mock(ApimResponseEnvelope.class);
    when(envelope.getResponse()).thenReturn(null);
    when(executor.postEnvelope(any())).thenReturn(Mono.just(envelope));

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .expectError(FundPriceRetrievalException.class)
        .verify();
  }

  @SuppressWarnings("unchecked")
  @Test
  void nonblankErrMessageMapsToSystemFailure() {
    var envelope = mock(ApimResponseEnvelope.class);
    var response = mock(com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody.class);
    when(response.getErrMessage()).thenReturn("err.tpa.no.fund.price");
    when(envelope.getResponse()).thenReturn(response);
    when(executor.postEnvelope(any())).thenReturn(Mono.just(envelope));

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .expectError(FundPriceRetrievalException.class)
        .verify();
  }

  @SuppressWarnings("unchecked")
  @Test
  void validatorReconciliationFailureIsWrappedAsFundPriceRetrievalException() {
    // An item that fails reconciliation (mismatched fund-code) should surface as the same
    // single generic exception, not leak the validator's internal failure.
    var invalidItem =
        new TrpGetFundPriceApimDataItem(
            List.of(new FundPriceEntry("UNMATCHED", "2026-05-05", BigDecimal.ONE, BigDecimal.ONE)),
            List.of(new FundInfoEntry("DUSM", "name", "name", 1, "HKD", "1")));
    stubSuccess(invalidItem);

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .expectError(FundPriceRetrievalException.class)
        .verify();
  }

  @Test
  void transportErrorMapsToSystemFailureNotRawException() {
    when(executor.postEnvelope(any()))
        .thenReturn(Mono.error(new IllegalStateException("connection refused: 10.0.0.1:6379")));

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .expectErrorSatisfies(
            error -> assertThat(error).isInstanceOf(FundPriceRetrievalException.class))
        .verify();
  }

  // ----- constructor validation --------------------------------------------------

  @Test
  void blankPathThrows() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new ApimFundPriceSourceAdapter(executor, "  ", FORMATTER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullExecutorThrows() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new ApimFundPriceSourceAdapter(null, PATH, FORMATTER))
        .isInstanceOf(NullPointerException.class);
  }
}
