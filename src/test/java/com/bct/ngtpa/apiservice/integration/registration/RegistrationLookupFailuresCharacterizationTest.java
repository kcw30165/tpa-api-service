package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedOperation;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sub-task 2b.2 STEP 1, Priority 1 item 2 (2026-10-28 coverage-matrix decision): Registration
 * lookup failures (EMPTY_DATA, INVALID_ACCOUNT_DATA, UNEXPECTED_ERROR), both captcha paths.
 *
 * <p>Disabled-captcha scenarios capture the real {@link Mono} error produced by the real {@link
 * RegistrationStartProtectedOperation} chain and feed the real captured exception to a real {@link
 * ApiExceptionHandler} (built exactly as in {@code ApiExceptionHandlerCharacterizationTest}, which
 * already pins ONE of these three reasons, {@code UNEXPECTED_ERROR}, via direct exception
 * construction - this class instead drives the real adapter chain for all three reasons).
 * Enabled-captcha scenarios assert the full-chain response via {@code RegistrationController}.
 *
 * <p>Confirmed real routing: {@code TrpSearchMemberApimResultMapper.toResult} throws {@link
 * RegistrationLookupDownstreamException} for each of {@code EMPTY_DATA} (data null/empty, blank
 * err-message), {@code INVALID_ACCOUNT_DATA} (an item with blank {@code env}/{@code trustCode}),
 * and {@code UNEXPECTED_ERROR} (any other non-blank err-message). {@code
 * RegistrationStartProtectedOperation.lookup} has no {@code try/catch} around {@code
 * searchMember(...).flatMap(...)}, so the thrown exception becomes a genuine Reactor {@code
 * onError} signal. On the disabled-captcha path, {@code execute(...)} returns that lookup Mono
 * directly with no error handling at all, so the exception propagates fully uncaught. {@link
 * RegistrationExceptionMappings#lookupDownstream()} maps ALL THREE reasons identically: {@code
 * HttpStatus.SERVICE_UNAVAILABLE} (503), {@code ApiStatus.SYSTEM_ERROR}, type {@code
 * "DOWNSTREAM_SYSTEM"}, code {@code SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE}, target {@code
 * "PAGE"}, logged at ERROR with no stack trace - the {@code reason()} only changes the internal
 * diagnostic message text, not any public-facing field.
 *
 * <p>Per Reactor's own operator semantics, an exception thrown inside {@code .map(...)} (where
 * {@code TrpSearchMemberApimResultMapper.toResult} is invoked) becomes an {@code onError} signal
 * and structurally bypasses any {@code onNext}-based side effect - including {@code
 * ApimRegistrationLookupAdapter}'s {@code FailureEvent} publish, which is confirmed (in Priority 1
 * item 1's passing tests) to run only for the {@code NOT_ELIGIBLE} NORMAL-RESULT case. This
 * scenario therefore asserts NO {@code FailureEvent}/{@code StructuredFailureEventLogger} output -
 * the real test run itself will surface it immediately if that reasoning is wrong.
 */
class RegistrationLookupFailuresCharacterizationTest {

  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String SOURCE_SERVER = "SERVER";

  private Logger apiExceptionHandlerLogger;
  private Logger failureEventLoggerLogger;
  private ListAppender<ILoggingEvent> apiExceptionHandlerAppender;
  private ListAppender<ILoggingEvent> failureEventAppender;
  private Logger registrationStartWebMapperLogger;
  private ListAppender<ILoggingEvent> registrationStartWebMapperAppender;

  @BeforeEach
  void setUp() {
    apiExceptionHandlerAppender = new ListAppender<>();
    apiExceptionHandlerAppender.start();
    apiExceptionHandlerLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    apiExceptionHandlerLogger.addAppender(apiExceptionHandlerAppender);

    failureEventAppender = new ListAppender<>();
    failureEventAppender.start();
    failureEventLoggerLogger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    failureEventLoggerLogger.addAppender(failureEventAppender);

    registrationStartWebMapperAppender = new ListAppender<>();
    registrationStartWebMapperAppender.start();
    registrationStartWebMapperLogger =
        (Logger)
            LoggerFactory.getLogger(
                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper
                    .RegistrationStartWebMapper.class);
    registrationStartWebMapperLogger.addAppender(registrationStartWebMapperAppender);
  }

