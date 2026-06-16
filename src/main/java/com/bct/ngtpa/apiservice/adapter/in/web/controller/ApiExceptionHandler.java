package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextResolver;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    private static final String SOURCE_SERVER = "SERVER";

    private final ErrorMessageResolver errorMessageResolver;
    private final LoggingSanitizer loggingSanitizer;
    private final PortalAccessContextResolver portalAccessContextResolver;

    public ApiExceptionHandler(ErrorMessageResolver errorMessageResolver, LoggingSanitizer loggingSanitizer) {
        this(errorMessageResolver, loggingSanitizer, null);
    }

    @Autowired
    public ApiExceptionHandler(
            ErrorMessageResolver errorMessageResolver,
            LoggingSanitizer loggingSanitizer,
            PortalAccessContextResolver portalAccessContextResolver) {
        this.errorMessageResolver = errorMessageResolver;
        this.loggingSanitizer = loggingSanitizer;
        this.portalAccessContextResolver = portalAccessContextResolver;
    }

    @ExceptionHandler(ApimException.class)
    public ResponseEntity<MutationResponse<Void>> handleApimException(ApimException ex, ServerWebExchange exchange) {
        String errorCode = resolveApimErrorCode(ex);
        ApiError error = ApiError.downstreamSystem(
                errorCode,
                resolvePublicMessage(errorCode, exchange, ex),
                SOURCE_SERVER);
        return buildMutationErrorResponse(
                ex.getStatusCode(),
                ApiStatus.DOWNSTREAM_ERROR,
                List.of(error),
                exchange,
                ex,
                ex.getMessage(),
                true,
                false);
    }

    @ExceptionHandler(InvalidNotificationRequestException.class)
    public ResponseEntity<MutationResponse<Void>> handleInvalidNotificationRequestException(
            InvalidNotificationRequestException ex, ServerWebExchange exchange) {
        return handleApplicationException(ex, exchange, HttpStatus.BAD_REQUEST, ApiStatus.VALIDATION_FAILED, "FORM");
    }

    @ExceptionHandler(InvalidContributionRequestException.class)
    public ResponseEntity<MutationResponse<Void>> handleInvalidContributionRequestException(
            InvalidContributionRequestException ex, ServerWebExchange exchange) {
        return handleApplicationException(ex, exchange, HttpStatus.BAD_REQUEST, ApiStatus.VALIDATION_FAILED, "FORM");
    }

    @ExceptionHandler(PortalAccessContextResolutionException.class)
    public ResponseEntity<MutationResponse<Void>> handlePortalAccessContextResolutionException(
            PortalAccessContextResolutionException ex, ServerWebExchange exchange) {
        ApiError error = ApiError.business(
                ex.getErrorCode(),
                resolvePublicMessage(ex.getErrorCode(), exchange, ex),
                List.of(),
                SOURCE_SERVER);
        return buildMutationErrorResponse(
                HttpStatus.BAD_REQUEST,
                ApiStatus.BUSINESS_REJECTED,
                List.of(error),
                exchange,
                ex,
                ex.getMessage(),
                false,
                false);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<MutationResponse<Void>> handleApplicationException(
            ApplicationException ex,
            ServerWebExchange exchange) {
        return handleApplicationException(ex, exchange, HttpStatus.BAD_REQUEST, ApiStatus.BUSINESS_REJECTED, "BUSINESS");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<MutationResponse<Void>> handleWebExchangeBindException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        List<ApiError> errors = validationErrors(ex, exchange);
        return buildMutationErrorResponse(
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                errors,
                exchange,
                ex,
                firstValidationMessage(ex),
                false,
                false);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<MutationResponse<Void>> handleServerWebInputException(
            ServerWebInputException ex, ServerWebExchange exchange) {
        String errorCode = ErrorCodes.REQUEST_BODY_MALFORMED;
        ApiError error = ApiError.form(
                errorCode,
                resolvePublicMessage(errorCode, exchange, ex),
                List.of(),
                SOURCE_SERVER);
        return buildMutationErrorResponse(
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                List.of(error),
                exchange,
                ex,
                firstNonBlank(ex.getReason(), ex.getMessage()),
                false,
                false);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<MutationResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, ServerWebExchange exchange) {
        String errorCode = ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED;
        ApiError error = ApiError.business(
                errorCode,
                resolvePublicMessage(errorCode, exchange, ex),
                List.of(),
                SOURCE_SERVER);
        return buildMutationErrorResponse(
                HttpStatus.UNAUTHORIZED,
                ApiStatus.BUSINESS_REJECTED,
                List.of(error),
                exchange,
                ex,
                ex.getMessage(),
                false,
                false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MutationResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, ServerWebExchange exchange) {
        String errorCode = ErrorCodes.SECURITY_ACCESS_DENIED;
        ApiError error = ApiError.business(
                errorCode,
                resolvePublicMessage(errorCode, exchange, ex),
                List.of(),
                SOURCE_SERVER);
        return buildMutationErrorResponse(
                HttpStatus.FORBIDDEN,
                ApiStatus.BUSINESS_REJECTED,
                List.of(error),
                exchange,
                ex,
                ex.getMessage(),
                false,
                false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MutationResponse<Void>> handleUnexpectedException(Exception ex, ServerWebExchange exchange) {
        String errorCode = ErrorCodes.SYSTEM_UNEXPECTED;
        ApiError error = ApiError.system(
                errorCode,
                resolvePublicMessage(errorCode, exchange, ex),
                SOURCE_SERVER);
        return buildMutationErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                List.of(error),
                exchange,
                ex,
                ex.getMessage(),
                true,
                true);
    }

    @ExceptionHandler(InvalidPersonalInformationUpdateException.class)
    public ResponseEntity<MutationResponse<Void>> handleInvalidPersonalInformationUpdate(
            InvalidPersonalInformationUpdateException ex,
            ServerWebExchange exchange) {
        String errorCode = ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID;
        ApiError error = ApiError.form(
                errorCode,
                resolvePublicMessage(errorCode, exchange, ex),
                List.of(),
                SOURCE_SERVER);
        return buildMutationErrorResponse(
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                List.of(error),
                exchange,
                ex,
                ex.getMessage(),
                false,
                false);
    }

    private ResponseEntity<MutationResponse<Void>> handleApplicationException(
            ApplicationException ex,
            ServerWebExchange exchange,
            HttpStatus status,
            ApiStatus apiStatus,
            String errorType) {
        ApiError error = new ApiError(
                errorType,
                ex.getErrorCode(),
                resolvePublicMessage(ex.getErrorCode(), exchange, ex),
                List.of(),
                "ERROR",
                SOURCE_SERVER);
        return buildMutationErrorResponse(
                status,
                apiStatus,
                List.of(error),
                exchange,
                ex,
                ex.getMessage(),
                false,
                false);
    }

    private ResponseEntity<MutationResponse<Void>> buildMutationErrorResponse(
            HttpStatusCode status,
            ApiStatus apiStatus,
            List<ApiError> errors,
            ServerWebExchange exchange,
            Throwable exception,
            String diagnosticMessage,
            boolean logAtError,
            boolean includeStackTrace) {
        List<ApiError> safeErrors = errors == null || errors.isEmpty()
                ? List.of(ApiError.system(
                        ErrorCodes.SYSTEM_UNEXPECTED,
                        resolvePublicMessage(ErrorCodes.SYSTEM_UNEXPECTED, exchange, exception),
                        SOURCE_SERVER))
                : List.copyOf(errors);
        logException(
                status,
                primaryErrorCode(safeErrors),
                exchange,
                exception,
                diagnosticMessage,
                logAtError,
                includeStackTrace);

        String requestId = getRequestId(exchange);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON);
        if (requestId != null) {
            builder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        }
        return builder.body(MutationResponse.<Void>failure(apiStatus, safeErrors));
    }

    private List<ApiError> validationErrors(WebExchangeBindException ex, ServerWebExchange exchange) {
        String errorCode = ErrorCodes.REQUEST_VALIDATION_FAILED;
        String publicMessage = resolvePublicMessage(errorCode, exchange, ex);
        List<ApiError> fieldErrors = ex.getFieldErrors().stream()
                .map(fieldError -> ApiError.field(
                        errorCode,
                        publicMessage,
                        fieldTarget(fieldError),
                        SOURCE_SERVER))
                .toList();
        if (!fieldErrors.isEmpty()) {
            return fieldErrors;
        }
        return List.of(ApiError.form(errorCode, publicMessage, List.of(), SOURCE_SERVER));
    }

    private List<String> fieldTarget(FieldError fieldError) {
        return StringUtils.hasText(fieldError.getField()) ? List.of(fieldError.getField()) : List.of();
    }

    private String primaryErrorCode(List<ApiError> errors) {
        return errors == null || errors.isEmpty() ? ErrorCodes.SYSTEM_UNEXPECTED : errors.getFirst().code();
    }

    private String getRequestId(ServerWebExchange exchange) {
        return (String) exchange.getAttributes().get(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
    }

    private void logException(
            HttpStatusCode status,
            String errorCode,
            ServerWebExchange exchange,
            Throwable exception,
            String diagnosticMessage,
            boolean logAtError,
            boolean includeStackTrace) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "http.api.error");
        String requestId = getRequestId(exchange);
        if (requestId != null) {
            event.put("requestId", requestId);
        }
        if (exchange.getRequest().getMethod() != null) {
            event.put("httpMethod", exchange.getRequest().getMethod().name());
        }
        event.put("path", exchange.getRequest().getPath().value());
        event.put("httpStatus", status.value());
        event.put("errorCode", errorCode);
        event.put("exceptionType", exception.getClass().getSimpleName());
        String sanitizedMessage = loggingSanitizer
                .sanitizeText(firstNonBlank(diagnosticMessage, exception.getMessage()));
        if (StringUtils.hasText(sanitizedMessage)) {
            event.put("sanitizedMessage", sanitizedMessage);
        }
        String payload = loggingSanitizer.toSafeString(event);
        if (logAtError) {
            if (includeStackTrace) {
                log.error("{}", payload, exception);
            } else {
                log.error("{}", payload);
            }
            return;
        }
        if (includeStackTrace) {
            log.warn("{}", payload, exception);
            return;
        }
        log.warn("{}", payload);
    }

    private String resolvePublicMessage(String errorCode, ServerWebExchange exchange, Throwable exception) {
        ErrorMessageContext context = resolveErrorMessageContext(exchange, exception);
        return errorMessageResolver.resolve(
                errorCode,
                context.locale(),
                context.accountEnv(),
                context.trustCode(),
                context.schemeType());
    }

    private ErrorMessageContext resolveErrorMessageContext(ServerWebExchange exchange, Throwable exception) {
        PortalAccessContext portalAccessContext = resolvePortalAccessContext(exchange);
        return new ErrorMessageContext(
                firstNonBlank(requestLocale(exchange), contextValue(exception, "lang")),
                accountEnv(portalAccessContext),
                trustCode(portalAccessContext),
                schemeType(portalAccessContext));
    }

    private PortalAccessContext resolvePortalAccessContext(ServerWebExchange exchange) {
        Object attribute = exchange.getAttributes().get(PortalAccessContextKeys.ATTRIBUTE_KEY);
        if (attribute instanceof PortalAccessContext portalAccessContext) {
            return portalAccessContext;
        }
        if (portalAccessContextResolver == null) {
            return null;
        }
        try {
            var contextMono = portalAccessContextResolver.currentOrEmpty();
            return contextMono == null ? null : contextMono.block();
        } catch (RuntimeException exception) {
            log.warn("Unable to read current PortalAccessContext for API error message context: {}",
                    loggingSanitizer.toSafeString(exception.getMessage()));
            return null;
        }
    }

    private String accountEnv(PortalAccessContext context) {
        return context == null || context.account() == null ? null : trimToNull(context.account().accountEnv());
    }

    private String trustCode(PortalAccessContext context) {
        return context == null || context.account() == null ? null : trimToNull(context.account().trustCode());
    }

    private String schemeType(PortalAccessContext context) {
        return context == null || context.account() == null ? null : trimToNull(context.account().schemeType());
    }

    private String resolveApimErrorCode(ApimException ex) {
        return StringUtils.hasText(ex.getErrorCode()) ? ex.getErrorCode() : ErrorCodes.APIM_UPSTREAM_FAILURE;
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
        String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            return trimToNull(target.getClass().getMethod(getterName).invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String firstValidationMessage(WebExchangeBindException ex) {
        return ex.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(ex.getMessage());
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

    private record ErrorMessageContext(String locale, String accountEnv, String trustCode, String schemeType) {
    }
}

