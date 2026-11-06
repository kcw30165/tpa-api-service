package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class InvestmentChoiceControllerTest {

  private static final String ROUTE = "/api/v1/investment-choice";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void returnsTheExactSuccessEnvelopeWithDataAndEmptyArrays() {
    WebTestClient client =
        client(
            command ->
                Mono.just(
                    new InvestmentChoiceResult(
                        "00000008802-0230",
                        new InvestmentChoiceDetails(
                            5, List.of(), List.of(), List.of(), List.of()))),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    client
        .get()
        .uri(ROUTE)
        .header("Accept-Language", "en")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "success": true,
              "status": "SUCCESS",
              "result": {
                "data": {
                  "accountNumber": "00000008802-0230",
                  "intervalPercentage": 5,
                  "switchSources": [],
                  "funds": [],
                  "history": [],
                  "pendingInstructions": []
                }
              },
              "messages": [],
              "errors": []
            }
            """,
            true);
  }

  @Test
  void serializesHistoryPercentagesAsStringsAndPendingPercentagesAsNumbers() {
    InvestmentChoiceDetails details =
        new InvestmentChoiceDetails(
            5,
            List.of(new InvestmentChoiceSource("BO", 1, true)),
            List.of(new InvestmentChoiceFund("F01", "Alpha", null, "1", true)),
            List.of(new InvestmentChoiceMandate(LocalDate.of(2026, 10, 1), "BO", "F01", "05")),
            List.of(
                new InvestmentChoicePending(
                    "REF-1",
                    LocalDate.of(2026, 10, 2),
                    LocalTime.of(14, 30, 5),
                    List.of(new InvestmentChoicePendingDetail("BO", "F01", 8)))));
    WebTestClient client =
        client(
            command -> Mono.just(new InvestmentChoiceResult("formatted-account", details)),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    client
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.history[0].allocations[0].percentage")
        .isEqualTo("05")
        .jsonPath("$.result.data.pendingInstructions[0].allocations[0].percentage")
        .isEqualTo(8);
  }

  @Test
  void responseDoesNotContainRawContextIdentifiers() {
    PortalAccessContext sensitiveContext =
        new PortalAccessContext(
            new ActorContext("actor", ActorUserRole.ROLE_USER_MBR),
            new AccountContext(
                "account-ref-marker",
                "JP",
                "policy-marker",
                "cert-marker",
                "T",
                "OE",
                TermStatus.BLANK,
                null));
    WebTestClient client =
        client(
            command ->
                Mono.just(
                    new InvestmentChoiceResult(
                        "formatted-account",
                        new InvestmentChoiceDetails(
                            5, List.of(), List.of(), List.of(), List.of()))),
            sensitiveContext,
            exchange -> Optional.of(SESSION_ID));

    String response =
        client
            .get()
            .uri(ROUTE)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(response)
        .isNotNull()
        .doesNotContain(
            "policy-marker", "cert-marker", "account-ref-marker", "sid_abcdefghijklmnopqrstuvwx");
  }

  @Test
  void buildsTheCommandOnlyFromTrustedContextAndRequestMetadata() {
    AtomicReference<GetInvestmentChoiceCommand> captured = new AtomicReference<>();
    var controller =
        controller(
            command -> {
              captured.set(command);
              return Mono.just(
                  new InvestmentChoiceResult(
                      "00000008802-0230",
                      new InvestmentChoiceDetails(5, List.of(), List.of(), List.of(), List.of())));
            },
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get(ROUTE).header("Accept-Language", "zh_HK").build());

    controller
        .getInvestmentChoice("zh_HK", exchange)
        .contextWrite(
            context ->
                context.put(
                    RequestHeaderContextKeys.CONTEXT_KEY,
                    new RequestHeaderContext("UNTRUSTED", "req-42", "zh_HK")))
        .block();

    assertThat(captured.get())
        .isEqualTo(
            new GetInvestmentChoiceCommand(
                ActorUserRole.ROLE_USER_MBR,
                "JP",
                "00000008802",
                "0230",
                "ACC-1",
                SESSION_ID,
                "req-42"));
  }

  @Test
  void missingAccountRefUsesTheSelectedAccountRejectionWithoutCallingTheUseCase() {
    AtomicInteger calls = new AtomicInteger();
    WebTestClient client =
        client(
            command -> {
              calls.incrementAndGet();
              return Mono.error(new AssertionError("use case must not run"));
            },
            context(" "),
            exchange -> Optional.of(SESSION_ID));

    client
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID);
    assertThat(calls).hasValue(0);
  }

  @Test
  void missingSessionUsesAuthenticatedContextRejectionWithoutCallingTheUseCase() {
    AtomicInteger calls = new AtomicInteger();
    WebTestClient client =
        client(
            command -> {
              calls.incrementAndGet();
              return Mono.error(new AssertionError("use case must not run"));
            },
            context("ACC-1"),
            exchange -> Optional.empty());

    client
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.errors[0].type")
        .isEqualTo("AUTHENTICATION_REQUIRED")
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
    assertThat(calls).hasValue(0);

    StepVerifier.create(
            controller(
                    command -> {
                      calls.incrementAndGet();
                      return Mono.error(new AssertionError("use case must not run"));
                    },
                    context("ACC-1"),
                    exchange -> Optional.empty())
                .getInvestmentChoice(
                    null, MockServerWebExchange.from(MockServerHttpRequest.get(ROUTE).build())))
        .expectError(AuthenticatedPortalContextResolutionException.class)
        .verify();
    assertThat(calls).hasValue(0);
  }

  @Test
  void retrievalFailureReturnsThePublic503SystemErrorWithoutMessage() {
    WebTestClient client =
        client(
            command -> Mono.error(InvestmentChoiceDetailsRetrievalException.systemFailure()),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    client
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .json(
            """
            {
              "success": false,
              "status": "SYSTEM_ERROR",
              "result": null,
              "messages": [],
              "errors": [
                {
                  "type": "SYSTEM",
                  "code": "err.investment-choice.retrieval.unavailable",
                  "targets": [],
                  "severity": "ERROR",
                  "source": "SERVER"
                }
              ]
            }
            """,
            true)
        .jsonPath("$.errors[0].message")
        .doesNotExist();
  }

  private static WebTestClient client(
      GetInvestmentChoiceUseCase useCase,
      PortalAccessContext context,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    return WebTestClient.bindToController(controller(useCase, context, sessionCookieReader))
        .controllerAdvice(advice())
        .build();
  }

  private static InvestmentChoiceController controller(
      GetInvestmentChoiceUseCase useCase,
      PortalAccessContext context,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    return new InvestmentChoiceController(
        useCase, resolver(context), sessionCookieReader, new InvestmentChoiceWebMapper());
  }

  private static CurrentPortalAccessContextResolver resolver(PortalAccessContext context) {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.just(context);
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context);
      }
    };
  }

  private static PortalAccessContext context(String accountRef) {
    return new PortalAccessContext(
        new ActorContext("actor", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            accountRef, "JP", "00000008802", "0230", "T", "OE", TermStatus.BLANK, null));
  }

  private static ApiExceptionHandler advice() {
    ErrorMessageResolver resolver = (code, locale, env, trust, scheme) -> code;
    ObjectMapper objectMapper = new ObjectMapper();
    return new ApiExceptionHandler(
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
        new PublicMessageResolver(resolver),
        new ErrorEventLogger(
            new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties()))),
        new ErrorResponseRenderer());
  }
}
