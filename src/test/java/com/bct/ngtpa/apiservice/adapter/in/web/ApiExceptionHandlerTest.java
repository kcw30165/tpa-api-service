package com.bct.ngtpa.apiservice.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiErrorResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsApimExceptionToItsStatusAndErrorCode() {
        ResponseEntity<ApiErrorResponse> response = handler.handleApimException(
                new ApimException(HttpStatus.BAD_GATEWAY, "UPSTREAM_FAILURE", "APIM failure"));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("UPSTREAM_FAILURE", response.getBody().errorCode());
        assertEquals("APIM failure", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void mapsApimCryptoExceptionToInternalServerError() {
        ResponseEntity<ApiErrorResponse> response = handler.handleApimCryptoException(
                new ApimCryptoException("Crypto failed"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("500", response.getBody().errorCode());
        assertEquals("Crypto failed", response.getBody().message());
    }

    @Test
    void mapsInvalidNotificationRequestExceptionToBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidNotificationRequestException(
                new InvalidNotificationRequestException("Invalid request"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("400", response.getBody().errorCode());
        assertEquals("Invalid request", response.getBody().message());
    }

    @Test
    void usesFirstFieldErrorMessageForBindFailures() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "field", "must not be blank"));

        ResponseEntity<ApiErrorResponse> response = handler.handleWebExchangeBindException(
                new WebExchangeBindException(methodParameter(), bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("400", response.getBody().errorCode());
        assertEquals("must not be blank", response.getBody().message());
    }

    @Test
    void fallsBackForBlankBindFailureMessages() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "field", null, false, null, null, "  "));

        ResponseEntity<ApiErrorResponse> response = handler.handleWebExchangeBindException(
                new WebExchangeBindException(methodParameter(), bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request payload.", response.getBody().message());
    }

    @Test
    void usesServerInputReasonWhenPresent() {
        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInputException(
                new ServerWebInputException("Malformed JSON"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("400", response.getBody().errorCode());
        assertEquals("Malformed JSON", response.getBody().message());
    }

    @Test
    void fallsBackForBlankServerInputReason() {
        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInputException(
                new ServerWebInputException("  "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request payload.", response.getBody().message());
    }

    private static MethodParameter methodParameter() throws Exception {
        Method method = ApiExceptionHandlerTest.class.getDeclaredMethod("sampleHandler", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void sampleHandler(String requestBody) {
    }
}