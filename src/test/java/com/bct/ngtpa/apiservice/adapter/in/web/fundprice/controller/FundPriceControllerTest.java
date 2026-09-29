package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.controller;

import com.bct.ngtpa.apiservice.application.exception.FundPriceRequestInvalidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.mapper.FundPriceWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support.FundPriceSortingSupport;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceExportUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceQueryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceQueryUseCaseResult;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support.FundPriceWorkbookExporter;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceExportUseCaseResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first / implementation tests for FundPriceController's query endpoint.
 *
 * <p>CORRECTED (script 118): script 117's original version used a nonsensical Reactor-context
 * injection trick to supply a ServerWebExchange. The real, correct Spring WebFlux idiom (confirmed
 * script 116, mirroring LogoutController/PortalAccessContextWebFilter) is for the controller
 * method to receive ServerWebExchange as a normal parameter, which Spring injects automatically --
 * so this test now passes the mocked exchange directly as a method argument. The constructor call
 * is also corrected to 7 args (a DateTimeFormatter was added, needed to parse the FE date query
 * parameter -- this was missing from script 117's original design).
 */
class FundPriceControllerTest {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final FundPriceQueryUseCase queryUseCase = mock(FundPriceQueryUseCase.class);
  private final FundPriceExportUseCase exportUseCase = mock(FundPriceExportUseCase.class);
  private final FundPriceWorkbookExporter workbookExporter = mock(FundPriceWorkbookExporter.class);
  private final com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCase modeUseCase =
      mock(com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCase.class);
  private final com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort referenceDatePort =
      mock(com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort.class);
  private final FundPriceWebMapper webMapper = mock(FundPriceWebMapper.class);
  private final FundPriceSortingSupport sortingSupport = new FundPriceSortingSupport();
  private final CurrentPortalAccessContextResolver contextResolver =
      mock(CurrentPortalAccessContextResolver.class);
  private final AuthenticatedSessionCookieWriter sessionCookies =
      new AuthenticatedSessionCookieWriter(validCookieProperties());

  private static com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties
          .AuthenticatedCookie
      validCookieProperties() {
    var props =
        new com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties
            .AuthenticatedCookie();
    props.setName("portal_sid");
    return props;
  }

  private FundPriceController newController() {
    return new FundPriceController(
        queryUseCase,
        exportUseCase,
        webMapper,
        sortingSupport,
        contextResolver,
        sessionCookies,
        FORMATTER,
        workbookExporter,
        modeUseCase,
        referenceDatePort);
  }

  private PortalAccessContext validPortalAccessContext() {
    return new PortalAccessContext(
        new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            "ref-1", "DB", "00000008802", "cert-1", "trust-1", "scheme-1", null, null));
  }

  private MockServerWebExchange exchangeWithSessionCookie() {
    var request =
        MockServerHttpRequest.get("/api/v1/fund-price?date=05/05/2026")
            .cookie(new HttpCookie("portal_sid", "sid_abcdefghijklmnopqrstuvwx"))
            .build();
    return MockServerWebExchange.from(request);
  }

  private MockServerWebExchange exchangeWithoutSessionCookie() {
    var request = MockServerHttpRequest.get("/api/v1/fund-price?date=05/05/2026").build();
    return MockServerWebExchange.from(request);
  }

  @Test
  void resolvesSessionIdFromCookieAndCallsQueryUseCase() {
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));
    var rawResult =
        new FundPriceQueryResult(
            List.of(
                new FundPriceRowResult(
                    "DUSM", "n", "n", LocalDate.of(2026, 5, 5), "HKD", BigDecimal.ONE, BigDecimal.ONE, "5")),
            "BIDOFFER PRICE",
            "5");
    when(queryUseCase.query(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(FundPriceQueryUseCaseResult.applied(rawResult)));
    var mappedResponse =
        new com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceQueryResponse(
            List.of(), "5");
    when(webMapper.toQueryResponse(any(), any())).thenReturn(mappedResponse);

    var result =
        newController().getFundPrice("05/05/2026", null, exchangeWithSessionCookie()).block();

    assertThat(result).isNotNull();
    assertThat(result.maxRiskLevel()).isEqualTo("5");
  }

  @Test
  void missingSessionCookieMapsToError() {
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));

    StepVerifier.create(
            newController().getFundPrice("05/05/2026", null, exchangeWithoutSessionCookie()))
        .expectError(FundPriceRequestInvalidException.class)
        .verify();
  }

  @Test
  void invalidDateFormatMapsToError() {
    StepVerifier.create(
            newController().getFundPrice("not-a-date", null, exchangeWithSessionCookie()))
        .expectError(FundPriceRequestInvalidException.class)
        .verify();
  }
  @Test
  void blankSessionCookieValueMapsToError() {
    var request =
        MockServerHttpRequest.get("/api/v1/fund-price?date=05/05/2026")
            .cookie(new HttpCookie("portal_sid", "   "))
            .build();
    var exchange = MockServerWebExchange.from(request);
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));

    StepVerifier.create(newController().getFundPrice("05/05/2026", null, exchange))
        .expectError(FundPriceRequestInvalidException.class)
        .verify();
  }

  @Test
  void malformedSessionCookieValueMapsToError() {
    // Confirms the catch(IllegalArgumentException) branch inside resolveSessionId: a non-blank
    // cookie value that still fails AuthenticatedSessionId's own format validation.
    var request =
        MockServerHttpRequest.get("/api/v1/fund-price?date=05/05/2026")
            .cookie(new HttpCookie("portal_sid", "not-a-valid-session-id-format"))
            .build();
    var exchange = MockServerWebExchange.from(request);
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));

    StepVerifier.create(newController().getFundPrice("05/05/2026", null, exchange))
        .expectError(FundPriceRequestInvalidException.class)
        .verify();
  }

  @Test
  void blankDateParameterMapsToError() {
    StepVerifier.create(newController().getFundPrice("  ", null, exchangeWithSessionCookie()))
        .expectError(FundPriceRequestInvalidException.class)
        .verify();
  }

  @Test
  void nullDateParameterMapsToError() {
    StepVerifier.create(newController().getFundPrice(null, null, exchangeWithSessionCookie()))
        .expectError(FundPriceRequestInvalidException.class)
        .verify();
  }

  @Test
  void invalidDateOutcomeFromUseCaseMapsToError() {
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));
    when(queryUseCase.query(any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceQueryUseCaseResult
                    .of(
                        com.bct.ngtpa.apiservice.application.usecase.fundprice
                            .FundPriceUseCaseOutcome.INVALID_DATE)));

    StepVerifier.create(newController().getFundPrice("05/05/2026", null, exchangeWithSessionCookie()))
        .expectError(FundPriceRequestInvalidException.class)
        .verify();
  }

  @Test
  void systemFailureOutcomeFromUseCaseMapsToError() {
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));
    when(queryUseCase.query(any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceQueryUseCaseResult
                    .of(
                        com.bct.ngtpa.apiservice.application.usecase.fundprice
                            .FundPriceUseCaseOutcome.SYSTEM_FAILURE)));

    StepVerifier.create(newController().getFundPrice("05/05/2026", null, exchangeWithSessionCookie()))
        .expectError()
        .verify();
  }
  @Test
  void modeEndpointReturnsWrappedSuccessResponse() {
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));
    when(referenceDatePort.resolveReferenceDate(any()))
        .thenReturn(Mono.just(LocalDate.of(2026, 9, 18)));
    var metadata =
        new com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2026, 9, 18)), 36, "5");
    when(modeUseCase.fetchAndCacheMode(any(), any(), any()))
        .thenReturn(
            Mono.just(
                com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCaseResult
                    .applied(metadata)));
    when(webMapper.toModeResponse(any(), any()))
        .thenReturn(
            new com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceModeResponse(
                "DAILY", List.of("18/09/2026"), 36, "18/09/2026"));

    var response = newController().getFundPriceMode(exchangeWithSessionCookie()).block();

    assertThat(response).isNotNull();
    assertThat(response.result().data().mode()).isEqualTo("DAILY");
  }

  @Test
  void modeEndpointSystemFailureOutcomeMapsToError() {
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));
    when(referenceDatePort.resolveReferenceDate(any()))
        .thenReturn(Mono.just(LocalDate.of(2026, 9, 18)));
    when(modeUseCase.fetchAndCacheMode(any(), any(), any()))
        .thenReturn(
            Mono.just(
                com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCaseResult
                    .systemFailure()));

    StepVerifier.create(newController().getFundPriceMode(exchangeWithSessionCookie()))
        .expectError()
        .verify();
  }

  @Test
  void exportEndpointReturnsExcelBytesWithCorrectContentTypeAndFilename() {
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));
    var exportResult =
        new FundPriceExportResult(List.of(), "UNIT PRICE", "5", BigInteger.TWO);
    when(exportUseCase.export(any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceExportUseCaseResult
                    .applied(exportResult)));
    byte[] fakeBytes = new byte[] {1, 2, 3};
    when(workbookExporter.write(any())).thenReturn(fakeBytes);

    ResponseEntity<byte[]> response =
        newController().exportFundPrice(exchangeWithSessionCookie()).block();

    assertThat(response).isNotNull();
    assertThat(response.getBody()).isEqualTo(fakeBytes);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(
            org.springframework.http.MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .contains("fund_price_info.xlsx");
  }

  @Test
  void exportEndpointMissingSessionCookieMapsToError() {
    when(contextResolver.current()).thenReturn(Mono.just(validPortalAccessContext()));

    StepVerifier.create(newController().exportFundPrice(exchangeWithoutSessionCookie()))
        .expectError(FundPriceRequestInvalidException.class)
        .verify();
  }
}
