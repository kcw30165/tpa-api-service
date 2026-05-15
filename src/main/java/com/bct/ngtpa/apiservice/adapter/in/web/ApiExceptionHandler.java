package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiErrorResponse;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
// import java.lang.reflect.ReflectiveOperationException;
import java.util.Locale;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    private final ErrorMessageResolver errorMessageResolver;
    private final LoggingSanitizer loggingSanitizer;

    public ApiExceptionHandler(ErrorMessageResolver errorMessageResolver, LoggingSanitizer loggingSanitizer) {
        this.errorMessageResolver = errorMessageResolver;
        this.loggingSanitizer = loggingSanitizer;
    }

    @ExceptionHandler(ApimException.class)
    public ResponseEntity<ApiErrorResponse> handleApimException(ApimException ex, ServerWebExchange exchange) {
        return buildErrorResponse(
                ex.getStatusCode(),
                resolveApimErrorCode(ex),
                exchange,
                ex,
                ex.getMessage(),
                true,
                false);
    }

    @ExceptionHandler(InvalidNotificationRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidNotificationRequestException(
            InvalidNotificationRequestException ex, ServerWebExchange exchange) {
        return handleApplicationException(ex, exchange, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidContributionRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidContributionRequestException(
            InvalidContributionRequestException ex, ServerWebExchange exchange) {
        return handleApplicationException(ex, exchange, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PortalAccessContextResolutionException.class)
    public ResponseEntity<ApiErrorResponse> handlePortalAccessContextResolutionException(
            PortalAccessContextResolutionException ex, ServerWebExchange exchange) {
        return handleApplicationException(ex, exchange, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException ex,
            ServerWebExchange exchange) {
        return handleApplicationException(ex, exchange, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiErrorResponse> handleWebExchangeBindException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            ErrorCodes.REQUEST_VALIDATION_FAILED,
            exchange,
            ex,
            firstValidationMessage(ex),
            false,
            false);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleServerWebInputException(
            ServerWebInputException ex, ServerWebExchange exchange) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            ErrorCodes.REQUEST_BODY_MALFORMED,
            exchange,
            ex,
            firstNonBlank(ex.getReason(), ex.getMessage()),
            false,
            false);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex, ServerWebExchange exchange) {
        return buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
            exchange,
            ex,
            ex.getMessage(),
            false,
            false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, ServerWebExchange exchange) {
        return buildErrorResponse(
            HttpStatus.FORBIDDEN,
            ErrorCodes.SECURITY_ACCESS_DENIED,
            exchange,
            ex,
            ex.getMessage(),
            false,
            false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex, ServerWebExchange exchange) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.SYSTEM_UNEXPECTED,
                exchange,
                ex,
                ex.getMessage(),
                true,
                true);
    }

    private String getRequestId(ServerWebExchange exchange) {
        return (String) exchange.getAttributes().get(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
    }

    private ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException ex,
            ServerWebExchange exchange,
            HttpStatus status) {
        return buildErrorResponse(status, ex.getErrorCode(), exchange, ex, ex.getMessage(), false, false);
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatusCode status,
            String errorCode,
            ServerWebExchange exchange,
            Throwable exception,
            String diagnosticMessage,
            boolean logAtError,
            boolean includeStackTrace) {
        logException(status, errorCode, exchange, exception, diagnosticMessage, logAtError, includeStackTrace);
        String requestId = getRequestId(exchange);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (requestId != null) {
            builder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        }
        return builder.body(ApiErrorResponse.of(errorCode, resolvePublicMessage(errorCode, exchange, exception)));
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

        String sanitizedMessage = loggingSanitizer.sanitizeText(firstNonBlank(diagnosticMessage, exception.getMessage()));
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
        return new ErrorMessageContext(
                firstNonBlank(requestParam(exchange, "lang"), requestLocale(exchange), contextValue(exception, "lang")),
                firstNonBlank(requestParam(exchange, "env"), contextValue(exception, "env")),
                firstNonBlank(requestParam(exchange, "trustCode"), contextValue(exception, "trustCode")),
                firstNonBlank(requestParam(exchange, "schemeType"), contextValue(exception, "schemeType")));
    }

    private String resolveApimErrorCode(ApimException ex) {
        return StringUtils.hasText(ex.getErrorCode()) ? ex.getErrorCode() : ErrorCodes.APIM_UPSTREAM_FAILURE;
    }

    private String requestParam(ServerWebExchange exchange, String name) {
        return trimToNull(exchange.getRequest().getQueryParams().getFirst(name));
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