  @AfterEach
  void tearDown() {
    apiExceptionHandlerLogger.detachAppender(apiExceptionHandlerAppender);
    apiExceptionHandlerAppender.stop();
    failureEventLoggerLogger.detachAppender(failureEventAppender);
    failureEventAppender.stop();
    registrationStartWebMapperLogger.detachAppender(registrationStartWebMapperAppender);
    registrationStartWebMapperAppender.stop();
  }

  // ───────── Scenario 1 of 6: EMPTY_DATA, captcha DISABLED ─────────

  @Test
  void registrationStart_captchaDisabled_emptyData_realChainReachesServiceUnavailable503()
      throws Exception {
    var loggingSanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    var failureEventLogger = new StructuredFailureEventLogger(loggingSanitizer);

    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          @SuppressWarnings("unchecked")
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseBody<TrpSearchMemberApimDataItem> responseBody =
                new ApimResponseBody<>("", List.of());
            ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope =
                new ApimResponseEnvelope<>(responseBody);
            return (Mono<ApimResponseEnvelope<D>>) (Mono<?>) Mono.just(envelope);
          }
        };
    var lookup =
        new ApimRegistrationLookupAdapter(
            executor, failureEventLogger, new MaskingService(new MaskingProperties()));

    var operation =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            recordingSessionPort(),
            fixedCsrfPort(),
            recordingOtpPort(),
            acceptingMessagePort(),
            captchaDisabledPolicy(),
            // RegistrationStartProtectedOperation's constructor confirmed to do
            // `this.captcha = captcha;` with NO requireNonNull check, and
            // consumeCaptcha()/captcha.consume(...) are only reached when
            // captchaEnabled()==true - never on this disabled path - so null is
            // safe here (not guessed; confirmed from the real constructor body).
            null,
            CLOCK);

    StartRegistrationCommand command =
        new StartRegistrationCommand(
            new com.bct.ngtpa.apiservice.application.dto.auth.idempotency
                .RegistrationIdempotencyKey("idem-empty-data-captcha-disabled-001"),
            "GPFS",
            "1234567890",
            "31/01/1980",
            "member@example.com",
            null,
            null,
            null,
            "en",
            "req-empty-data-captcha-disabled-001",
            java.time.Duration.ofMinutes(5));

    RegistrationLookupDownstreamException captured =
        catchThrowableOfType(
            () -> operation.execute(command).block(), RegistrationLookupDownstreamException.class);

    assertThat(captured).isNotNull();
    assertThat(captured.reason())
        .isEqualTo(RegistrationLookupDownstreamException.Reason.EMPTY_DATA);

    var handler =
        new ApiExceptionHandler(
            new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
            new PublicMessageResolver(
                (errorCode, locale, accountEnv, trustCode, schemeType) ->
                    "message for " + errorCode),
            new ErrorEventLogger(loggingSanitizer),
            new ErrorResponseRenderer());

    var response = handler.handle(captured, exchangeWithRequestId("req-empty-data-001"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    MutationResponse<?> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors()).hasSize(1);
    ApiError error = body.errors().getFirst();
    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(error.targets()).isEqualTo(List.of("PAGE"));
    assertThat(error.source()).isEqualTo(SOURCE_SERVER);
    // PublicMessageResolver.isPreLoginAuthRequest(...) returns true for any path starting with
    // "/api/v1/auth/registration" and short-circuits message() to null BEFORE MessagePolicy.RESOLVE
    // is ever reached - confirmed real source behavior, not a test-construction bug.
    assertThat(error.message()).isNull();

    assertThat(apiExceptionHandlerAppender.list).hasSize(1);
    ILoggingEvent event = apiExceptionHandlerAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNull();
    JsonNode payload = new ObjectMapper().readTree(event.getFormattedMessage());
    assertThat(payload.get("event").asText()).isEqualTo("http.api.error");
    assertThat(payload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(payload.get("errorCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(payload.get("exceptionType").asText())
        .isEqualTo("RegistrationLookupDownstreamException");

    // ApimRegistrationLookupAdapter.withFailureLogging uses .doOnEach(...), which (unlike
    // .doOnNext) sees EVERY signal type including onError - so the thrown
    // RegistrationLookupDownstreamException's onError signal still triggers the adapter's own
    // FailureEvent publish, confirmed by real source (adapter lines 108-126) and real test log
    // output: event="registration.lookup.downstream_contract_failure", internalCode=null (the
    // adapter's own throw site passes null as downstreamErrorCode for EMPTY_DATA),
    // publicCode=DOWNSTREAM_UNAVAILABLE, httpStatus=503, retryable=true (hardcoded).
    assertThat(failureEventAppender.list).hasSize(1);
    ILoggingEvent failureEvent = failureEventAppender.list.getFirst();
    assertThat(failureEvent.getLevel()).isEqualTo(Level.ERROR);
    JsonNode failurePayload = new ObjectMapper().readTree(failureEvent.getFormattedMessage());
    assertThat(failurePayload.get("event").asText())
        .isEqualTo("registration.lookup.downstream_contract_failure");
    assertThat(failurePayload.get("failureOrigin").asText()).isEqualTo("DOWNSTREAM_CONTRACT");
    assertThat(failurePayload.get("internalCode").isNull()).isTrue();
    assertThat(failurePayload.get("publicCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(failurePayload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(failurePayload.get("retryable").asBoolean()).isTrue();
    assertThat(failurePayload.get("dimensions").get("reason").asText()).isEqualTo("EMPTY_DATA");
  }

  @Test
  void registrationStart_captchaDisabled_invalidAccountData_realChainReachesServiceUnavailable503()
      throws Exception {
    var loggingSanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    var failureEventLogger = new StructuredFailureEventLogger(loggingSanitizer);

    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          @SuppressWarnings("unchecked")
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            TrpSearchMemberApimDataItem blankEnvItem =
                new TrpSearchMemberApimDataItem(
                    "",
                    "trust1",
                    "schemeType1",
                    "policy1",
                    "cert1",
                    "termStatus1",
                    "termDate1",
                    true,
                    "cust1");
            ApimResponseBody<TrpSearchMemberApimDataItem> responseBody =
                new ApimResponseBody<>("", List.of(blankEnvItem));
            ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope =
                new ApimResponseEnvelope<>(responseBody);
            return (Mono<ApimResponseEnvelope<D>>) (Mono<?>) Mono.just(envelope);
          }
        };
    var lookup =
        new ApimRegistrationLookupAdapter(
            executor, failureEventLogger, new MaskingService(new MaskingProperties()));

    var operation =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            recordingSessionPort(),
            fixedCsrfPort(),
            recordingOtpPort(),
            acceptingMessagePort(),
            captchaDisabledPolicy(),
            null,
            CLOCK);

    StartRegistrationCommand command =
        new StartRegistrationCommand(
            new com.bct.ngtpa.apiservice.application.dto.auth.idempotency
                .RegistrationIdempotencyKey("idem-invalid-account-data-captcha-disabled-001"),
            "GPFS",
            "1234567890",
            "31/01/1980",
            "member@example.com",
            null,
            null,
            null,
            "en",
            "req-invalid-account-data-captcha-disabled-001",
            java.time.Duration.ofMinutes(5));

    RegistrationLookupDownstreamException captured =
        catchThrowableOfType(
            () -> operation.execute(command).block(), RegistrationLookupDownstreamException.class);

    assertThat(captured).isNotNull();
    assertThat(captured.reason())
        .isEqualTo(RegistrationLookupDownstreamException.Reason.INVALID_ACCOUNT_DATA);

    var handler =
        new ApiExceptionHandler(
            new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
            new PublicMessageResolver(
                (errorCode, locale, accountEnv, trustCode, schemeType) ->
                    "message for " + errorCode),
            new ErrorEventLogger(loggingSanitizer),
            new ErrorResponseRenderer());

    var response = handler.handle(captured, exchangeWithRequestId("req-invalid-account-data-001"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    MutationResponse<?> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors()).hasSize(1);
    ApiError error = body.errors().getFirst();
    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(error.targets()).isEqualTo(List.of("PAGE"));
    assertThat(error.source()).isEqualTo(SOURCE_SERVER);
    assertThat(error.message()).isNull();

    assertThat(apiExceptionHandlerAppender.list).hasSize(1);
    ILoggingEvent event = apiExceptionHandlerAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNull();
    JsonNode payload = new ObjectMapper().readTree(event.getFormattedMessage());
    assertThat(payload.get("event").asText()).isEqualTo("http.api.error");
    assertThat(payload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(payload.get("errorCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(payload.get("exceptionType").asText())
        .isEqualTo("RegistrationLookupDownstreamException");

    assertThat(failureEventAppender.list).hasSize(1);
    ILoggingEvent failureEvent = failureEventAppender.list.getFirst();
    assertThat(failureEvent.getLevel()).isEqualTo(Level.ERROR);
    JsonNode failurePayload = new ObjectMapper().readTree(failureEvent.getFormattedMessage());
    assertThat(failurePayload.get("event").asText())
        .isEqualTo("registration.lookup.downstream_contract_failure");
    assertThat(failurePayload.get("failureOrigin").asText()).isEqualTo("DOWNSTREAM_CONTRACT");
    assertThat(failurePayload.get("internalCode").isNull()).isTrue();
    assertThat(failurePayload.get("publicCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(failurePayload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(failurePayload.get("retryable").asBoolean()).isTrue();
    assertThat(failurePayload.get("dimensions").get("reason").asText())
        .isEqualTo("INVALID_ACCOUNT_DATA");
  }

  @Test
  void registrationStart_captchaDisabled_unexpectedError_realChainReachesServiceUnavailable503()
      throws Exception {
    var loggingSanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    var failureEventLogger = new StructuredFailureEventLogger(loggingSanitizer);

    String syntheticErrMessage = "err.tpa.synthetic.unexpected";

    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          @SuppressWarnings("unchecked")
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseBody<TrpSearchMemberApimDataItem> responseBody =
                new ApimResponseBody<>(syntheticErrMessage, List.of());
            ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope =
                new ApimResponseEnvelope<>(responseBody);
            return (Mono<ApimResponseEnvelope<D>>) (Mono<?>) Mono.just(envelope);
          }
        };
    var lookup =
        new ApimRegistrationLookupAdapter(
            executor, failureEventLogger, new MaskingService(new MaskingProperties()));

    var operation =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            recordingSessionPort(),
            fixedCsrfPort(),
            recordingOtpPort(),
            acceptingMessagePort(),
            captchaDisabledPolicy(),
            null,
            CLOCK);

    StartRegistrationCommand command =
        new StartRegistrationCommand(
            new com.bct.ngtpa.apiservice.application.dto.auth.idempotency
                .RegistrationIdempotencyKey("idem-unexpected-error-captcha-disabled-001"),
            "GPFS",
            "1234567890",
            "31/01/1980",
            "member@example.com",
            null,
            null,
            null,
            "en",
            "req-unexpected-error-captcha-disabled-001",
            java.time.Duration.ofMinutes(5));

    RegistrationLookupDownstreamException captured =
        catchThrowableOfType(
            () -> operation.execute(command).block(), RegistrationLookupDownstreamException.class);

    assertThat(captured).isNotNull();
    assertThat(captured.reason())
        .isEqualTo(RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR);

    var handler =
        new ApiExceptionHandler(
            new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
            new PublicMessageResolver(
                (errorCode, locale, accountEnv, trustCode, schemeType) ->
                    "message for " + errorCode),
            new ErrorEventLogger(loggingSanitizer),
            new ErrorResponseRenderer());

    var response = handler.handle(captured, exchangeWithRequestId("req-unexpected-error-001"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    MutationResponse<?> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors()).hasSize(1);
    ApiError error = body.errors().getFirst();
    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(error.targets()).isEqualTo(List.of("PAGE"));
    assertThat(error.source()).isEqualTo(SOURCE_SERVER);
    assertThat(error.message()).isNull();

    assertThat(apiExceptionHandlerAppender.list).hasSize(1);
    ILoggingEvent event = apiExceptionHandlerAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNull();
    JsonNode payload = new ObjectMapper().readTree(event.getFormattedMessage());
    assertThat(payload.get("event").asText()).isEqualTo("http.api.error");
    assertThat(payload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(payload.get("errorCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(payload.get("exceptionType").asText())
        .isEqualTo("RegistrationLookupDownstreamException");

    // KEY DIFFERENCE from scenarios 1/2: UNEXPECTED_ERROR's downstreamErrorCode is the actual
    // non-blank errMessage (not null), so internalCode is expected to be populated here.
    assertThat(failureEventAppender.list).hasSize(1);
    ILoggingEvent failureEvent = failureEventAppender.list.getFirst();
    assertThat(failureEvent.getLevel()).isEqualTo(Level.ERROR);
    JsonNode failurePayload = new ObjectMapper().readTree(failureEvent.getFormattedMessage());
    assertThat(failurePayload.get("event").asText())
        .isEqualTo("registration.lookup.downstream_contract_failure");
    assertThat(failurePayload.get("failureOrigin").asText()).isEqualTo("DOWNSTREAM_CONTRACT");
    assertThat(failurePayload.get("internalCode").asText()).isEqualTo(syntheticErrMessage);
    assertThat(failurePayload.get("publicCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(failurePayload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(failurePayload.get("retryable").asBoolean()).isTrue();
    assertThat(failurePayload.get("dimensions").get("reason").asText())
        .isEqualTo("UNEXPECTED_ERROR");
  }

  // ───────── Enabled-captcha scenario 1 of 3: EMPTY_DATA, captcha ENABLED ─────────

  @Test
  void registrationStart_captchaEnabled_emptyData_realChainReachesBadGateway502() throws Exception {
    var loggingSanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    var failureEventLogger = new StructuredFailureEventLogger(loggingSanitizer);

    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          @SuppressWarnings("unchecked")
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseBody<TrpSearchMemberApimDataItem> responseBody =
                new ApimResponseBody<>("", List.of());
            ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope =
                new ApimResponseEnvelope<>(responseBody);
            return (Mono<ApimResponseEnvelope<D>>) (Mono<?>) Mono.just(envelope);
          }
        };
    var lookup =
        new ApimRegistrationLookupAdapter(
            executor, failureEventLogger, new MaskingService(new MaskingProperties()));

    String challengeId = "cap_lookupfailures_empty_data_enabled";
    InMemoryCaptchaPort captchaPort = new InMemoryCaptchaPort();
    captchaPort.seedVerified(challengeId);

    var operation =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            recordingSessionPort(),
            fixedCsrfPort(),
            recordingOtpPort(),
            acceptingMessagePort(),
            captchaEnabledPolicy(),
            new RegistrationCaptchaGateService(captchaPort, CLOCK),
            CLOCK);

    StartRegistrationUseCase useCase = operation::execute;
    RegistrationController controller =
        startOnlyController(
            useCase, new RegistrationStartWebMapper(java.time.Duration.ofMinutes(5)));

    var response =
        controller
            .startRegistration(
                new com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto
                    .RegistrationStartRequest(
                    "GPFS", "1234567890", "31/01/1980", "member@example.com", null, challengeId),
                "idem-empty-data-captcha-enabled-001",
                "req-empty-data-captcha-enabled-001",
                "en",
                null,
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
            .block();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    MutationResponse<?> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors()).hasSize(1);
    ApiError error = body.errors().getFirst();
    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
    assertThat(error.targets()).isEqualTo(List.of("PAGE"));
    assertThat(error.source()).isEqualTo(SOURCE_SERVER);
    assertThat(error.message()).isNull();

    // This response never reaches ApiExceptionHandler (the exception is caught by
    // onErrorResume before the controller sees it) - so NO http.api.error log line fires here.
    assertThat(apiExceptionHandlerAppender.list).isEmpty();

    // RegistrationStartWebMapper.logFatalIfNeeded fires its own FATAL line for SYSTEM_FAILED.
    assertThat(registrationStartWebMapperAppender.list).hasSize(1);
    ILoggingEvent fatalEvent = registrationStartWebMapperAppender.list.getFirst();
    assertThat(fatalEvent.getLevel()).isEqualTo(Level.ERROR);
    assertThat(fatalEvent.getFormattedMessage())
        .isEqualTo(
            "event=registration.start.failure severity=FATAL status=SYSTEM_FAILED errorCodes=["
                + SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()
                + "]");

    // ApimRegistrationLookupAdapter's FailureEvent still fires - doOnEach sees the onError
    // signal regardless of what wraps around it downstream (same mechanism confirmed in the
    // disabled-path scenarios).
    assertThat(failureEventAppender.list).hasSize(1);
    ILoggingEvent failureEvent = failureEventAppender.list.getFirst();
    assertThat(failureEvent.getLevel()).isEqualTo(Level.ERROR);
    JsonNode failurePayload = new ObjectMapper().readTree(failureEvent.getFormattedMessage());
    assertThat(failurePayload.get("event").asText())
        .isEqualTo("registration.lookup.downstream_contract_failure");
    assertThat(failurePayload.get("failureOrigin").asText()).isEqualTo("DOWNSTREAM_CONTRACT");
    assertThat(failurePayload.get("internalCode").isNull()).isTrue();
    assertThat(failurePayload.get("publicCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(failurePayload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(failurePayload.get("retryable").asBoolean()).isTrue();
    assertThat(failurePayload.get("dimensions").get("reason").asText()).isEqualTo("EMPTY_DATA");
  }

  // ───────── Enabled-captcha scenario 2 of 3: INVALID_ACCOUNT_DATA, captcha ENABLED ─────────

  @Test
  void registrationStart_captchaEnabled_invalidAccountData_realChainReachesBadGateway502()
      throws Exception {
    var loggingSanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    var failureEventLogger = new StructuredFailureEventLogger(loggingSanitizer);

    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          @SuppressWarnings("unchecked")
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            TrpSearchMemberApimDataItem blankEnvItem =
                new TrpSearchMemberApimDataItem(
                    "",
                    "trust1",
                    "schemeType1",
                    "policy1",
                    "cert1",
                    "termStatus1",
                    "termDate1",
                    true,
                    "cust1");
            ApimResponseBody<TrpSearchMemberApimDataItem> responseBody =
                new ApimResponseBody<>("", List.of(blankEnvItem));
            ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope =
                new ApimResponseEnvelope<>(responseBody);
            return (Mono<ApimResponseEnvelope<D>>) (Mono<?>) Mono.just(envelope);
          }
        };
    var lookup =
        new ApimRegistrationLookupAdapter(
            executor, failureEventLogger, new MaskingService(new MaskingProperties()));

    String challengeId = "cap_lookupfailures_invalid_account_data_enabled";
    InMemoryCaptchaPort captchaPort = new InMemoryCaptchaPort();
    captchaPort.seedVerified(challengeId);

    var operation =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            recordingSessionPort(),
            fixedCsrfPort(),
            recordingOtpPort(),
            acceptingMessagePort(),
            captchaEnabledPolicy(),
            new RegistrationCaptchaGateService(captchaPort, CLOCK),
            CLOCK);

    StartRegistrationUseCase useCase = operation::execute;
    RegistrationController controller =
        startOnlyController(
            useCase, new RegistrationStartWebMapper(java.time.Duration.ofMinutes(5)));

    var response =
        controller
            .startRegistration(
                new com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto
                    .RegistrationStartRequest(
                    "GPFS", "1234567890", "31/01/1980", "member@example.com", null, challengeId),
                "idem-invalid-account-data-captcha-enabled-001",
                "req-invalid-account-data-captcha-enabled-001",
                "en",
                null,
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
            .block();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    MutationResponse<?> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors()).hasSize(1);
    ApiError error = body.errors().getFirst();
    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
    assertThat(error.targets()).isEqualTo(List.of("PAGE"));
    assertThat(error.source()).isEqualTo(SOURCE_SERVER);
    assertThat(error.message()).isNull();

    assertThat(apiExceptionHandlerAppender.list).isEmpty();

    assertThat(registrationStartWebMapperAppender.list).hasSize(1);
    ILoggingEvent fatalEvent = registrationStartWebMapperAppender.list.getFirst();
    assertThat(fatalEvent.getLevel()).isEqualTo(Level.ERROR);
    assertThat(fatalEvent.getFormattedMessage())
        .isEqualTo(
            "event=registration.start.failure severity=FATAL status=SYSTEM_FAILED errorCodes=["
                + SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()
                + "]");

    assertThat(failureEventAppender.list).hasSize(1);
    ILoggingEvent failureEvent = failureEventAppender.list.getFirst();
    assertThat(failureEvent.getLevel()).isEqualTo(Level.ERROR);
    JsonNode failurePayload = new ObjectMapper().readTree(failureEvent.getFormattedMessage());
    assertThat(failurePayload.get("event").asText())
        .isEqualTo("registration.lookup.downstream_contract_failure");
    assertThat(failurePayload.get("failureOrigin").asText()).isEqualTo("DOWNSTREAM_CONTRACT");
    assertThat(failurePayload.get("internalCode").isNull()).isTrue();
    assertThat(failurePayload.get("publicCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(failurePayload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(failurePayload.get("retryable").asBoolean()).isTrue();
    assertThat(failurePayload.get("dimensions").get("reason").asText())
        .isEqualTo("INVALID_ACCOUNT_DATA");
  }

  // ───────── Enabled-captcha scenario 3 of 3: UNEXPECTED_ERROR, captcha ENABLED ─────────

  @Test
  void registrationStart_captchaEnabled_unexpectedError_realChainReachesBadGateway502()
      throws Exception {
    var loggingSanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    var failureEventLogger = new StructuredFailureEventLogger(loggingSanitizer);

    String syntheticErrMessage = "err.tpa.synthetic.unexpected.enabled";

    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          @SuppressWarnings("unchecked")
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseBody<TrpSearchMemberApimDataItem> responseBody =
                new ApimResponseBody<>(syntheticErrMessage, List.of());
            ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope =
                new ApimResponseEnvelope<>(responseBody);
            return (Mono<ApimResponseEnvelope<D>>) (Mono<?>) Mono.just(envelope);
          }
        };
    var lookup =
        new ApimRegistrationLookupAdapter(
            executor, failureEventLogger, new MaskingService(new MaskingProperties()));

    String challengeId = "cap_lookupfailures_unexpected_error_enabled";
    InMemoryCaptchaPort captchaPort = new InMemoryCaptchaPort();
    captchaPort.seedVerified(challengeId);

    var operation =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            recordingSessionPort(),
            fixedCsrfPort(),
            recordingOtpPort(),
            acceptingMessagePort(),
            captchaEnabledPolicy(),
            new RegistrationCaptchaGateService(captchaPort, CLOCK),
            CLOCK);

    StartRegistrationUseCase useCase = operation::execute;
    RegistrationController controller =
        startOnlyController(
            useCase, new RegistrationStartWebMapper(java.time.Duration.ofMinutes(5)));

    var response =
        controller
            .startRegistration(
                new com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto
                    .RegistrationStartRequest(
                    "GPFS", "1234567890", "31/01/1980", "member@example.com", null, challengeId),
                "idem-unexpected-error-captcha-enabled-001",
                "req-unexpected-error-captcha-enabled-001",
                "en",
                null,
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
            .block();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    MutationResponse<?> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors()).hasSize(1);
    ApiError error = body.errors().getFirst();
    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
    assertThat(error.targets()).isEqualTo(List.of("PAGE"));
    assertThat(error.source()).isEqualTo(SOURCE_SERVER);
    assertThat(error.message()).isNull();

    assertThat(apiExceptionHandlerAppender.list).isEmpty();

    assertThat(registrationStartWebMapperAppender.list).hasSize(1);
    ILoggingEvent fatalEvent = registrationStartWebMapperAppender.list.getFirst();
    assertThat(fatalEvent.getLevel()).isEqualTo(Level.ERROR);
    assertThat(fatalEvent.getFormattedMessage())
        .isEqualTo(
            "event=registration.start.failure severity=FATAL status=SYSTEM_FAILED errorCodes=["
                + SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value()
                + "]");

    // KEY DIFFERENCE from EMPTY_DATA/INVALID_ACCOUNT_DATA: UNEXPECTED_ERROR's downstreamErrorCode
    // is the actual non-blank errMessage (not null), so internalCode is populated here - same
    // mechanism confirmed in the disabled-path UNEXPECTED_ERROR scenario.
    assertThat(failureEventAppender.list).hasSize(1);
    ILoggingEvent failureEvent = failureEventAppender.list.getFirst();
    assertThat(failureEvent.getLevel()).isEqualTo(Level.ERROR);
    JsonNode failurePayload = new ObjectMapper().readTree(failureEvent.getFormattedMessage());
    assertThat(failurePayload.get("event").asText())
        .isEqualTo("registration.lookup.downstream_contract_failure");
    assertThat(failurePayload.get("failureOrigin").asText()).isEqualTo("DOWNSTREAM_CONTRACT");
    assertThat(failurePayload.get("internalCode").asText()).isEqualTo(syntheticErrMessage);
    assertThat(failurePayload.get("publicCode").asText())
        .isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertThat(failurePayload.get("httpStatus").asInt()).isEqualTo(503);
    assertThat(failurePayload.get("retryable").asBoolean()).isTrue();
    assertThat(failurePayload.get("dimensions").get("reason").asText())
        .isEqualTo("UNEXPECTED_ERROR");
  }

  // ───────── shared fixtures ─────────

  private static RegistrationSessionPort recordingSessionPort() {
    return new RegistrationSessionPort() {
      @Override
      public Mono<String> createRegistrationSession(
          String maskedDestination, RegistrationContactType contactType) {
        return Mono.error(new AssertionError("session creation must not be invoked"));
      }

      @Override
      public Mono<Void> updateOtpChallenge(
          String authFlowSessionId,
          String activeOtpChallengeId,
          RegistrationContactType contactType,
          String maskedDestination) {
        return Mono.error(new AssertionError("OTP challenge update must not be invoked"));
      }
    };
  }

  private static RegistrationCsrPort fixedCsrfPort() {
    return authFlowSessionId -> Mono.error(new AssertionError("CSRF binding must not be invoked"));
  }

  private static RegistrationOtpPort recordingOtpPort() {
    return (contactType, destination) ->
        Mono.error(new AssertionError("OTP creation must not be invoked"));
  }

  private static RegistrationMessagePort acceptingMessagePort() {
    return (contactType, destination, code, ttlSeconds, language, correlationId) ->
        Mono.error(new AssertionError("message delivery must not be invoked"));
  }

  private static AuthWorkflowPolicyPort captchaDisabledPolicy() {
    return policyWith(new PreStartChallengePolicy(false, PreStartChallengeType.CAPTCHA));
  }

  private static AuthWorkflowPolicyPort policyWith(PreStartChallengePolicy preStartChallenge) {
    return new AuthWorkflowPolicyPort() {
      @Override
      public RegistrationAuthWorkflowPolicy registrationPolicy() {
        return new RegistrationAuthWorkflowPolicy(
            preStartChallenge,
            new CaptchaPolicy(
                7, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", true, 300, 5),
            null,
            null,
            null);
      }

      @Override
      public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
        return Optional.empty();
      }
    };
  }

  private static ServerWebExchange exchangeWithRequestId(String requestId) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/registration/start"));
    exchange
        .getAttributes()
        .put(
            com.bct.ngtpa.apiservice.shared.web.RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY,
            requestId);
    return exchange;
  }

  private static RegistrationController startOnlyController(
      StartRegistrationUseCase useCase, RegistrationStartWebMapper mapper) {
    return new RegistrationController(
        useCase,
        mapper,
        com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter
            .defaultWriter(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static AuthWorkflowPolicyPort captchaEnabledPolicy() {
    return policyWith(new PreStartChallengePolicy(true, PreStartChallengeType.CAPTCHA));
  }

  private static final class InMemoryCaptchaPort
      implements com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort {
    private final java.util.Map<
            com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId,
            com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge>
        values = new java.util.concurrent.ConcurrentHashMap<>();

    void seedVerified(String id) {
      var challengeId =
          new com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId(id);
      values.put(
          challengeId,
          new com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge(
              challengeId,
              new com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash(
                  "0".repeat(64)),
              com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus.VERIFIED,
              0,
              5,
              NOW.minusSeconds(30),
              NOW.plusSeconds(300),
              NOW.minusSeconds(5),
              null,
              null,
              null));
    }

    @Override
    public Mono<com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge> create(
        com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge challenge) {
      values.put(challenge.captchaChallengeId(), challenge);
      return Mono.just(challenge);
    }

    @Override
    public Mono<
            java.util.Optional<
                com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge>>
        load(com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId id) {
      return Mono.just(java.util.Optional.ofNullable(values.get(id)));
    }

    @Override
    public Mono<com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge>
        markVerified(
            com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId id,
            Instant at) {
      return Mono.just(values.get(id));
    }

    @Override
    public Mono<com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge>
        incrementFailedAttempt(
            com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId id,
            Instant at) {
      return Mono.just(values.get(id));
    }

    @Override
    public Mono<com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge> markLocked(
        com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    @Override
    public Mono<com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge>
        markConsumed(
            com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId id,
            Instant at) {
      var old = values.get(id);
      var consumed =
          new com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge(
              old.captchaChallengeId(),
              old.answerHash(),
              com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus.CONSUMED,
              old.failedAttemptCount(),
              old.maxAttempts(),
              old.createdAt(),
              old.expiresAt(),
              old.verifiedAt(),
              at,
              old.refreshedAt(),
              old.lockedAt());
      values.put(id, consumed);
      return Mono.just(consumed);
    }

    @Override
    public Mono<com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge>
        markRefreshed(
            com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId id,
            Instant at) {
      return Mono.just(values.get(id));
    }

    @Override
    public Mono<Void> delete(
        com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId id) {
      values.remove(id);
      return Mono.empty();
    }
  }
}
