package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonPricesWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request.FundComparisonPricesRequest;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceRow;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Route and envelope tests for {@code POST /api/v1/fund-comparison/prices}. The real {@link
 * FundComparisonPricesUseCase} is wired with stubbed ports: options that offer FAEU and FBAU with
 * the standard time ranges, no session (so the cache is never used), and a counting price port.
 */
class FundComparisonPricesControllerTest {

  private static final String ROUTE = "/api/v1/fund-comparison/prices";
  private static final AuthenticatedSessionCookieReader NO_SESSION = exchange -> Optional.empty();
  private static final MockServerWebExchange EXCHANGE =
      MockServerWebExchange.from(MockServerHttpRequest.post(ROUTE).build());
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 15);
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final AtomicReference<FetchFundComparisonPricesCommand> portCommand =
      new AtomicReference<>();
  private final AtomicInteger calls = new AtomicInteger();

  private static FundComparisonPricesUseCase pricesUseCase(
      FundComparisonPriceSourcePort sourcePort) {
    ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(REFERENCE_DATE);
    FundComparisonOptionsSourcePort offered =
        cmd ->
            Mono.just(
                new RawFundComparisonOptionsData(
                    "10D,1M,3M,6M,1Y,YTD,3Y",
                    List.of(
                        new RawFundComparisonOption("FAEU", "A Fund", ""),
                        new RawFundComparisonOption("FBAU", "B Fund", ""))));
    var cache = mock(FundComparisonOptionsCacheService.class);
    return new FundComparisonPricesUseCase(
        sourcePort, referenceDatePort, new FundComparisonOptionsUseCase(offered, cache), cache);
  }

  private FundComparisonPricesUseCase useCase(RawFundPriceData rawData) {
    return pricesUseCase(
        portCmd -> {
          calls.incrementAndGet();
          portCommand.set(portCmd);
          return Mono.just(rawData);
        });
  }

  private FundComparisonPricesUseCase failingUseCase(RuntimeException error) {
    return pricesUseCase(
        portCmd -> {
          calls.incrementAndGet();
          return Mono.error(error);
        });
  }

  private static RawFundPriceRow row(String fundCode, LocalDate date, String bid) {
    return new RawFundPriceRow(
        fundCode, "Name", "Name", date, "HKD", new BigDecimal(bid), new BigDecimal("1"), "3");
  }

  private static PortalAccessContext context() {
    return new PortalAccessContext(
        new ActorContext("U", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            "ACC-1", "DB", "00000008802", "0230", "T", "OE", TermStatus.BLANK, null));
  }

  private static CurrentPortalAccessContextResolver resolver(Mono<PortalAccessContext> current) {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return current;
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return current;
      }
    };
  }

  private static FundComparisonPricesController controller(FundComparisonPricesUseCase useCase) {
    return new FundComparisonPricesController(
        useCase,
        resolver(Mono.just(context())),
        new FundComparisonPricesWebMapper(FORMATTER),
        NO_SESSION);
  }

  private static ApiExceptionHandler advice() {
    ErrorMessageResolver resolver = (code, locale, accountEnv, trustCode, schemeType) -> code;
    return new ApiExceptionHandler(
        resolver,
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties())),
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry());
  }

  private static WebTestClient client(FundComparisonPricesUseCase useCase) {
    return WebTestClient.bindToController(controller(useCase)).build();
  }

  private static WebTestClient clientWithAdvice(FundComparisonPricesUseCase useCase) {
    return WebTestClient.bindToController(controller(useCase)).controllerAdvice(advice()).build();
  }

  @Test
  void returnsTheSuccessEnvelopeAsJsonWithTheDataUnderResultData() {
    var rawData = new RawFundPriceData(List.of(row("FAEU", LocalDate.of(2026, 10, 6), "159.7628")));

    client(useCase(rawData))
        .post()
        .uri(ROUTE)
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU"), "10D"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.result.data.fromDate")
        .isEqualTo("06/10/2026")
        .jsonPath("$.result.data.toDate")
        .isEqualTo("15/10/2026")
        .jsonPath("$.result.data.series[0].fundCode")
        .isEqualTo("FAEU")
        .jsonPath("$.result.data.series[0].prices[0].priceDate")
        .isEqualTo("06/10/2026")
        .jsonPath("$.result.data.series[0].prices[0].unitPrice")
        .isEqualTo(159.7628)
        .jsonPath("$.messages")
        .isArray()
        .jsonPath("$.errors")
        .isArray();
  }

  @Test
  void forwardsPolicyNoAccountEnvActorUserRoleAndJoinedFundListToThePortCommand() {
    client(useCase(new RawFundPriceData(List.of())))
        .post()
        .uri(ROUTE)
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU", "FBAU"), "10D"))
        .exchange()
        .expectStatus()
        .isOk();

    var captured = portCommand.get();
    assertThat(captured.policyNo()).isEqualTo("00000008802");
    assertThat(captured.accountEnv()).isEqualTo("DB");
    assertThat(captured.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(captured.fundList()).isEqualTo("FAEU,FBAU");
    assertThat(calls).hasValue(1);
  }

  @Test
  void aValidationFailureIsABadRequestAndTheUseCaseIsNeverCalled() {
    clientWithAdvice(useCase(new RawFundPriceData(List.of())))
        .post()
        .uri(ROUTE)
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
        .isEqualTo("err.fund-comparison.request.invalid");

    assertThat(calls).hasValue(0);
  }

  @Test
  void aMissingRequestBodyIsABadRequestUsingTheSameValidationCode() {
    clientWithAdvice(useCase(new RawFundPriceData(List.of())))
        .post()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.request.invalid");

    assertThat(calls).hasValue(0);
  }

  @Test
  void aRetrievalFailureIsAServiceUnavailableSystemErrorWithACodeAndNoMessage() {
    clientWithAdvice(failingUseCase(FundComparisonRetrievalException.systemFailure()))
        .post()
        .uri(ROUTE)
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU"), "10D"))
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.retrieval.unavailable");
  }

  @Test
  void anUnofferedTimeRangeIsAServiceUnavailableAndThePricesAreNeverFetched() {
    clientWithAdvice(useCase(new RawFundPriceData(List.of())))
        .post()
        .uri(ROUTE)
        .bodyValue(new FundComparisonPricesRequest(List.of("FAEU"), "10Y"))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-comparison.retrieval.unavailable");

    assertThat(calls).hasValue(0);
  }

  @Test
  void theUseCaseIsNotCalledWhenTheContextCannotBeResolved() {
    var controller =
        new FundComparisonPricesController(
            useCase(new RawFundPriceData(List.of())),
            resolver(Mono.error(new IllegalStateException("no context"))),
            new FundComparisonPricesWebMapper(FORMATTER),
            NO_SESSION);

    StepVerifier.create(
            controller.getPrices(new FundComparisonPricesRequest(List.of("FAEU"), "10D"), EXCHANGE))
        .expectErrorMatches(IllegalStateException.class::isInstance)
        .verify();

    assertThat(calls).hasValue(0);
  }

  @Test
  void theConstructorRequiresEveryDependency() {
    var useCase = useCase(new RawFundPriceData(List.of()));
    var resolver = resolver(Mono.just(context()));
    var mapper = new FundComparisonPricesWebMapper(FORMATTER);

    assertThatThrownBy(() -> new FundComparisonPricesController(null, resolver, mapper, NO_SESSION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("useCase");
    assertThatThrownBy(() -> new FundComparisonPricesController(useCase, null, mapper, NO_SESSION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("contextResolver");
    assertThatThrownBy(
            () -> new FundComparisonPricesController(useCase, resolver, null, NO_SESSION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("webMapper");
    assertThatThrownBy(() -> new FundComparisonPricesController(useCase, resolver, mapper, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sessionCookieReader");
  }
}
