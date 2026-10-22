package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonOptionsMapper;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Route and envelope tests, mirroring {@code FundFactSheetControllerTest}'s exact structure (script
 * 042 evidence).
 *
 * <p>Corrected (script 047, closing a confirmed Clean Architecture violation): {@code
 * FundComparisonOptionsUseCase} is now constructed with ONLY a {@link
 * FundComparisonOptionsSourcePort} (no mapper); the controller takes the REAL {@link
 * FundComparisonOptionsMapper} as its own third constructor dependency, mirroring {@code
 * FundFactSheetController}'s exact confirmed parameter order (useCase, contextResolver, webMapper).
 * All test scenarios are unchanged from before the correction -- only the construction calls
 * differ.
 */
class FundComparisonOptionsControllerTest {

  private static final String ROUTE = "/api/v1/fund-comparison/options";
  private static final com.bct.ngtpa.apiservice.application.usecase.fundcomparison
          .FundComparisonOptionsCacheService
      NO_CACHE =
          org.mockito.Mockito.mock(
              com.bct.ngtpa.apiservice.application.usecase.fundcomparison
                  .FundComparisonOptionsCacheService.class);
  private static final com.bct.ngtpa.apiservice.adapter.in.web.auth.session
          .AuthenticatedSessionCookieReader
      NO_SESSION = exchange -> java.util.Optional.empty();
  private static final org.springframework.mock.web.server.MockServerWebExchange EXCHANGE =
      org.springframework.mock.web.server.MockServerWebExchange.from(
          org.springframework.mock.http.server.reactive.MockServerHttpRequest.post(ROUTE).build());

  private final AtomicReference<FetchFundComparisonOptionsCommand> command =
      new AtomicReference<>();
  private final AtomicInteger calls = new AtomicInteger();

  private FundComparisonOptionsUseCase useCase(RawFundComparisonOptionsData raw) {
    FundComparisonOptionsSourcePort sourcePort =
        cmd -> {
          calls.incrementAndGet();
          command.set(cmd);
          return Mono.just(raw);
        };
    return new FundComparisonOptionsUseCase(sourcePort, NO_CACHE);
  }

  private FundComparisonOptionsUseCase failingUseCase(RuntimeException error) {
    FundComparisonOptionsSourcePort sourcePort =
        cmd -> {
          calls.incrementAndGet();
          return Mono.error(error);
        };
    return new FundComparisonOptionsUseCase(sourcePort, NO_CACHE);
  }

  private static RawFundComparisonOptionsData raw(
      String rawTimeRange, RawFundComparisonOption... funds) {
    return new RawFundComparisonOptionsData(rawTimeRange, List.of(funds));
  }

  private static RawFundComparisonOption fund(String code, String name, String chinName) {
    return new RawFundComparisonOption(code, name, chinName);
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

  private static FundComparisonOptionsController controller(FundComparisonOptionsUseCase useCase) {
    return new FundComparisonOptionsController(
        useCase, resolver(Mono.just(context())), new FundComparisonOptionsMapper(), NO_SESSION);
  }

  private static ApiExceptionHandler advice() {
    ErrorMessageResolver resolver = (code, locale, accountEnv, trustCode, schemeType) -> code;
    return new ApiExceptionHandler(
        resolver,
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties())),
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry());
  }

  private static WebTestClient client(FundComparisonOptionsUseCase useCase) {
    return WebTestClient.bindToController(controller(useCase)).build();
  }

  private static WebTestClient clientWithAdvice(FundComparisonOptionsUseCase useCase) {
    return WebTestClient.bindToController(controller(useCase)).controllerAdvice(advice()).build();
  }

  @Test
  void returnsTheSuccessEnvelopeAsJsonWithTheDataUnderResultData() {
    var data = raw("10D,1M", fund("FAEU", "Fidelity Advantage Portfolio Fund", ""));

    client(useCase(data))
        .post()
        .uri(ROUTE)
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
        .jsonPath("$.result.data.defaultTimeRange")
        .isEqualTo("10D")
        .jsonPath("$.result.data.funds[0].fundCode")
        .isEqualTo("FAEU")
        .jsonPath("$.result.data.funds[0].fundName")
        .isEqualTo("Fidelity Advantage Portfolio Fund")
        .jsonPath("$.messages")
        .isArray()
        .jsonPath("$.errors")
        .isArray();
  }

  @Test
  void forwardsPolicyNoAccountEnvAndActorUserRoleFromTheTrustedAccountContext() {
    var data = raw("10D", fund("FAEU", "Name", ""));

    client(useCase(data)).post().uri(ROUTE).exchange().expectStatus().isOk();

    var captured = command.get();
    assertThat(captured.policyNo()).isEqualTo("00000008802");
    assertThat(captured.accountEnv()).isEqualTo("DB");
    assertThat(captured.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(calls).hasValue(1);
  }

  @Test
  void withoutAcceptLanguageDefaultsToEnglish() {
    var data = raw("10D", fund("FAEU", "English Name", "Chinese Name"));

    client(useCase(data))
        .post()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.funds[0].fundName")
        .isEqualTo("English Name");
  }

  @Test
  void acceptLanguageZhHkSelectsTheChineseFundName() {
    var data = raw("10D", fund("FAEU", "English Name", "Chinese Name"));

    client(useCase(data))
        .post()
        .uri(ROUTE)
        .header("Accept-Language", "zh_HK")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.funds[0].fundName")
        .isEqualTo("Chinese Name");
  }

  @Test
  void aRetrievalFailureIsAServiceUnavailableSystemErrorWithACodeAndNoMessage() {
    clientWithAdvice(failingUseCase(FundComparisonRetrievalException.systemFailure()))
        .post()
        .uri(ROUTE)
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
  void theUseCaseIsNotCalledWhenTheContextCannotBeResolved() {
    var data = raw("10D", fund("FAEU", "Name", ""));
    var controller =
        new FundComparisonOptionsController(
            useCase(data),
            resolver(Mono.error(new IllegalStateException("no context"))),
            new FundComparisonOptionsMapper(),
            NO_SESSION);

    StepVerifier.create(controller.getOptions(null, EXCHANGE))
        .expectErrorMatches(IllegalStateException.class::isInstance)
        .verify();

    assertThat(calls).hasValue(0);
  }

  @Test
  void theConstructorRequiresEveryDependency() {
    var data = raw("10D", fund("FAEU", "Name", ""));
    var useCase = useCase(data);
    var resolver = resolver(Mono.just(context()));
    var mapper = new FundComparisonOptionsMapper();

    assertThatThrownBy(
            () -> new FundComparisonOptionsController(null, resolver, mapper, NO_SESSION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("useCase");
    assertThatThrownBy(() -> new FundComparisonOptionsController(useCase, null, mapper, NO_SESSION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("contextResolver");
    assertThatThrownBy(
            () -> new FundComparisonOptionsController(useCase, resolver, null, NO_SESSION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("webMapper");
    assertThatThrownBy(() -> new FundComparisonOptionsController(useCase, resolver, mapper, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sessionCookieReader");
  }
}
