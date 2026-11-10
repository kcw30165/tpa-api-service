package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.CancelInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancellation;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceResult;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmission;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmitOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.SubmitInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelNotCancellableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnavailableException;
import com.bct.ngtpa.apiservice.application.port.in.CancelInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.in.SubmitInvestmentChoiceUseCase;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class InvestmentChoiceControllerTest {

  private static final String ROUTE = "/api/v1/investment-choice";
  private static final String SUBMIT_ROUTE = "/api/v1/investment-choice/instructions";
  private static final String CANCEL_ROUTE = "/api/v1/investment-choice/instructions/cancel";
  private static final String CANCEL_REFERENCE_NO = "260001449";
  private static final String PRESERVED_REFERENCE_NO = "0260001449";
  private static final String CANCEL_BODY =
      """
      {"refNo":"%s"}
      """
          .formatted(CANCEL_REFERENCE_NO)
          .strip();
  private static final String BODY_WITH_PRESERVED_REFERENCE =
      """
      {"refNo":"%s"}
      """
          .formatted(PRESERVED_REFERENCE_NO)
          .strip();
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String VALID_INSTRUCTION =
      "{\"switchSources\":[\"BO\",\"EE\"],\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":60},{\"fundCode\":\"FHKM\",\"percentage\":40}]}";
  private static final InvestmentChoiceInstructionRequest VALID_REQUEST =
      new InvestmentChoiceInstructionRequest(
          List.of("BO", "EE"),
          List.of(
              new InvestmentChoiceInstructionRequest.Allocation("FBAL", 60),
              new InvestmentChoiceInstructionRequest.Allocation("FHKM", 40)));

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
            JsonCompareMode.STRICT);
  }

  @Test
  void returnsSourceDescriptionAndFundSequenceInTheExactGetJsonContract() {
    InvestmentChoiceDetails details =
        new InvestmentChoiceDetails(
            5,
            List.of(
                new InvestmentChoiceSource(
                    "BO", "Employer and Member Voluntary Contributions", 10, true)),
            List.of(
                new InvestmentChoiceFund(
                    "FAEU",
                    "Fidelity Advantage Portfolio Fund - Asia Pacific Equity Fund",
                    null,
                    60,
                    "",
                    true)),
            List.of(),
            List.of());
    WebTestClient client =
        client(
            command -> Mono.just(new InvestmentChoiceResult("00000008802-0230", details)),
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
                  "switchSources": [{
                    "switchSourceCode": "BO",
                    "switchSource": "Employer and Member Voluntary Contributions",
                    "sequence": 10,
                    "available": true
                  }],
                  "funds": [{
                    "fundCode": "FAEU",
                    "fundName": "Fidelity Advantage Portfolio Fund - Asia Pacific Equity Fund",
                    "sequence": 60,
                    "riskLevel": ""
                  }],
                  "history": [],
                  "pendingInstructions": []
                }
              },
              "messages": [],
              "errors": []
            }
            """,
            JsonCompareMode.STRICT);
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
            JsonCompareMode.STRICT)
        .jsonPath("$.errors[0].message")
        .doesNotExist();
  }

  @Test
  void submitsAndReturnsTheExactSubmittedEnvelope() {
    WebTestClient client =
        submitClient(
            command ->
                Mono.just(
                    new InvestmentChoiceSubmitOutcome.Submitted(
                        new InvestmentChoiceSubmission(
                            "submission-reference",
                            LocalDate.of(2026, 11, 6),
                            LocalTime.of(9, 5, 7)))),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    submitPost(client, VALID_INSTRUCTION)
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "success": true,
              "status": "SUBMITTED",
              "result": {
                "data": {
                  "referenceNo": "submission-reference",
                  "submitDate": "06/11/2026",
                  "submitTime": "09:05:07"
                }
              },
              "messages": [],
              "errors": []
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void cancelRouteReturnsTheExactSuccessEnvelope() {
    WebTestClient client =
        cancelClient(
            command ->
                Mono.just(
                    new InvestmentChoiceCancelOutcome.Cancelled(
                        new InvestmentChoiceCancellation(command.referenceNo()))),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    client
        .post()
        .uri(CANCEL_ROUTE)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CANCEL_BODY)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "success": true,
              "status": "SUCCESS",
              "result": {"data": {"referenceNo": "%s"}},
              "messages": [],
              "errors": []
            }
            """
                .formatted(CANCEL_REFERENCE_NO),
            JsonCompareMode.STRICT);
  }

  @Test
  void buildsTheCancelCommandFromTrustedContextAndRequestMetadata() throws Exception {
    AtomicReference<CancelInvestmentChoiceCommand> captured = new AtomicReference<>();
    var controller =
        new InvestmentChoiceController(
            command -> Mono.error(new AssertionError("GET use case must not run")),
            command -> Mono.error(new AssertionError("submit use case must not run")),
            command -> {
              captured.set(command);
              return Mono.just(
                  new InvestmentChoiceCancelOutcome.Cancelled(
                      new InvestmentChoiceCancellation(command.referenceNo())));
            },
            resolver(context("ACC-1")),
            exchange -> Optional.of(SESSION_ID),
            new InvestmentChoiceWebMapper(),
            new ObjectMapper());
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(CANCEL_ROUTE).build());

    controller
        .cancelInvestmentChoice(
            Mono.just(new tools.jackson.databind.ObjectMapper().readTree(CANCEL_BODY)), exchange)
        .contextWrite(
            reactorContext ->
                reactorContext.put(
                    RequestHeaderContextKeys.CONTEXT_KEY,
                    new RequestHeaderContext("UNTRUSTED", "req-42", "en")))
        .block();

    assertThat(captured.get())
        .isEqualTo(
            new CancelInvestmentChoiceCommand(
                "actor",
                ActorUserRole.ROLE_USER_MBR,
                "JP",
                "00000008802",
                "0230",
                "ACC-1",
                SESSION_ID,
                "req-42",
                CANCEL_REFERENCE_NO));
  }

  @Test
  void passesLeadingZeroReferenceNumberToUseCaseUnchanged() {
    AtomicReference<String> receivedReferenceNo = new AtomicReference<>();
    WebTestClient client =
        cancelClient(
            command -> {
              receivedReferenceNo.set(command.referenceNo());
              return Mono.just(
                  new InvestmentChoiceCancelOutcome.Cancelled(
                      new InvestmentChoiceCancellation(command.referenceNo())));
            },
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    cancelPost(client, BODY_WITH_PRESERVED_REFERENCE).expectStatus().isOk();

    assertThat(receivedReferenceNo.get()).isEqualTo(PRESERVED_REFERENCE_NO);
  }

  @ParameterizedTest
  @MethodSource("invalidCancelBodies")
  void cancelBodyViolationsReturn422WithoutCallingTheUseCase(
      String scenario, String body, String type, String code, List<String> targets) {
    AtomicInteger calls = new AtomicInteger();
    WebTestClient client =
        cancelClient(
            command -> {
              calls.incrementAndGet();
              return Mono.just(
                  new InvestmentChoiceCancelOutcome.Cancelled(
                      new InvestmentChoiceCancellation(command.referenceNo())));
            },
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    cancelPost(client, body)
        .expectStatus()
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        .expectBody()
        .json(validationEnvelope(type, code, targets), JsonCompareMode.STRICT);

    assertThat(calls).as(scenario).hasValue(0);
  }

  private static Stream<Arguments> invalidCancelBodies() {
    return Stream.of(
        Arguments.of(
            "missing body", null, "FORM", ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID, List.of()),
        Arguments.of(
            "empty body", "", "FORM", ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID, List.of()),
        Arguments.of(
            "array root", "[]", "FORM", ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID, List.of()),
        Arguments.of(
            "string root",
            "\"body\"",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "unknown member",
            "{\"refNo\":\"260001449\",\"extra\":true}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "null reference",
            "{\"refNo\":null}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "numeric reference",
            "{\"refNo\":260001449}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "boolean reference",
            "{\"refNo\":false}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "array reference",
            "{\"refNo\":[]}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "object reference",
            "{\"refNo\":{}}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "missing reference",
            "{}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
            List.of("refNo")),
        Arguments.of(
            "blank reference",
            "{\"refNo\":\"\"}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
            List.of("refNo")),
        Arguments.of(
            "whitespace reference",
            "{\"refNo\":\"   \"}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
            List.of("refNo")));
  }

  @Test
  void cancelUseCaseInvalidOutcomeReturns422() {
    WebTestClient client =
        cancelClient(
            command ->
                Mono.just(
                    new InvestmentChoiceCancelOutcome.Invalid(
                        List.of(
                            new InvestmentChoiceViolation(
                                InvestmentChoiceViolation.Kind.FIELD,
                                ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_INVALID,
                                List.of("refNo"))))),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    cancelPost(client, CANCEL_BODY)
        .expectStatus()
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        .expectBody()
        .json(
            validationEnvelope(
                "FIELD", ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_INVALID, List.of("refNo")),
            JsonCompareMode.STRICT);
  }

  @ParameterizedTest
  @MethodSource("mappedCancelFailures")
  void cancelFailuresUseSafeWarnMappings(
      RuntimeException failure, HttpStatus status, String expectedBody) {
    Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    logger.addAppender(captured);
    try {
      cancelPost(
              cancelClient(
                  command -> Mono.error(failure),
                  sensitiveContext("account-ref-secret"),
                  exchange -> Optional.of(SESSION_ID)),
              CANCEL_BODY)
          .expectStatus()
          .isEqualTo(status)
          .expectBody()
          .json(expectedBody, JsonCompareMode.STRICT);
    } finally {
      logger.detachAppender(captured);
      captured.stop();
    }

    assertThat(captured.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getThrowableProxy()).isNull();
            });
    String logText =
        captured.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(java.util.stream.Collectors.joining("\n"));
    assertThat(logText)
        .doesNotContain(
            "policyNo-secret",
            "certNo-secret",
            "actorUserId-secret",
            "account-ref-secret",
            SESSION_ID.value(),
            "password-validation-token-secret",
            CANCEL_REFERENCE_NO,
            "FBAL",
            "FHKM",
            "\"percentage\"");
  }

  private static Stream<Arguments> mappedCancelFailures() {
    return Stream.of(
        Arguments.of(
            new InvestmentChoiceCancelNotCancellableException(),
            HttpStatus.UNPROCESSABLE_CONTENT,
            """
            {
              "success": false,
              "status": "BUSINESS_REJECTED",
              "result": null,
              "messages": [],
              "errors": [
                {
                  "type": "BUSINESS",
                  "code": "err.investment-choice.cancel.not-cancellable",
                  "targets": [],
                  "severity": "ERROR",
                  "source": "SERVER"
                }
              ]
            }
            """),
        Arguments.of(
            new InvestmentChoiceCancelUnavailableException(),
            HttpStatus.SERVICE_UNAVAILABLE,
            """
            {
              "success": false,
              "status": "SYSTEM_ERROR",
              "result": null,
              "messages": [],
              "errors": [
                {
                  "type": "SYSTEM",
                  "code": "err.investment-choice.cancel.unavailable",
                  "targets": [],
                  "severity": "ERROR",
                  "source": "SERVER"
                }
              ]
            }
            """));
  }

  @Test
  void missingAccountRefAndSessionRejectCancelBeforeCallingTheUseCase() {
    AtomicInteger calls = new AtomicInteger();
    CancelInvestmentChoiceUseCase cancelUseCase =
        command -> {
          calls.incrementAndGet();
          return Mono.error(new AssertionError("cancel use case must not run"));
        };
    WebTestClient missingAccount =
        cancelClient(cancelUseCase, context(" "), exchange -> Optional.of(SESSION_ID));

    cancelPost(missingAccount, CANCEL_BODY)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID);

    WebTestClient missingSession =
        cancelClient(cancelUseCase, context("ACC-1"), exchange -> Optional.empty());
    cancelPost(missingSession, CANCEL_BODY)
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);

    assertThat(calls).hasValue(0);
  }

  @Test
  void submissionUnavailableReturnsTheExact503SystemEnvelope() {
    WebTestClient client =
        submitClient(
            command -> Mono.error(new InvestmentChoiceSubmissionUnavailableException()),
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    submitPost(client, VALID_INSTRUCTION)
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
                  "code": "err.investment-choice.submission.unavailable",
                  "targets": [],
                  "severity": "ERROR",
                  "source": "SERVER"
                }
              ]
            }
            """,
            JsonCompareMode.STRICT)
        .jsonPath("$.errors[0].message")
        .doesNotExist();
  }

  @Test
  void submitPathsDoNotLogSensitiveRequestData() {
    Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    logger.addAppender(captured);
    try {
      submitPost(
              submitClient(
                  command ->
                      Mono.just(
                          new InvestmentChoiceSubmitOutcome.Submitted(
                              new InvestmentChoiceSubmission(
                                  "referenceNo-secret",
                                  LocalDate.of(2026, 11, 6),
                                  LocalTime.NOON))),
                  sensitiveContext("account-ref-secret"),
                  exchange -> Optional.of(SESSION_ID)),
              VALID_INSTRUCTION)
          .expectStatus()
          .isOk();

      submitPost(
              submitClient(
                  command ->
                      Mono.just(
                          new InvestmentChoiceSubmitOutcome.Submitted(
                              new InvestmentChoiceSubmission(
                                  "referenceNo-secret",
                                  LocalDate.of(2026, 11, 6),
                                  LocalTime.NOON))),
                  sensitiveContext("account-ref-secret"),
                  exchange -> Optional.of(SESSION_ID)),
              "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":99}]}")
          .expectStatus()
          .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

      submitPost(
              submitClient(
                  command ->
                      Mono.just(
                          new InvestmentChoiceSubmitOutcome.Invalid(
                              List.of(
                                  new InvestmentChoiceViolation(
                                      InvestmentChoiceViolation.Kind.FIELD,
                                      ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                                      List.of("allocations[0].fundCode"))))),
                  sensitiveContext("account-ref-secret"),
                  exchange -> Optional.of(SESSION_ID)),
              VALID_INSTRUCTION)
          .expectStatus()
          .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

      submitPost(
              submitClient(
                  command -> Mono.error(new InvestmentChoiceSubmissionUnavailableException()),
                  sensitiveContext("account-ref-secret"),
                  exchange -> Optional.of(SESSION_ID)),
              VALID_INSTRUCTION)
          .expectStatus()
          .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

      submitPost(
              submitClient(
                  command -> Mono.error(new AssertionError("use case must not run")),
                  sensitiveContext("account-ref-secret"),
                  exchange -> Optional.empty()),
              VALID_INSTRUCTION)
          .expectStatus()
          .isEqualTo(HttpStatus.UNAUTHORIZED);

      submitPost(
              submitClient(
                  command -> Mono.error(new AssertionError("use case must not run")),
                  sensitiveContext(" "),
                  exchange -> Optional.of(SESSION_ID)),
              VALID_INSTRUCTION)
          .expectStatus()
          .isBadRequest();
    } finally {
      logger.detachAppender(captured);
      captured.stop();
    }

    String logText =
        captured.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(java.util.stream.Collectors.joining("\n"));
    assertThat(logText)
        .contains(
            "InvestmentChoiceSubmissionUnavailableException",
            "AuthenticatedPortalContextResolutionException",
            "PortalAccessContextResolutionException")
        .doesNotContain(
            "policyNo-secret",
            "certNo-secret",
            "actorUserId-secret",
            "account-ref-secret",
            SESSION_ID.value(),
            "password-validation-token-secret",
            "FBAL",
            "FHKM",
            "\"percentage\":60",
            "\"percentage\":40",
            "referenceNo-secret");
  }

  @Test
  void nonJsonContentTypeIsRejectedByTheWebFluxBodyDecoder() {
    AtomicInteger calls = new AtomicInteger();
    WebTestClient client =
        submitClient(
            command -> {
              calls.incrementAndGet();
              return Mono.error(new AssertionError("use case must not run"));
            },
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    var missingContentType =
        client
            .post()
            .uri(SUBMIT_ROUTE)
            .body(
                BodyInserters.fromDataBuffers(
                    Mono.just(
                        new DefaultDataBufferFactory()
                            .wrap(
                                VALID_INSTRUCTION.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8)))))
            .exchange()
            .expectBody()
            .returnResult();
    var textContentType =
        client
            .post()
            .uri(SUBMIT_ROUTE)
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(VALID_INSTRUCTION)
            .exchange()
            .expectBody()
            .returnResult();

    assertThat(missingContentType.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(textContentType.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(calls).hasValue(0);
  }

  @Test
  void buildsTheSubmitCommandFromTrustedContextAndRequestMetadata() throws Exception {
    AtomicReference<SubmitInvestmentChoiceCommand> captured = new AtomicReference<>();
    var controller =
        controller(
            command -> Mono.error(new AssertionError("GET use case must not run")),
            command -> {
              captured.set(command);
              return Mono.just(
                  new InvestmentChoiceSubmitOutcome.Submitted(
                      new InvestmentChoiceSubmission(
                          "submission-reference", LocalDate.of(2026, 11, 6), LocalTime.NOON)));
            },
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(SUBMIT_ROUTE).build());

    controller
        .submitInvestmentChoice(
            Mono.just(new tools.jackson.databind.ObjectMapper().readTree(VALID_INSTRUCTION)),
            exchange)
        .contextWrite(
            reactorContext ->
                reactorContext.put(
                    RequestHeaderContextKeys.CONTEXT_KEY,
                    new RequestHeaderContext("UNTRUSTED", "req-42", "zh_HK")))
        .block();

    assertThat(captured.get())
        .isEqualTo(
            new SubmitInvestmentChoiceCommand(
                "actor",
                ActorUserRole.ROLE_USER_MBR,
                "JP",
                "00000008802",
                "0230",
                "ACC-1",
                SESSION_ID,
                "req-42",
                VALID_REQUEST));
  }

  @ParameterizedTest
  @MethodSource("invalidInstructionBodies")
  void bodyViolationsReturn422WithoutCallingTheUseCase(
      String scenario, String body, String type, String code, List<String> targets) {
    AtomicInteger calls = new AtomicInteger();
    WebTestClient client =
        submitClient(
            command -> {
              calls.incrementAndGet();
              return Mono.just(
                  new InvestmentChoiceSubmitOutcome.Submitted(
                      new InvestmentChoiceSubmission(
                          "unexpected-reference", LocalDate.of(2026, 11, 6), LocalTime.NOON)));
            },
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    submitPost(client, body)
        .expectStatus()
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        .expectBody()
        .json(validationEnvelope(type, code, targets), JsonCompareMode.STRICT);

    assertThat(calls).as(scenario).hasValue(0);
  }

  private static Stream<Arguments> invalidInstructionBodies() {
    return Stream.of(
        Arguments.of(
            "V1 missing body",
            null,
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "V1 non-object", "[]", "FORM", ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID, List.of()),
        Arguments.of(
            "V1 unknown top-level member",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":100}],\"extra\":true}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "V1 unknown allocation member",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":100,\"extra\":true}]}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "V1 switchSources is not an array",
            "{\"switchSources\":\"BO\",\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":100}]}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "V1 allocation is not an object",
            "{\"switchSources\":[\"BO\"],\"allocations\":[null]}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "V1 fundCode is not a string",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":1,\"percentage\":100}]}",
            "FORM",
            ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
            List.of()),
        Arguments.of(
            "V2 missing switchSources",
            "{\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":100}]}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
            List.of("switchSources")),
        Arguments.of(
            "V3 duplicate switch source",
            "{\"switchSources\":[\"BO\",\"BO\"],\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":100}]}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
            List.of("switchSources[1]")),
        Arguments.of(
            "V4 empty allocations",
            "{\"switchSources\":[\"BO\"],\"allocations\":[]}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_ALLOCATION_REQUIRED,
            List.of("allocations")),
        Arguments.of(
            "V5 missing fundCode",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"percentage\":100}]}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
            List.of("allocations[0].fundCode")),
        Arguments.of(
            "V6 duplicate fundCode",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":50},{\"fundCode\":\"FBAL\",\"percentage\":50}]}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
            List.of("allocations[1].fundCode")),
        Arguments.of(
            "V7 percentage outside range",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":0}]}",
            "FIELD",
            ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
            List.of("allocations[0].percentage")),
        Arguments.of(
            "V8 percentage total",
            "{\"switchSources\":[\"BO\"],\"allocations\":[{\"fundCode\":\"FBAL\",\"percentage\":99}]}",
            "CROSS_FIELD",
            ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
            List.of("allocations")));
  }

  @Test
  void useCaseSnapshotViolationsReturn422() {
    var violations =
        List.of(
            new InvestmentChoiceViolation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                List.of("allocations[0].percentage")),
            new InvestmentChoiceViolation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                List.of("switchSources[1]")),
            new InvestmentChoiceViolation(
                InvestmentChoiceViolation.Kind.FIELD,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                List.of("allocations[0].fundCode")));
    AtomicInteger calls = new AtomicInteger();
    WebTestClient client =
        submitClient(
            command -> {
              calls.incrementAndGet();
              return Mono.just(new InvestmentChoiceSubmitOutcome.Invalid(violations));
            },
            context("ACC-1"),
            exchange -> Optional.of(SESSION_ID));

    submitPost(client, VALID_INSTRUCTION)
        .expectStatus()
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        .expectBody()
        .json(
            """
            {
              "success": false,
              "status": "VALIDATION_FAILED",
              "result": null,
              "messages": [],
              "errors": [
                {"type":"FIELD","code":"err.investment-choice.percentage.interval-invalid","targets":["allocations[0].percentage"],"severity":"ERROR","source":"SERVER"},
                {"type":"FIELD","code":"err.investment-choice.switch-source.unavailable","targets":["switchSources[1]"],"severity":"ERROR","source":"SERVER"},
                {"type":"FIELD","code":"err.investment-choice.fund.unavailable","targets":["allocations[0].fundCode"],"severity":"ERROR","source":"SERVER"}
              ]
            }
            """,
            JsonCompareMode.STRICT);

    assertThat(calls).hasValue(1);
  }

  @Test
  void missingAccountRefAndSessionRejectSubmitBeforeCallingTheUseCase() {
    AtomicInteger calls = new AtomicInteger();
    SubmitInvestmentChoiceUseCase submitUseCase =
        command -> {
          calls.incrementAndGet();
          return Mono.error(new AssertionError("use case must not run"));
        };
    WebTestClient missingAccount =
        submitClient(submitUseCase, context(" "), exchange -> Optional.of(SESSION_ID));

    submitPost(missingAccount, VALID_INSTRUCTION)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID);

    WebTestClient missingSession =
        submitClient(submitUseCase, context("ACC-1"), exchange -> Optional.empty());
    submitPost(missingSession, VALID_INSTRUCTION)
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);

    assertThat(calls).hasValue(0);
  }

  private static String validationEnvelope(String type, String code, List<String> targets) {
    String serializedTargets =
        targets.stream()
            .map(target -> "\"" + target + "\"")
            .collect(java.util.stream.Collectors.joining(","));
    return """
        {"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"type":"%s","code":"%s","targets":[%s],"severity":"ERROR","source":"SERVER"}]}
        """
        .formatted(type, code, serializedTargets);
  }

  private static WebTestClient.ResponseSpec submitPost(WebTestClient client, String body) {
    var request = client.post().uri(SUBMIT_ROUTE).contentType(MediaType.APPLICATION_JSON);
    return body == null ? request.exchange() : request.bodyValue(body).exchange();
  }

  private static WebTestClient.ResponseSpec cancelPost(WebTestClient client, String body) {
    var request = client.post().uri(CANCEL_ROUTE).contentType(MediaType.APPLICATION_JSON);
    return body == null || body.isEmpty() ? request.exchange() : request.bodyValue(body).exchange();
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
        useCase,
        command -> Mono.error(new AssertionError("submit use case must not run")),
        command -> Mono.error(new AssertionError("cancel use case must not run")),
        resolver(context),
        sessionCookieReader,
        new InvestmentChoiceWebMapper(),
        new ObjectMapper());
  }

  private static WebTestClient submitClient(
      SubmitInvestmentChoiceUseCase useCase,
      PortalAccessContext context,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    return WebTestClient.bindToController(
            controller(
                command -> Mono.error(new AssertionError("GET use case must not run")),
                useCase,
                context,
                sessionCookieReader))
        .controllerAdvice(advice())
        .build();
  }

  private static InvestmentChoiceController controller(
      GetInvestmentChoiceUseCase getUseCase,
      SubmitInvestmentChoiceUseCase submitUseCase,
      PortalAccessContext context,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    return new InvestmentChoiceController(
        getUseCase,
        submitUseCase,
        command -> Mono.error(new AssertionError("cancel use case must not run")),
        resolver(context),
        sessionCookieReader,
        new InvestmentChoiceWebMapper(),
        new ObjectMapper());
  }

  private static WebTestClient cancelClient(
      CancelInvestmentChoiceUseCase cancelUseCase,
      PortalAccessContext context,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    return WebTestClient.bindToController(
            new InvestmentChoiceController(
                command -> Mono.error(new AssertionError("GET use case must not run")),
                command -> Mono.error(new AssertionError("submit use case must not run")),
                cancelUseCase,
                resolver(context),
                sessionCookieReader,
                new InvestmentChoiceWebMapper(),
                new ObjectMapper()))
        .controllerAdvice(advice())
        .build();
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

  private static PortalAccessContext sensitiveContext(String accountRef) {
    return new PortalAccessContext(
        new ActorContext("actorUserId-secret", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            accountRef,
            "JP",
            "policyNo-secret",
            "certNo-secret",
            "T",
            "OE",
            TermStatus.BLANK,
            null));
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
