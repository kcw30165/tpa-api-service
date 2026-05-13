package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiErrorResponse;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.exception.MemberContextResolutionException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final ErrorMessageResolver errorMessageResolver;

    public ApiExceptionHandler(ErrorMessageResolver errorMessageResolver) {
        this.errorMessageResolver = errorMessageResolver;
    }

    @ExceptionHandler(ApimException.class)
    public ResponseEntity<ApiErrorResponse> handleApimException(ApimException ex, ServerWebExchange exchange) {
        String requestId = getRequestId(exchange);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatusCode());
        if (requestId != null) {
            builder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        }
        return builder.body(ApiErrorResponse.of(ex.getErrorCode(), resolvePublicMessage(ex.getErrorCode())));
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

    @ExceptionHandler(MemberContextResolutionException.class)
    public ResponseEntity<ApiErrorResponse> handleMemberContextResolutionException(
            MemberContextResolutionException ex, ServerWebExchange exchange) {
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
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCodes.REQUEST_VALIDATION_FAILED, exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleServerWebInputException(
            ServerWebInputException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCodes.REQUEST_BODY_MALFORMED, exchange);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, exchange);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ErrorCodes.SECURITY_ACCESS_DENIED, exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED, exchange);
    }

    private String getRequestId(ServerWebExchange exchange) {
        return (String) exchange.getAttributes().get(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
    }

    private ResponseEntity<ApiErrorResponse> buildApplicationErrorResponse(
            ApplicationException ex,
            ResponseEntity.BodyBuilder builder) {
        return builder.body(ApiErrorResponse.of(ex.getErrorCode(), resolvePublicMessage(ex.getErrorCode())));
    }

    private ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException ex,
            ServerWebExchange exchange,
            HttpStatus status) {
        return buildErrorResponse(status, ex.getErrorCode(), exchange);
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String errorCode,
            ServerWebExchange exchange) {
        String requestId = getRequestId(exchange);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (requestId != null) {
            builder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        }
        return builder.body(ApiErrorResponse.of(errorCode, resolvePublicMessage(errorCode)));
    }

    private String resolvePublicMessage(String errorCode) {
        return errorMessageResolver.resolve(errorCode, null, null, null, null);
    }
}
