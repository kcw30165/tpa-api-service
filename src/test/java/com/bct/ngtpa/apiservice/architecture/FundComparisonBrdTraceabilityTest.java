package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.FundComparisonOptionsController;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.FundComparisonPricesController;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonOptionsMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonPricesWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request.FundComparisonPricesRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.ApimFundComparisonOptionsSourceAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.FundComparisonOptionsApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto.TrpGetFundCmpOptApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto.TrpGetFundCmpOptApimFundItem;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceInvalidDateException;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsCacheService;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonPricesUseCase;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Milestone 4, sub-task 4.2 -- BRD traceability pass, per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.5 and the real,
 * verbatim-confirmed text of docs/design/13-fund-comparison/05-test-and-traceability.md section 5.6
 * ("Exact error-contract regression tests").
 *
 * <p>Confirmed, precise, per-scenario coverage check (scripts 067-068 evidence): every scenario
 * except #2 previously had ONLY mapper/parser-level (internal unit) proof, never a full
 * HTTP-contract proof (real status code + real JSON response body).
 *
 * <p>Scenario 3 (corrected, script 070 root-cause investigation): a first attempt used a FAKE
 * {@link FundComparisonOptionsSourcePort} whose lambda directly constructed an already-invalid
 * {@link RawFundComparisonOptionsData} -- bypassing {@link
 * ApimFundComparisonOptionsSourceAdapter}'s own try/catch(RuntimeException) translation entirely
 * and violating that port's real contract (confirmed: the only real implementation NEVER returns an
 * invalid RawFundComparisonOptionsData -- it always catches construction failures itself and emits
 * {@link FundComparisonRetrievalException}). This is NOT a production bug: the real adapter already
 * handles a blank/null downstream time-range correctly (confirmed, and already independently tested
 * by {@code ApimFundComparisonOptionsSourceAdapterTest#nullTimeRangeFailsClosed}/{@code
 * #blankTimeRangeFailsClosed}). Scenario 3 here instead wires the REAL adapter (mocking only {@link
 * ApimExchangeExecutor}, exactly matching that adapter test's own established pattern) into the
 * real use case and real controller, simulating a genuinely malformed DOWNSTREAM response.
 *
 * <p>This class adds NO new production behavior -- it proves, end-to-end through the real
 * controllers (and, for scenario 3, the real adapter too), that all 7 section 5.6 scenarios hold at
 * the full public HTTP contract, closing the traceability gap (not a functional gap).
 */
class FundComparisonBrdTraceabilityTest {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 15);
  private static final String OPTIONS_PATH = "/ws/NGTPA/v1/TRPGetFundCmpOpt";

  // ----- shared fixtures: prices flow ---------------------------------------------
  private static PortalAccessContext context() {
    return new PortalAccessContext(
        new ActorContext("U", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            "ACC-1", "DB", "00000008802", "0230", "T", "OE", TermStatus.BLANK, null));
  }

  private static CurrentPortalAccessContextResolver resolver() {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.just(context());
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context());
      }
    };
  }

  private static ApiExceptionHandler advice() {
    ErrorMessageResolver errorResolver = (code, locale, accountEnv, trustCode, schemeType) -> code;
    return new ApiExceptionHandler(
        errorResolver,
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties())));
  }

  private WebTestClient pricesClient(FundComparisonPriceSourcePort sourcePort) {
    ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(REFERENCE_DATE);
    var useCase = pricesUseCase(sourcePort, referenceDatePort);
    var controller =
        new FundComparisonPricesController(
            useCase,
            resolver(),
            new FundComparisonPricesWebMapper(FORMATTER),
            exchange -> Optional.empty());
    return WebTestClient.bindToController(controller).controllerAdvice(advice()).build();
  }

  /** Real prices use case whose options offer every code these scenarios use; no session. */
  private static FundComparisonPricesUseCase pricesUseCase(
      FundComparisonPriceSourcePort sourcePort, ReferenceDatePort referenceDatePort) {
    FundComparisonOptionsSourcePort offered =
        cmd ->
            Mono.just(
                optionsRaw(
                    "10D,1M,3M,6M,1Y,YTD,3Y",
                    new RawFundComparisonOption("FAEU", "A Fund", ""),
                    new RawFundComparisonOption("FBAU", "B Fund", "")));
    var cache = mock(FundComparisonOptionsCacheService.class);
    return new FundComparisonPricesUseCase(
        sourcePort, referenceDatePort, new FundComparisonOptionsUseCase(offered, cache), cache);
  }

  // ----- shared fixtures: options flow (fake-port based, for scenarios other than 3) ----------
  private static RawFundComparisonOptionsData optionsRaw(
      String rawTimeRange, RawFundComparisonOption... funds) {
    return new RawFundComparisonOptionsData(rawTimeRange, List.of(funds));
  }

  private WebTestClient optionsClient(FundComparisonOptionsSourcePort sourcePort) {
    var useCase =
        new FundComparisonOptionsUseCase(sourcePort, mock(FundComparisonOptionsCacheService.class));
    var controller =
        new FundComparisonOptionsController(
            useCase, resolver(), new FundComparisonOptionsMapper(), exchange -> Optional.empty());
    return WebTestClient.bindToController(controller).controllerAdvice(advice()).build();
  }

  // ----- scenario-3-only fixture: REAL adapter wired with a mocked executor -------------------
  @SuppressWarnings("unchecked")
  private static ApimResponseEnvelope<TrpGetFundCmpOptApimDataItem> envelopeWith(String timeRange) {
    var fundItem =
        new TrpGetFundCmpOptApimFundItem("FAEU", "Fidelity Advantage Portfolio Fund", "");
    var dataItem = new TrpGetFundCmpOptApimDataItem(timeRange, List.of(fundItem));
    var body =
        ApimResponseBody.<TrpGetFundCmpOptApimDataItem>builder()
            .errMessage("")
            .data(List.of(dataItem))
            .build();
    return ApimResponseEnvelope.<TrpGetFundCmpOptApimDataItem>builder().response(body).build();
  }

  private WebTestClient optionsClientWithRealAdapter(String downstreamTimeRange) {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    doReturn(Mono.just(envelopeWith(downstreamTimeRange))).when(executor).postEnvelope(any());
    // The 2-arg (executor, String path) constructor is package-private (confirmed,
    // script 070/071 compile error) -- this test lives in a different package
    // (com.bct.ngtpa.apiservice.architecture) from the adapter
    // (adapter.out.apim.fundcomparison), so it must use the PUBLIC
    // (executor, FundComparisonOptionsApimProperties) constructor instead,
    // mirroring ApimFundComparisonOptionsSourceAdapterTest's own
    // productionConstructorDelegatesToPackagePrivateConstructor test exactly.
    var properties = new FundComparisonOptionsApimProperties();
    properties.setPath(OPTIONS_PATH);
    var realAdapter = new ApimFundComparisonOptionsSourceAdapter(executor, properties);
    return optionsClient(realAdapter);
  }

  // ===== Scenario 1: missing/blank/invalid REQUEST time range -> request.invalid ONLY =====
  @Test
  void scenario1_blankRequestTimeRangeUsesOnlyRequestInvalidCode() {
    FundComparisonPriceSourcePort sourcePort = cmd -> Mono.just(new RawFundPriceData(List.of()));

    pricesClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/prices")
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU"), "   "))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.request.invalid")
        .jsonPath("$.errors.length()")
        .isEqualTo(1);
  }

  // ===== Scenario 2: >5 unique REQUEST fund codes -> request.invalid ONLY =====
  @Test
  void scenario2_moreThanFiveRequestFundCodesUsesOnlyRequestInvalidCode() {
    FundComparisonPriceSourcePort sourcePort = cmd -> Mono.just(new RawFundPriceData(List.of()));

    pricesClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/prices")
        .bodyValue(new FundComparisonPricesRequest(List.of("A", "B", "C", "D", "E", "F"), "10D"))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.request.invalid")
        .jsonPath("$.errors.length()")
        .isEqualTo(1);
  }

  // ===== Scenario 3 (corrected): missing/blank DOWNSTREAM options time range ->
  // retrieval.unavailable ONLY, exercised through the REAL adapter =====
  @Test
  void scenario3_blankDownstreamOptionsTimeRangeUsesOnlyRetrievalUnavailableCode() {
    optionsClientWithRealAdapter("   ")
        .post()
        .uri("/api/v1/fund-comparison/options")
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.retrieval.unavailable")
        .jsonPath("$.errors.length()")
        .isEqualTo(1);
  }

  @Test
  void scenario3_missingDownstreamOptionsTimeRangeUsesOnlyRetrievalUnavailableCode() {
    optionsClientWithRealAdapter(null)
        .post()
        .uri("/api/v1/fund-comparison/options")
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.retrieval.unavailable");
  }

  // ===== Scenario 4: mixed valid/invalid DOWNSTREAM tokens -> succeeds with valid tokens =====
  @Test
  void scenario4_mixedValidAndInvalidDownstreamTokensSucceedsWithTheValidOnes() {
    FundComparisonOptionsSourcePort sourcePort =
        cmd ->
            Mono.just(optionsRaw("10D,bogus,1M", new RawFundComparisonOption("FAEU", "Name", "")));

    optionsClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/options")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.result.data.timeRanges[0]")
        .isEqualTo("10D")
        .jsonPath("$.result.data.timeRanges[1]")
        .isEqualTo("1M")
        .jsonPath("$.result.data.timeRanges.length()")
        .isEqualTo(2);
  }

  // ===== Scenario 5: all-invalid DOWNSTREAM tokens -> HTTP 503 =====
  @Test
  void scenario5_allInvalidDownstreamTokensReturnsHttp503() {
    FundComparisonOptionsSourcePort sourcePort =
        cmd ->
            Mono.just(
                optionsRaw("bogus,also-bogus", new RawFundComparisonOption("FAEU", "Name", "")));

    optionsClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/options")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.retrieval.unavailable");
  }

  // ===== Scenario 6: raw downstream err-message is never exposed =====
  @Test
  void scenario6_rawDownstreamErrMessageIsNeverExposedInThePricesResponseBody() {
    FundComparisonPriceSourcePort sourcePort =
        cmd -> Mono.error(FundComparisonRetrievalException.systemFailure());

    pricesClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/prices")
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU"), "10D"))
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody(String.class)
        .value(
            body -> {
              assertThat(body).doesNotContain("err.tpa.", "connection refused", "Connection reset");
              assertThat(body).contains("err.fund-comparison.retrieval.unavailable");
            });
  }

  @Test
  void scenario6_rawDownstreamErrMessageIsNeverExposedInTheOptionsResponseBody() {
    FundComparisonOptionsSourcePort sourcePort =
        cmd -> Mono.error(FundComparisonRetrievalException.systemFailure());

    optionsClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/options")
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody(String.class)
        .value(
            body -> {
              assertThat(body).doesNotContain("err.tpa.", "connection refused", "Connection reset");
              assertThat(body).contains("err.fund-comparison.retrieval.unavailable");
            });
  }

  // ===== Scenario 7: legitimate empty FundPrice -> HTTP 200 with empty series =====
  @Test
  void scenario7_legitimateEmptyFundPriceIsHttp200WithEmptySeries() {
    FundComparisonPriceSourcePort sourcePort = cmd -> Mono.just(new RawFundPriceData(List.of()));

    pricesClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/prices")
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU", "FBAU"), "10D"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.result.data.series")
        .isArray()
        .jsonPath("$.result.data.series.length()")
        .isEqualTo(0);
  }

  // ===== Scenario 8 (M5.4, D8): an unoffered time range is HTTP 503; prices never fetched =====
  @Test
  void scenario8_unofferedTimeRangeIsHttp503AndThePricesAreNeverFetched() {
    var priceCalls = new AtomicInteger();
    FundComparisonPriceSourcePort sourcePort =
        cmd -> {
          priceCalls.incrementAndGet();
          return Mono.just(new RawFundPriceData(List.of()));
        };
    pricesClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/prices")
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU"), "10Y"))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.retrieval.unavailable")
        .jsonPath("$.errors.length()")
        .isEqualTo(1);
    assertThat(priceCalls).hasValue(0);
  }

  // ===== Scenario 9 (D6, script 096): TRPGetFundPrice err.tpa.invalid.date maps to HTTP 400,
  // not HTTP 503, for Fund Comparison prices =====
  @Test
  void scenario9_downstreamInvalidDateMapsToRequestInvalidNotRetrievalUnavailable() {
    FundComparisonPriceSourcePort sourcePort =
        cmd -> Mono.error(FundPriceInvalidDateException.forInvalidDate());

    pricesClient(sourcePort)
        .post()
        .uri("/api/v1/fund-comparison/prices")
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU"), "10D"))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.request.invalid");
  }
}
