package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
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

class ApiExceptionHandlerMutationResponseContractTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler(errorMessageResolver(), loggingSanitizer());

    @Test
    void invalidNotificationRequestUsesMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handleInvalidNotificationRequestException(
                new InvalidNotificationRequestException("invalid notification request"),
                exchangeWithRequestId("REQ-001"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FORM",
                ErrorCodes.NOTIFICATION_REQUEST_INVALID,
                List.of(),
                "REQ-001");
    }

    @Test
    void invalidContributionRequestUsesMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handleInvalidContributionRequestException(
                new InvalidContributionRequestException("invalid contribution request"),
                exchangeWithRequestId("REQ-002"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FORM",
                ErrorCodes.CONTRIBUTION_REQUEST_INVALID,
                List.of(),
                "REQ-002");
    }

    @Test
    void bindValidationFailureUsesFieldTargetInMutationFailureEnvelope() throws Exception {
        ResponseEntity<?> response = handler.handleWebExchangeBindException(
                bindException("notificationId", "notificationId must not be empty"),
                exchangeWithRequestId("REQ-003"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FIELD",
                ErrorCodes.REQUEST_VALIDATION_FAILED,
                List.of("notificationId"),
                "REQ-003");
    }

    @Test
    void malformedBodyUsesFormValidationMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handleServerWebInputException(
                new ServerWebInputException("payload is malformed"),
                exchangeWithRequestId("REQ-004"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FORM",
                ErrorCodes.REQUEST_BODY_MALFORMED,
                List.of(),
                "REQ-004");
    }

    @Test
    void applicationExceptionUsesBusinessRejectedMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handleApplicationException(
                new ApplicationException(ErrorCodes.REQUEST_INVALID, "application rejected request"),
                exchangeWithRequestId("REQ-005"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.REQUEST_INVALID,
                List.of(),
                "REQ-005");
    }

    @Test
    void invalidPersonalInformationUpdateUsesValidationMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handleInvalidPersonalInformationUpdate(
                new InvalidPersonalInformationUpdateException("No personal information fields were submitted."),
                exchangeWithRequestId("REQ-006"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FORM",
                ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID,
                List.of(),
                "REQ-006");
    }

    @Test
    void missingPortalContextUsesBusinessMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handlePortalAccessContextResolutionException(
                new PortalAccessContextResolutionException(
                        ErrorCodes.MEMBER_CONTEXT_INVALID,
                        "Missing Account-Ref header for selected-account API"),
                exchangeWithRequestId("REQ-007"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.MEMBER_CONTEXT_INVALID,
                List.of(),
                "REQ-007");
    }

    @Test
    void authenticationExceptionUsesMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handleAuthenticationException(
                new BadCredentialsException("bad credentials"),
                exchangeWithRequestId("REQ-008"));

        assertMutationFailure(
                response,
                HttpStatus.UNAUTHORIZED,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
                List.of(),
                "REQ-008");
    }

    @Test
    void accessDeniedExceptionUsesMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handleAccessDeniedException(
                new AccessDeniedException("access denied"),
                exchangeWithRequestId("REQ-009"));

        assertMutationFailure(
                response,
                HttpStatus.FORBIDDEN,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.SECURITY_ACCESS_DENIED,
                List.of(),
                "REQ-009");
    }

    @Test
    void unexpectedExceptionUsesSystemMutationFailureEnvelope() {
        ResponseEntity<?> response = handler.handleUnexpectedException(
                new IllegalStateException("policyNo=123 should not leak"),
                exchangeWithRequestId("REQ-010"));

        assertMutationFailure(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                ErrorCodes.SYSTEM_UNEXPECTED,
                List.of(),
                "REQ-010");
    }

    @Test
    void apimExceptionUsesDownstreamMutationFailureEnvelopeWithRequestIdHeader() {
        ResponseEntity<?> response = handler.handleApimException(
                new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE, "APIM failed"),
                exchangeWithRequestId("REQ-011"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_GATEWAY,
                ApiStatus.DOWNSTREAM_ERROR,
                "DOWNSTREAM_SYSTEM",
                ErrorCodes.APIM_UPSTREAM_FAILURE,
                List.of(),
                "REQ-011");
    }

    private static void assertMutationFailure(
            ResponseEntity<?> response,
            HttpStatus expectedHttpStatus,
            ApiStatus expectedApiStatus,
            String expectedErrorType,
            String expectedErrorCode,
            List<String> expectedTargets,
            String expectedRequestId) {
        assertEquals(expectedHttpStatus, response.getStatusCode());
        assertEquals(expectedRequestId, response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));

        Object body = response.getBody();
        assertNotNull(body);
        MutationResponse<?> mutation = assertInstanceOf(MutationResponse.class, body);

        assertFalse(mutation.success());
        assertEquals(expectedApiStatus, mutation.status());
        assertNull(mutation.result());
        assertTrue(mutation.messages().isEmpty());
        assertEquals(1, mutation.errors().size());

        ApiError error = mutation.errors().getFirst();
        assertEquals(expectedErrorType, error.type());
        assertEquals(expectedErrorCode, error.code());
        assertEquals("message for " + expectedErrorCode, error.message());
        assertEquals(expectedTargets, error.targets());
        assertEquals("ERROR", error.severity());
        assertEquals("SERVER", error.source());
    }

    private static WebExchangeBindException bindException(String fieldName, String defaultMessage) throws Exception {
        Method method = ApiExceptionHandlerMutationResponseContractTest.class.getDeclaredMethod(
                "notificationPatch",
                UpdateNotificationsReadStatusRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        UpdateNotificationsReadStatusRequest target = new UpdateNotificationsReadStatusRequest(List.of());
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", fieldName, defaultMessage));
        return new WebExchangeBindException(parameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private void notificationPatch(@RequestBody UpdateNotificationsReadStatusRequest request) {
        // Method signature used only to construct MethodParameter for WebExchangeBindException.
    }

    private static ServerWebExchange exchangeWithRequestId(String requestId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.patch("/api/v1/notifications"));
        exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, requestId);
        return exchange;
    }

    private static ErrorMessageResolver errorMessageResolver() {
        return (errorCode, locale, accountEnv, trustCode, schemeType) -> "message for " + errorCode;
    }

    private static LoggingSanitizer loggingSanitizer() {
        return new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties());
    }
}
