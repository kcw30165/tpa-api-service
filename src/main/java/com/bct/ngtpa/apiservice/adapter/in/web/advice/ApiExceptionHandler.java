package com.bct.ngtpa.apiservice.adapter.in.web.advice;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorContext;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorDescriptor;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorLogDirective;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistry;
import com.bct.ngtpa.apiservice.adapter.in.web.error.LogLevel;
import com.bct.ngtpa.apiservice.adapter.in.web.error.MessagePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
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
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * Sub-task 1.7 (M1 closure, Option A): every {@code handleXxx} method is now a one-line delegation
 * to {@link #delegate}, including the four that previously built their status/body/log inline
 * (AccountBalance, AccountGainLoss, FundFactSheet, WebExchangeBind) -- their mappings now live in
 * {@code AccountBalanceExceptionMappings}, {@code AccountGainLossExceptionMappings}, {@code
 * FundFactSheetExceptionMappings} and {@code WebExchangeBindExceptionMappings} respectively. The
 * method names and {@code @ExceptionHandler} annotations are kept as-is in this sub-task (sub-task
 * 1.8 collapses them to a single {@code handle(Exception, ServerWebExchange)} method, per A1).
 */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
  private static final String SOURCE_SERVER = "SERVER";

  private final ErrorMessageResolver errorMessageResolver;
  private final LoggingSanitizer loggingSanitizer;
  private final ExceptionMappingRegistry registry;

  @Autowired
  public ApiExceptionHandler(
      ErrorMessageResolver errorMessageResolver,
      LoggingSanitizer loggingSanitizer,
      ExceptionMappingRegistry registry) {
    this.errorMessageResolver = errorMessageResolver;
    this.loggingSanitizer = loggingSanitizer;
    this.registry = registry;
  }

  @ExceptionHandler(RegistrationLookupDownstreamException.class)
  public ResponseEntity<MutationResponse<Void>> handleRegistrationLookupDownstreamException(
      RegistrationLookupDownstreamException exception, ServerWebExchange exchange) {
    return delegate(exception, exchange);
  }

  @ExceptionHandler(com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException.class)
  public ResponseEntity<MutationResponse<Void>> handleApimProfileResolutionException(
      com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException ex,
      ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(AccountBalanceRetrievalException.class)
  public ResponseEntity<MutationResponse<Void>> handleAccountBalanceRetrievalException(
      AccountBalanceRetrievalException exception, ServerWebExchange exchange) {
    return delegate(exception, exchange);
  }

  @ExceptionHandler(AccountGainLossRetrievalException.class)
  public ResponseEntity<MutationResponse<Void>> handleAccountGainLossRetrievalException(
      AccountGainLossRetrievalException exception, ServerWebExchange exchange) {
    return delegate(exception, exchange);
  }

  @ExceptionHandler(ApimException.class)
  public ResponseEntity<MutationResponse<Void>> handleApimException(
      ApimException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(InvalidNotificationRequestException.class)
  public ResponseEntity<MutationResponse<Void>> handleInvalidNotificationRequestException(
      InvalidNotificationRequestException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(InvalidContributionRequestException.class)
  public ResponseEntity<MutationResponse<Void>> handleInvalidContributionRequestException(
      InvalidContributionRequestException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(PortalAccessContextResolutionException.class)
  public ResponseEntity<MutationResponse<Void>> handlePortalAccessContextResolutionException(
      PortalAccessContextResolutionException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(ApplicationException.class)
  public ResponseEntity<MutationResponse<Void>> handleApplicationException(
      ApplicationException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<MutationResponse<Void>> handleWebExchangeBindException(
      WebExchangeBindException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(ServerWebInputException.class)
  public ResponseEntity<MutationResponse<Void>> handleServerWebInputException(
      ServerWebInputException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<MutationResponse<Void>> handleAuthenticationException(
      AuthenticationException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<MutationResponse<Void>> handleAccessDeniedException(
      AccessDeniedException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<MutationResponse<Void>> handleUnexpectedException(
      Exception ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(InvalidPersonalInformationUpdateException.class)
  public ResponseEntity<MutationResponse<Void>> handleInvalidPersonalInformationUpdate(
      InvalidPersonalInformationUpdateException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(CacheException.class)
  public ResponseEntity<MutationResponse<Void>> handleCacheException(
      CacheException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(FundPriceRetrievalException.class)
  public ResponseEntity<MutationResponse<Void>> handleFundPriceRetrievalException(
      FundPriceRetrievalException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(FundPriceInvalidDateException.class)
  public ResponseEntity<MutationResponse<Void>> handleFundPriceInvalidDateException(
      FundPriceInvalidDateException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(FundComparisonRequestInvalidException.class)
  public ResponseEntity<MutationResponse<Void>> handleFundComparisonRequestInvalidException(
      FundComparisonRequestInvalidException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(FundComparisonRetrievalException.class)
  public ResponseEntity<MutationResponse<Void>> handleFundComparisonRetrievalException(
      FundComparisonRetrievalException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  @ExceptionHandler(FundFactSheetRetrievalException.class)
  public ResponseEntity<MutationResponse<Void>> handleFundFactSheetRetrievalException(
      FundFactSheetRetrievalException ex, ServerWebExchange exchange) {
    return delegate(ex, exchange);
  }

  // Helper methods
  private <T extends Throwable> ResponseEntity<MutationResponse<Void>> delegate(
      T exception, ServerWebExchange exchange) {
    ErrorContext context = ErrorContext.from(exchange);
    ErrorDescriptor descriptor = registry.resolve(exception, context);
    List<ApiError> errors =
        descriptor.errors().stream()
            .map(
                entry ->
                    new ApiError(
                        entry.type(),
                        entry.code(),
                        entry.policy() == MessagePolicy.RESOLVE
                            ? resolvePublicMessage(entry.code(), exchange, exception)
                            : null,
                        entry.targets(),
                        "ERROR",
                        SOURCE_SERVER))
            .toList();
    return buildMutationErrorResponse(
        descriptor.httpStatus(),
        descriptor.apiStatus(),
        errors,
        exchange,
        exception,
        registry.diagnosticMessage(exception),
        descriptor.logLevel() == LogLevel.ERROR,
        descriptor.includeStackTrace(),
        descriptor.extraLogFields());
  }

  private ResponseEntity<MutationResponse<Void>> buildMutationErrorResponse(
      HttpStatusCode status,
      ApiStatus apiStatus,
      List<ApiError> errors,
      ServerWebExchange exchange,
      Throwable exception,
      String diagnosticMessage,
      boolean logAtError,
      boolean includeStackTrace,
      Map<String, Object> extraLogFields) {
    List<ApiError> safeErrors =
        errors == null || errors.isEmpty()
            ? List.of(
                ApiError.system(
                    ErrorCodes.SYSTEM_UNEXPECTED,
                    resolvePublicMessage(ErrorCodes.SYSTEM_UNEXPECTED, exchange, exception),
                    SOURCE_SERVER))
            : List.copyOf(errors);
    ErrorEventLogger.log(
        log,
        loggingSanitizer,
        new ErrorLogDirective(
            status, primaryErrorCode(safeErrors), logAtError, includeStackTrace, extraLogFields),
        exchange,
        exception,
        diagnosticMessage);

    String requestId = getRequestId(exchange);
    return ErrorResponseRenderer.render(status, apiStatus, safeErrors, requestId);
  }

  private String primaryErrorCode(List<ApiError> errors) {
    return errors == null || errors.isEmpty()
        ? ErrorCodes.SYSTEM_UNEXPECTED
        : errors.getFirst().code();
  }

  private String getRequestId(ServerWebExchange exchange) {
    return (String) exchange.getAttributes().get(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
  }

  private String resolvePublicMessage(
      String errorCode, ServerWebExchange exchange, Throwable exception) {
    if (isPreLoginAuthRequest(exchange)) {
      return null;
    }
    ErrorMessageContext context = resolveErrorMessageContext(exchange, exception);
    return errorMessageResolver.resolve(
        errorCode,
        context.locale(),
        context.accountEnv(),
        context.trustCode(),
        context.schemeType());
  }

  private ErrorMessageContext resolveErrorMessageContext(
      ServerWebExchange exchange, Throwable exception) {
    PortalAccessContext portalAccessContext = resolvePortalAccessContext(exchange);
    return new ErrorMessageContext(
        firstNonBlank(requestLocale(exchange), contextValue(exception, "lang")),
        accountEnv(portalAccessContext),
        trustCode(portalAccessContext),
        schemeType(portalAccessContext));
  }

  private PortalAccessContext resolvePortalAccessContext(ServerWebExchange exchange) {
    if (isPreLoginAuthRequest(exchange)) {
      return null;
    }
    Object attribute = exchange.getAttributes().get(PortalAccessContextKeys.ATTRIBUTE_KEY);
    return attribute instanceof PortalAccessContext portalAccessContext
        ? portalAccessContext
        : null;
  }

  private boolean isPreLoginAuthRequest(ServerWebExchange exchange) {
    if (exchange == null || exchange.getRequest() == null) {
      return false;
    }
    String path = exchange.getRequest().getPath().value();
    return path != null
        && (path.startsWith("/api/v1/auth/registration")
            || path.startsWith("/api/v1/auth/captcha")
            || path.startsWith("/api/v1/auth/login")
            || path.startsWith("/api/v1/auth/forgot-password")
            || path.startsWith("/api/v1/auth/username-recovery"));
  }

  private String accountEnv(PortalAccessContext context) {
    return context == null || context.account() == null
        ? null
        : trimToNull(context.account().accountEnv());
  }

  private String trustCode(PortalAccessContext context) {
    return context == null || context.account() == null
        ? null
        : trimToNull(context.account().trustCode());
  }

  private String schemeType(PortalAccessContext context) {
    return context == null || context.account() == null
        ? null
        : trimToNull(context.account().schemeType());
  }

  private String requestLocale(ServerWebExchange exchange) {
    try {
      return exchange.getRequest().getHeaders().getAcceptLanguageAsLocales().stream()
          .findFirst()
          .map(Locale::toLanguageTag)
          .orElse(null);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private String contextValue(Throwable exception, String propertyName) {
    if (!(exception instanceof WebExchangeBindException bindException)) {
      return null;
    }
    Object target = bindException.getTarget();
    if (target == null) {
      return null;
    }
    try {
      return trimToNull(target.getClass().getMethod(propertyName).invoke(target));
    } catch (ReflectiveOperationException ignored) {
      return getterContextValue(target, propertyName);
    }
  }

  private String getterContextValue(Object target, String propertyName) {
    String getterName =
        "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    try {
      return trimToNull(target.getClass().getMethod(getterName).invoke(target));
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private String firstNonBlank(String first, String second) {
    return StringUtils.hasText(first) ? first : second;
  }

  private String firstNonBlank(String first, String second, String third) {
    return firstNonBlank(firstNonBlank(first, second), third);
  }

  private String trimToNull(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return StringUtils.hasText(text) ? text : null;
  }

  private record ErrorMessageContext(
      String locale, String accountEnv, String trustCode, String schemeType) {}
}
