package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiErrorResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.Optional;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApimException.class)
    public ResponseEntity<ApiErrorResponse> handleApimException(ApimException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ApimCryptoException.class)
    public ResponseEntity<ApiErrorResponse> handleApimCryptoException(ApimCryptoException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), ex.getMessage()));
    }

    @ExceptionHandler(InvalidNotificationRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidNotificationRequestException(
            InvalidNotificationRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(String.valueOf(HttpStatus.BAD_REQUEST.value()), ex.getMessage()));
    }

        @ExceptionHandler(WebExchangeBindException.class)
        public ResponseEntity<ApiErrorResponse> handleWebExchangeBindException(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .filter(msg -> msg != null && !msg.isBlank())
            .findFirst()
            .orElse("Invalid request payload.");

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.of(String.valueOf(HttpStatus.BAD_REQUEST.value()), message));
        }

        @ExceptionHandler(ServerWebInputException.class)
        public ResponseEntity<ApiErrorResponse> handleServerWebInputException(ServerWebInputException ex) {
        String message = Optional.ofNullable(ex.getReason())
            .filter(reason -> !reason.isBlank())
            .orElse("Invalid request payload.");

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.of(String.valueOf(HttpStatus.BAD_REQUEST.value()), message));
        }
}
