package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.accountgainloss.AccountGainLossRetrievalResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRequestInvalidException;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceInvalidDateException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * M1 sub-task 1.1 characterization pack (docs/design/15-exception-handling/05-milestones.md).
 *
 * <p>Pins, for every current {@link ApiExceptionHandler} handler method: HTTP status, response body
 * (ApiStatus, error type/code/targets/severity/source), the {@code X-Request-Id} response header,
 * the log event name, the log level, and whether a stack trace is attached.
 *
 * <p>Sub-task 1.4 update: {@code ApiExceptionHandler} gained a third constructor parameter, {@code
 * ExceptionMappingRegistry}; this pack passes the real production registry ({@code
 * ExceptionMappingRegistryConfig}) so every pinned row still exercises genuine behaviour.
 *
 * <p>Sub-task 1.6 update: {@code handleAccountGainLossRetrievalException} gained the {@code
 * X-Request-Id} header and the {@code http.api.error} log line (D5's one planned exception to "no
 * public behaviour change"); status and body remain unchanged.
 *
 * <p>Sub-task 1.6b (M1 closure, pin-only): extends {@link #assertMutationFailure} with an {@code
 * expectedMessage} parameter and asserts {@code error.message()} on every row, including two new
 * rows (pre-login suppression, two-field-error WebExchangeBind).
 *
 * <p>Sub-task 1.7 (M1 closure, Option A, approved A2): every {@code handleXxx} method is now a
 * one-line {@code delegate(...)} call, including the four previously-inline handlers
 * (AccountBalance, AccountGainLoss, FundFactSheet, WebExchangeBind). Every assertion from sub-task
 * 1.6b is unchanged -- same status, body, header, message, log level and stack-trace presence --
 * EXCEPT the one row A2 approved changing: {@code accountBalanceNormalizationFailure_...} now
 * asserts exactly ONE {@code http.api.error} log event carrying the merged {@code
 * collection}/{@code rowIndex}/{@code field}/{@code reason} fields, instead of the pre-1.7 two
 * separate events (a value-free {@code account.balance.normalization.failed} event followed by the
 * {@code http.api.error} event).
 *
 * <p>SonarQube fix (post-M1 quality gate, java:S1874): three tests below assert against {@code
 * HttpStatus.UNPROCESSABLE_ENTITY}, which spring-web 7.0.7 flags as deprecated in favour of {@code
 * HttpStatus.UNPROCESSABLE_CONTENT}. Production (the Account Balance / Account Gain-Loss response
 * policies, both outside this branch's scope) still returns {@code UNPROCESSABLE_ENTITY}, and it is
 * not yet confirmed whether the two constants are reference-equal or distinct enum entries with the
 * same numeric code -- swapping the literal without that confirmation risks a false test failure.
 * Suppressed on each affected method rather than guessed; migrating the whole codebase to {@code
 * UNPROCESSABLE_CONTENT} is a separate, broader workstream.
 */
class ApiExceptionHandlerCharacterizationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SOURCE_SERVER = "SERVER";

  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;
  private ApiExceptionHandler handler;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    handler =
        new ApiExceptionHandler(
            testErrorMessageResolver(),
            testLoggingSanitizer(),
            new ExceptionMappingRegistryConfig().exceptionMappingRegistry());
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
    listAppender.stop();
  }

  // ───────────────────────── RegistrationLookupDownstreamException ─────────────────────────

  @Test
  void
      registrationLookupDownstreamException_serviceUnavailableSystemErrorLoggedAtErrorNoStackTrace() {
    var ex =
        new RegistrationLookupDownstreamException(
            RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR, "err.tpa.unexpected");

    var response =
        handler.handleRegistrationLookupDownstreamException(ex, exchangeWithRequestId("req-1"));

    assertMutationFailure(
        response,
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "DOWNSTREAM_SYSTEM",
        SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value(),
        List.of("PAGE"),
        "req-1",
        "message for " + SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
    assertLoggedOnce(
        Level.ERROR,
        "RegistrationLookupDownstreamException",
        SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value(),
        false);
  }

  // ───────────────────────── ApimProfileResolutionException ─────────────────────────

  @Test
  void apimProfileResolutionException_publicStatusSystemErrorLoggedAtErrorNoStackTrace() {
    var ex = new ApimProfileResolutionException("profile resolution failed");

    var response = handler.handleApimProfileResolutionException(ex, exchangeWithRequestId("req-2"));

    assertMutationFailure(
        response,
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiStatus.SYSTEM_ERROR,
        "SYSTEM",
        ex.getInternalCode(),
        List.of(),
        "req-2",
        "message for " + ex.getInternalCode());
    assertLoggedOnce(Level.ERROR, "ApimProfileResolutionException", ex.getInternalCode(), false);
  }

  // ───────────────────────── AccountBalanceRetrievalException ─────────────────────────

  @SuppressWarnings("deprecation")
  @Test
  void accountBalanceAccountNotFound_unprocessableBusinessRejectedLoggedAtWarn() {
    var response =
        handler.handleAccountBalanceRetrievalException(
            AccountBalanceRetrievalException.accountNotFound(), exchangeWithRequestId("req-3"));

    assertMutationFailure(
        response,
        HttpStatus.UNPROCESSABLE_ENTITY,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        "err.ab.account.not.exist",
        List.of(),
        "req-3",
        null);
    assertLoggedOnce(
        Level.WARN, "AccountBalanceRetrievalException", "err.ab.account.not.exist", false);
  }

  @SuppressWarnings("deprecation")
  @Test
  void accountBalanceAccountMismatch_unprocessableBusinessRejectedLoggedAtWarn() {
    var response =
        handler.handleAccountBalanceRetrievalException(
            AccountBalanceRetrievalException.accountMismatch(), exchangeWithRequestId("req-4"));

    assertMutationFailure(
        response,
        HttpStatus.UNPROCESSABLE_ENTITY,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        "err.ab.account.mismatch",
        List.of(),
        "req-4",
        null);
    assertLoggedOnce(
        Level.WARN, "AccountBalanceRetrievalException", "err.ab.account.mismatch", false);
  }

  @Test
  void accountBalanceSystemFailure_serviceUnavailableSystemErrorLoggedAtErrorNoStackTrace() {
    var response =
        handler.handleAccountBalanceRetrievalException(
            AccountBalanceRetrievalException.systemFailure(), exchangeWithRequestId("req-5"));

    assertMutationFailure(
        response,
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "SYSTEM",
        "err.system.unavailable",
        List.of(),
        "req-5",
        null);
    assertLoggedOnce(
        Level.ERROR, "AccountBalanceRetrievalException", "err.system.unavailable", false);
  }

  /**
   * Sub-task 1.7 (A2, approved 2026-10-22): rewritten to assert exactly ONE {@code http.api.error}
   * log event carrying the merged {@code collection}/{@code rowIndex}/{@code field}/{@code reason}
   * fields, instead of the pre-1.7 two separate events. This is the one row A1/A2 explicitly
   * permitted to change.
   */
  @Test
  void accountBalanceNormalizationFailure_logsOneEventWithMergedValueFreeFields() {
    var failure =
        new AccountBalanceRetrievalException.NormalizationFailure(
            AccountBalanceRetrievalException.Collection.FUND_DTL,
            3,
            AccountBalanceRetrievalException.Field.FUND_CODE,
            AccountBalanceRetrievalException.ValidationReason.EMPTY);

    handler.handleAccountBalanceRetrievalException(
        AccountBalanceRetrievalException.normalizationFailure(failure),
        exchangeWithRequestId("req-6"));

    assertEquals(1, listAppender.list.size());
    ILoggingEvent event = listAppender.list.getFirst();
    assertEquals(Level.ERROR, event.getLevel());
    assertNull(event.getThrowableProxy());

    JsonNode payload = parse(event);
    assertEquals("http.api.error", payload.get("event").asText());
    assertEquals("req-6", payload.get("requestId").asText());
    assertEquals("err.system.unavailable", payload.get("errorCode").asText());
    assertEquals("fundDtl", payload.get("collection").asText());
    assertEquals(3, payload.get("rowIndex").asInt());
    assertEquals("fund-code", payload.get("field").asText());
    assertEquals("EMPTY", payload.get("reason").asText());
    assertEquals("AccountBalanceRetrievalException", payload.get("exceptionType").asText());
  }

  // ───────────────────────── AccountGainLossRetrievalException ─────────────────────────

  @SuppressWarnings("deprecation")
  @Test
  void
      accountGainLossFundSwitchingInProgress_unprocessableBusinessRejectedLoggedAtWarnWithRequestId() {
    var exception = AccountGainLossRetrievalException.fundSwitchingInProgress();

    var response =
        handler.handleAccountGainLossRetrievalException(exception, exchangeWithRequestId("req-25"));

    assertMutationFailure(
        response,
        HttpStatus.UNPROCESSABLE_ENTITY,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        "err.fund.switching.in.progress",
        List.of(),
        "req-25",
        null);
    assertLoggedOnce(
        Level.WARN, "AccountGainLossRetrievalException", "err.fund.switching.in.progress", false);
  }

  @Test
  void accountGainLossSystemFailure_serviceUnavailableSystemErrorLoggedAtErrorWithRequestId() {
    var exception = AccountGainLossRetrievalException.systemFailure();

    var response =
        handler.handleAccountGainLossRetrievalException(exception, exchangeWithRequestId("req-26"));

    assertMutationFailure(
        response,
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "SYSTEM",
        "err.system.unavailable",
        List.of(),
        "req-26",
        null);
    assertLoggedOnce(
        Level.ERROR, "AccountGainLossRetrievalException", "err.system.unavailable", false);
  }

  @Test
  void accountGainLossResponsePolicyMatchesHandlerDelegation() {
    ResponseEntity<MutationResponse<Void>> viaPolicy =
        AccountGainLossRetrievalResponsePolicy.map(
            AccountGainLossRetrievalException.systemFailure());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, viaPolicy.getStatusCode());
  }

  // ───────────────────────── ApimException ─────────────────────────

  @Test
  void apimException_statusFromExceptionSystemErrorLoggedAtErrorNoStackTrace() {
    var ex =
        new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE, "APIM failed");

    var response = handler.handleApimException(ex, exchangeWithRequestId("req-7"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_GATEWAY,
        ApiStatus.SYSTEM_ERROR,
        "DOWNSTREAM_SYSTEM",
        ErrorCodes.APIM_UPSTREAM_FAILURE,
        List.of(),
        "req-7",
        "message for " + ErrorCodes.APIM_UPSTREAM_FAILURE);
    assertLoggedOnce(Level.ERROR, "ApimException", ErrorCodes.APIM_UPSTREAM_FAILURE, false);
  }

  // ───────────────────────── InvalidNotificationRequestException ─────────────────────────

  @Test
  void invalidNotificationRequestException_badRequestValidationFailedLoggedAtWarn() {
    var response =
        handler.handleInvalidNotificationRequestException(
            new InvalidNotificationRequestException("invalid notification request"),
            exchangeWithRequestId("req-8"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        ErrorCodes.NOTIFICATION_REQUEST_INVALID,
        List.of(),
        "req-8",
        "message for " + ErrorCodes.NOTIFICATION_REQUEST_INVALID);
    assertLoggedOnce(
        Level.WARN,
        "InvalidNotificationRequestException",
        ErrorCodes.NOTIFICATION_REQUEST_INVALID,
        false);
  }

  // ───────────────────────── InvalidContributionRequestException ─────────────────────────

  @Test
  void invalidContributionRequestException_badRequestValidationFailedLoggedAtWarn() {
    var response =
        handler.handleInvalidContributionRequestException(
            new InvalidContributionRequestException("invalid contribution request"),
            exchangeWithRequestId("req-9"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        ErrorCodes.CONTRIBUTION_REQUEST_INVALID,
        List.of(),
        "req-9",
        "message for " + ErrorCodes.CONTRIBUTION_REQUEST_INVALID);
    assertLoggedOnce(
        Level.WARN,
        "InvalidContributionRequestException",
        ErrorCodes.CONTRIBUTION_REQUEST_INVALID,
        false);
  }

  // ───────────────────────── PortalAccessContextResolutionException ─────────────────────────

  @Test
  void portalAccessContextResolutionException_badRequestBusinessRejectedLoggedAtWarn() {
    var response =
        handler.handlePortalAccessContextResolutionException(
            new PortalAccessContextResolutionException(
                ErrorCodes.MEMBER_CONTEXT_INVALID, "Missing Account-Ref header"),
            exchangeWithRequestId("req-10"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.MEMBER_CONTEXT_INVALID,
        List.of(),
        "req-10",
        "message for " + ErrorCodes.MEMBER_CONTEXT_INVALID);
    assertLoggedOnce(
        Level.WARN,
        "PortalAccessContextResolutionException",
        ErrorCodes.MEMBER_CONTEXT_INVALID,
        false);
  }

  // ───────────────────────── ApplicationException (generic) ─────────────────────────

  @Test
  void genericApplicationException_badRequestBusinessRejectedLoggedAtWarn() {
    var response =
        handler.handleApplicationException(
            new ApplicationException(ErrorCodes.REQUEST_INVALID, "application rejected request"),
            exchangeWithRequestId("req-11"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.REQUEST_INVALID,
        List.of(),
        "req-11",
        "message for " + ErrorCodes.REQUEST_INVALID);
    assertLoggedOnce(Level.WARN, "ApplicationException", ErrorCodes.REQUEST_INVALID, false);
  }

  // ───────────────────────── Pre-login path suppresses resolved message ─────────────────────────

  @Test
  void preLoginAuthRequest_suppressesResolvedMessageForDelegateRoutedException() {
    var response =
        handler.handleApplicationException(
            new ApplicationException(ErrorCodes.REQUEST_INVALID, "application rejected request"),
            preLoginExchangeWithRequestId("req-27"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.REQUEST_INVALID,
        List.of(),
        "req-27",
        null);
  }

  // ───────────────────────── WebExchangeBindException ─────────────────────────

  @Test
  void webExchangeBindException_withFieldErrors_fieldTypeValidationFailedLoggedAtWarn()
      throws Exception {
    var response =
        handler.handleWebExchangeBindException(
            bindExceptionWithFieldError("notificationId", "notificationId must not be empty"),
            exchangeWithRequestId("req-12"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FIELD",
        ErrorCodes.REQUEST_VALIDATION_FAILED,
        List.of("notificationId"),
        "req-12",
        "message for " + ErrorCodes.REQUEST_VALIDATION_FAILED);
    assertLoggedOnce(
        Level.WARN, "WebExchangeBindException", ErrorCodes.REQUEST_VALIDATION_FAILED, false);
  }

  @Test
  void webExchangeBindException_withoutFieldErrors_formTypeValidationFailedLoggedAtWarn()
      throws Exception {
    var response =
        handler.handleWebExchangeBindException(
            bindExceptionWithoutFieldErrors(), exchangeWithRequestId("req-13"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        ErrorCodes.REQUEST_VALIDATION_FAILED,
        List.of(),
        "req-13",
        "message for " + ErrorCodes.REQUEST_VALIDATION_FAILED);
    assertLoggedOnce(
        Level.WARN, "WebExchangeBindException", ErrorCodes.REQUEST_VALIDATION_FAILED, false);
  }

  @Test
  void
      webExchangeBindException_withTwoFieldErrors_bothEntriesCarryTheSameResolvedMessageInFieldOrder()
          throws Exception {
    var response =
        handler.handleWebExchangeBindException(
            bindExceptionWithTwoFieldErrors(
                "notificationId",
                "notificationId must not be empty",
                "status",
                "status must not be empty"),
            exchangeWithRequestId("req-28"));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    MutationResponse<Void> mutation = response.getBody();
    assertNotNull(mutation);
    assertFalse(mutation.success());
    assertEquals(ApiStatus.VALIDATION_FAILED, mutation.status());
    assertEquals(2, mutation.errors().size());

    String expectedMessage = "message for " + ErrorCodes.REQUEST_VALIDATION_FAILED;
    ApiError first = mutation.errors().get(0);
    ApiError second = mutation.errors().get(1);
    assertEquals(List.of("notificationId"), first.targets());
    assertEquals(List.of("status"), second.targets());
    assertEquals(expectedMessage, first.message());
    assertEquals(expectedMessage, second.message());
    assertEquals("FIELD", first.type());
    assertEquals("FIELD", second.type());
    assertEquals(ErrorCodes.REQUEST_VALIDATION_FAILED, first.code());
    assertEquals(ErrorCodes.REQUEST_VALIDATION_FAILED, second.code());
  }

  // ───────────────────────── ServerWebInputException ─────────────────────────

  @Test
  void serverWebInputException_badRequestFormValidationFailedLoggedAtWarn() {
    var response =
        handler.handleServerWebInputException(
            new ServerWebInputException("payload is malformed"), exchangeWithRequestId("req-14"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        ErrorCodes.REQUEST_BODY_MALFORMED,
        List.of(),
        "req-14",
        "message for " + ErrorCodes.REQUEST_BODY_MALFORMED);
    assertLoggedOnce(
        Level.WARN, "ServerWebInputException", ErrorCodes.REQUEST_BODY_MALFORMED, false);
  }

  // ───────────────────────── AuthenticationException ─────────────────────────

  @Test
  void authenticationException_unauthorizedBusinessRejectedLoggedAtWarn() {
    var response =
        handler.handleAuthenticationException(
            new BadCredentialsException("bad credentials"), exchangeWithRequestId("req-15"));

    assertMutationFailure(
        response,
        HttpStatus.UNAUTHORIZED,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
        List.of(),
        "req-15",
        "message for " + ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
    assertLoggedOnce(
        Level.WARN, "BadCredentialsException", ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, false);
  }

  // ───────────────────────── AccessDeniedException ─────────────────────────

  @Test
  void accessDeniedException_forbiddenBusinessRejectedLoggedAtWarn() {
    var response =
        handler.handleAccessDeniedException(
            new AccessDeniedException("access denied"), exchangeWithRequestId("req-16"));

    assertMutationFailure(
        response,
        HttpStatus.FORBIDDEN,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.SECURITY_ACCESS_DENIED,
        List.of(),
        "req-16",
        "message for " + ErrorCodes.SECURITY_ACCESS_DENIED);
    assertLoggedOnce(Level.WARN, "AccessDeniedException", ErrorCodes.SECURITY_ACCESS_DENIED, false);
  }

  // ───────────────────────── Unexpected exception (catch-all) ─────────────────────────

  @Test
  void unexpectedException_internalServerErrorSystemErrorLoggedAtErrorWithStackTrace() {
    var response =
        handler.handleUnexpectedException(
            new IllegalStateException("unexpected"), exchangeWithRequestId("req-17"));

    assertMutationFailure(
        response,
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiStatus.SYSTEM_ERROR,
        "SYSTEM",
        ErrorCodes.SYSTEM_UNEXPECTED,
        List.of(),
        "req-17",
        "message for " + ErrorCodes.SYSTEM_UNEXPECTED);
    assertLoggedOnce(Level.ERROR, "IllegalStateException", ErrorCodes.SYSTEM_UNEXPECTED, true);
  }

  // ───────────────────────── InvalidPersonalInformationUpdateException ─────────────────────────

  @Test
  void invalidPersonalInformationUpdateException_badRequestFormValidationFailedLoggedAtWarn() {
    var response =
        handler.handleInvalidPersonalInformationUpdate(
            new InvalidPersonalInformationUpdateException(
                "No personal information fields were submitted."),
            exchangeWithRequestId("req-18"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID,
        List.of(),
        "req-18",
        "message for " + ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID);
    assertLoggedOnce(
        Level.WARN,
        "InvalidPersonalInformationUpdateException",
        ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID,
        false);
  }

  // ───────────────────────── CacheException ─────────────────────────

  @Test
  void cacheException_serviceUnavailableSystemErrorLoggedAtErrorNoStackTrace() {
    var response =
        handler.handleCacheException(
            new CacheException(
                "Cache get operation failed",
                new RuntimeException("redis://default:SECRET@redis.prod:6379 auth failure")),
            exchangeWithRequestId("req-19"));

    assertMutationFailure(
        response,
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "FORM",
        ErrorCodes.CACHE_UNEXPECTED,
        List.of(),
        "req-19",
        "message for " + ErrorCodes.CACHE_UNEXPECTED);
    assertLoggedOnce(Level.ERROR, "CacheException", ErrorCodes.CACHE_UNEXPECTED, false);
  }

  // ───────────────────────── FundPriceRetrievalException ─────────────────────────

  @Test
  void fundPriceRetrievalException_serviceUnavailableSystemErrorLoggedAtErrorNoStackTrace() {
    var response =
        handler.handleFundPriceRetrievalException(
            FundPriceRetrievalException.systemFailure(), exchangeWithRequestId("req-20"));

    assertMutationFailure(
        response,
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "FORM",
        ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE,
        List.of(),
        "req-20",
        "message for " + ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE);
    assertLoggedOnce(
        Level.ERROR,
        "FundPriceRetrievalException",
        ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE,
        false);
  }

  // ───────────────────────── FundPriceInvalidDateException ─────────────────────────

  @Test
  void fundPriceInvalidDateException_badRequestValidationFailedLoggedAtWarn() {
    var response =
        handler.handleFundPriceInvalidDateException(
            FundPriceInvalidDateException.forInvalidDate(), exchangeWithRequestId("req-21"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        ErrorCodes.FUND_PRICE_REQUEST_INVALID,
        List.of(),
        "req-21",
        "message for " + ErrorCodes.FUND_PRICE_REQUEST_INVALID);
    assertLoggedOnce(
        Level.WARN, "FundPriceInvalidDateException", ErrorCodes.FUND_PRICE_REQUEST_INVALID, false);
  }

  // ───────────────────────── FundComparisonRequestInvalidException ─────────────────────────

  @Test
  void
      fundComparisonRequestInvalidException_badRequestValidationFailedTypeValidationLoggedAtWarn() {
    var response =
        handler.handleFundComparisonRequestInvalidException(
            new FundComparisonRequestInvalidException("invalid fund comparison request"),
            exchangeWithRequestId("req-22"));

    assertMutationFailure(
        response,
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "VALIDATION",
        ErrorCodes.FUND_COMPARISON_REQUEST_INVALID,
        List.of(),
        "req-22",
        "message for " + ErrorCodes.FUND_COMPARISON_REQUEST_INVALID);
    assertLoggedOnce(
        Level.WARN,
        "FundComparisonRequestInvalidException",
        ErrorCodes.FUND_COMPARISON_REQUEST_INVALID,
        false);
  }

  // ───────────────────────── FundComparisonRetrievalException ─────────────────────────

  @Test
  void fundComparisonRetrievalException_serviceUnavailableSystemErrorLoggedAtErrorNoStackTrace() {
    var response =
        handler.handleFundComparisonRetrievalException(
            FundComparisonRetrievalException.systemFailure(), exchangeWithRequestId("req-23"));

    assertMutationFailure(
        response,
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "FORM",
        ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE,
        List.of(),
        "req-23",
        "message for " + ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE);
    assertLoggedOnce(
        Level.ERROR,
        "FundComparisonRetrievalException",
        ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE,
        false);
  }

  // ───────────────────────── FundFactSheetRetrievalException ─────────────────────────

  @Test
  void fundFactSheetRetrievalException_serviceUnavailableSystemErrorLoggedAtErrorNoStackTrace() {
    var response =
        handler.handleFundFactSheetRetrievalException(
            FundFactSheetRetrievalException.systemFailure(), exchangeWithRequestId("req-24"));

    assertMutationFailure(
        response,
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "SYSTEM",
        ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE,
        List.of(),
        "req-24",
        null);
    assertLoggedOnce(
        Level.ERROR,
        "FundFactSheetRetrievalException",
        ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE,
        false);
  }

  // ───────────────────────── Cross-cutting: X-Request-Id header absence ─────────────────────────

  @Test
  void xRequestIdHeaderIsOmittedWhenNotPresentInExchangeAttributes() {
    var response =
        handler.handleApimException(
            new ApimException(
                HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE, "APIM failed"),
            emptyExchange());

    assertNull(response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
  }

  // ───────────────────────── helpers ─────────────────────────

  private void assertMutationFailure(
      ResponseEntity<MutationResponse<Void>> response,
      HttpStatus expectedHttpStatus,
      ApiStatus expectedApiStatus,
      String expectedErrorType,
      String expectedErrorCode,
      List<String> expectedTargets,
      String expectedRequestId,
      String expectedMessage) {
    assertEquals(expectedHttpStatus, response.getStatusCode());
    assertEquals(
        expectedRequestId, response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

    MutationResponse<Void> mutation = response.getBody();
    assertNotNull(mutation);
    assertFalse(mutation.success());
    assertEquals(expectedApiStatus, mutation.status());
    assertNull(mutation.result());
    assertTrue(mutation.messages().isEmpty());
    assertEquals(1, mutation.errors().size());

    ApiError error = mutation.errors().getFirst();
    assertEquals(expectedErrorType, error.type());
    assertEquals(expectedErrorCode, error.code());
    assertEquals(expectedTargets, error.targets());
    assertEquals("ERROR", error.severity());
    assertEquals(SOURCE_SERVER, error.source());
    assertEquals(expectedMessage, error.message());
  }

  private void assertLoggedOnce(
      Level expectedLevel,
      String expectedExceptionType,
      String expectedErrorCode,
      boolean expectStackTrace) {
    assertEquals(1, listAppender.list.size());
    ILoggingEvent event = listAppender.list.getFirst();
    assertEquals(expectedLevel, event.getLevel());
    JsonNode payload = parse(event);
    assertEquals("http.api.error", payload.get("event").asText());
    assertEquals(expectedExceptionType, payload.get("exceptionType").asText());
    assertEquals(expectedErrorCode, payload.get("errorCode").asText());
    if (expectStackTrace) {
      assertNotNull(event.getThrowableProxy(), "expected a stack trace to be attached");
    } else {
      assertNull(event.getThrowableProxy(), "expected no stack trace to be attached");
    }
  }

  private JsonNode parse(ILoggingEvent event) {
    try {
      return OBJECT_MAPPER.readTree(event.getFormattedMessage());
    } catch (Exception e) {
      throw new AssertionError(
          "Expected valid JSON log payload but got: " + event.getFormattedMessage(), e);
    }
  }

  private static ServerWebExchange emptyExchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
  }

  private static MockServerWebExchange exchangeWithRequestId(String requestId) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.patch("/api/v1/notifications"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, requestId);
    return exchange;
  }

  private static MockServerWebExchange preLoginExchangeWithRequestId(String requestId) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, requestId);
    return exchange;
  }

  private static WebExchangeBindException bindExceptionWithFieldError(
      String fieldName, String defaultMessage) throws Exception {
    Method method =
        ApiExceptionHandlerCharacterizationTest.class.getDeclaredMethod(
            "notificationPatch", UpdateNotificationsReadStatusRequest.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    UpdateNotificationsReadStatusRequest target =
        new UpdateNotificationsReadStatusRequest(List.of());
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
    bindingResult.addError(new FieldError("request", fieldName, defaultMessage));
    return new WebExchangeBindException(parameter, bindingResult);
  }

  private static WebExchangeBindException bindExceptionWithTwoFieldErrors(
      String firstFieldName,
      String firstDefaultMessage,
      String secondFieldName,
      String secondDefaultMessage)
      throws Exception {
    Method method =
        ApiExceptionHandlerCharacterizationTest.class.getDeclaredMethod(
            "notificationPatch", UpdateNotificationsReadStatusRequest.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    UpdateNotificationsReadStatusRequest target =
        new UpdateNotificationsReadStatusRequest(List.of());
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
    bindingResult.addError(new FieldError("request", firstFieldName, firstDefaultMessage));
    bindingResult.addError(new FieldError("request", secondFieldName, secondDefaultMessage));
    return new WebExchangeBindException(parameter, bindingResult);
  }

  private static WebExchangeBindException bindExceptionWithoutFieldErrors() throws Exception {
    Method method =
        ApiExceptionHandlerCharacterizationTest.class.getDeclaredMethod(
            "notificationPatch", UpdateNotificationsReadStatusRequest.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    UpdateNotificationsReadStatusRequest target =
        new UpdateNotificationsReadStatusRequest(List.of());
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
    return new WebExchangeBindException(parameter, bindingResult);
  }

  @SuppressWarnings("unused")
  private void notificationPatch(@RequestBody UpdateNotificationsReadStatusRequest request) {
    // Method signature used only to construct MethodParameter for WebExchangeBindException.
  }

  private static ErrorMessageResolver testErrorMessageResolver() {
    return (errorCode, locale, accountEnv, trustCode, schemeType) -> "message for " + errorCode;
  }

  private static LoggingSanitizer testLoggingSanitizer() {
    MaskingProperties properties = new MaskingProperties();
    properties
        .getLogging()
        .setSensitiveTokens(
            List.of(
                "authorization",
                "token",
                "apiKey",
                "Certificate",
                "policyNo",
                "certNo",
                "memberId",
                "userId"));
    return new LoggingSanitizer(OBJECT_MAPPER, new MaskingService(properties));
  }
}
