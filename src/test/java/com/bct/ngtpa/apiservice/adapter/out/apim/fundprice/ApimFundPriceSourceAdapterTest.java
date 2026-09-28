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
 * <p>Confirmed design under test (corrected, Nick): the command carries ONE unmanipulated FE
 * date plus {@code mode}. BOTH DAILY and MONTHLY send that exact date as both the
 * downstream request's from-date and to-date -- mode has no effect on this adapter's
 * downstream request shape (an earlier version incorrectly expanded MONTHLY into a
 * first/last-calendar-day range; there is no such expansion).
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
  void monthlyModeSetsFromDateAndToDateBothEqualToTheCommandDateJustLikeDaily() {
    // Corrected (Nick): MONTHLY does NOT expand into a first/last-calendar-day range.
    // FE always submits one exact date; Java sends that same date as both from-date
    // and to-date, identically to DAILY. mode has no effect on this adapter's
    // downstream request shape.
    stubSuccess(validItem());
    ArgumentCaptor<ApimExchangeRequest<?, ?>> captor =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    newAdapter().fetchPrice(command("MONTHLY", LocalDate.of(2026, 2, 15))).block();
    verify(executor).postEnvelope(captor.capture());
    var request =
        (com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest)
            captor.getValue().request();
    assertThat(request.fromDate()).isEqualTo("15/02/2026");
    assertThat(request.toDate()).isEqualTo("15/02/2026");
  }

  @Test
  void monthlyModeDoesNotExpandEvenAroundLeapFebruaryDates() {
    // Regression guard against reintroducing the incorrect first/last-calendar-day
    // expansion: a leap-February date must still pass through unexpanded.
    stubSuccess(validItem());
    ArgumentCaptor<ApimExchangeRequest<?, ?>> captor =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    newAdapter().fetchPrice(command("MONTHLY", LocalDate.of(2028, 2, 29))).block();
    verify(executor).postEnvelope(captor.capture());
    var request =
        (com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest)
            captor.getValue().request();
    assertThat(request.fromDate()).isEqualTo("29/02/2028");
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

  // ----- fetchPriceRange (export redesign, script 107): NO mode, explicit fromDate/toDate --------

  @Test
  void fetchPriceRangePassesFromDateAndToDateDirectlyWithNoModeLogic() {
    stubSuccess(validItem());
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);

    var rangeCommand =
        new com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceRangeCommand(
            "00000008802",
            LocalDate.of(2023, 9, 25),
            LocalDate.of(2026, 9, 25),
            "DB",
            ActorUserRole.ROLE_USER_MBR);

    newAdapter().fetchPriceRange(rangeCommand).block();

    verify(executor).postEnvelope(captor.capture());
    var request = (com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest)
        captor.getValue().request();
    assertThat(request.fromDate()).isEqualTo("25/09/2023");
    assertThat(request.toDate()).isEqualTo("25/09/2026");
    assertThat(request.policyNo()).isEqualTo("00000008802");
  }

  @Test
  void fetchPriceRangeDelegatesToTheSameValidatorAndErrorMapping() {
    stubSuccess(validItem());
    var rangeCommand =
        new com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceRangeCommand(
            "00000008802",
            LocalDate.of(2023, 9, 25),
            LocalDate.of(2026, 9, 25),
            "DB",
            ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(newAdapter().fetchPriceRange(rangeCommand))
        .assertNext(result -> assertThat(result.rows()).hasSize(1))
        .verifyComplete();
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

  @Test
  void nullPathThrows() {
    // Confirmed real gap (script 161/162): only blankPathThrows (" ") was previously
    // exercised; path == null was never independently tested, even though the guard
    // clause is "path == null || path.isBlank()" -- two genuinely distinct branches.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new ApimFundPriceSourceAdapter(executor, (String) null, FORMATTER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void publicProductionConstructorDelegatesToPropertiesPath() {
    // Confirmed real gap (script 161/162): the PUBLIC constructor Spring actually uses
    // in production -- (ApimExchangeExecutor, FundPriceApimProperties, DateTimeFormatter)
    // -- had 0.0% instruction coverage; every other test in this file uses only the
    // package-private test constructor.
    var properties = mock(FundPriceApimProperties.class);
    when(properties.getPath()).thenReturn(PATH);
    var adapter = new ApimFundPriceSourceAdapter(executor, properties, FORMATTER);
    stubSuccess(validItem());

    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    adapter.fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))).block();
    verify(executor).postEnvelope(captor.capture());
    assertThat(captor.getValue().path()).isEqualTo(PATH);
  }

  @SuppressWarnings("unchecked")
  @Test
  void nullErrMessageIsTreatedAsNoErrorAndProceedsToReconciliation() {
    // Confirmed real gap (script 161/162): errMessage == null was never independently
    // tested -- every existing test stubs getErrMessage() to "" or a real string. The
    // guard "errMessage != null && !errMessage.isEmpty()" has a genuinely distinct
    // errMessage==null branch that short-circuits to false (no error), letting
    // reconciliation proceed normally.
    var envelope = mock(ApimResponseEnvelope.class);
    var response = mock(com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody.class);
    when(response.getErrMessage()).thenReturn(null);
    when(response.getData()).thenReturn(List.of(validItem()));
    when(envelope.getResponse()).thenReturn(response);
    when(executor.postEnvelope(any())).thenReturn(Mono.just(envelope));

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .assertNext(result -> assertThat(result.rows()).hasSize(1))
        .verifyComplete();
  }

  @SuppressWarnings("unchecked")
  @Test
  void nullDataMapsToSystemFailure() {
    // Confirmed real gap (script 161/162): response.getData() returning null was never
    // independently tested -- the guard "items == null || items.size() != 1 ||
    // items.get(0) == null" has a genuinely distinct items==null branch.
    var envelope = mock(ApimResponseEnvelope.class);
    var response = mock(com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody.class);
    when(response.getErrMessage()).thenReturn("");
    when(response.getData()).thenReturn(null);
    when(envelope.getResponse()).thenReturn(response);
    when(executor.postEnvelope(any())).thenReturn(Mono.just(envelope));

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .expectError(FundPriceRetrievalException.class)
        .verify();
  }

  @SuppressWarnings("unchecked")
  @Test
  void firstDataItemNullMapsToSystemFailure() {
    // Confirmed real gap (script 161/162): a single-element data list whose sole
    // element is itself null was never independently tested -- the guard's third
    // OR clause, items.get(0) == null, was never exercised as true.
    var envelope = mock(ApimResponseEnvelope.class);
    var response = mock(com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody.class);
    when(response.getErrMessage()).thenReturn("");
    when(response.getData()).thenReturn(java.util.Collections.singletonList(null));
    when(envelope.getResponse()).thenReturn(response);
    when(executor.postEnvelope(any())).thenReturn(Mono.just(envelope));

    StepVerifier.create(newAdapter().fetchPrice(command("DAILY", LocalDate.of(2026, 5, 5))))
        .expectError(FundPriceRetrievalException.class)
        .verify();
  }
}
