package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;
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
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
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
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
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

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler(
                errorMessageResolver(),
                loggingSanitizer(),
                currentPortalAccessContextProvider()
        );

    @Test
    void mapsInvalidNotificationRequestExceptionToValidationMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleInvalidNotificationRequestException(
                new InvalidNotificationRequestException("invalid notification request"),
                exchangeWithRequestId("REQ-NOTIFICATION"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FORM",
                ErrorCodes.NOTIFICATION_REQUEST_INVALID,
                List.of(),
                "REQ-NOTIFICATION");
    }

    @Test
    void mapsInvalidContributionRequestExceptionToValidationMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleInvalidContributionRequestException(
                new InvalidContributionRequestException("invalid contribution request"),
                exchangeWithRequestId("REQ-CONTRIBUTION"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FORM",
                ErrorCodes.CONTRIBUTION_REQUEST_INVALID,
                List.of(),
                "REQ-CONTRIBUTION");
    }

    @Test
    void mapsBindFailuresToFieldValidationMutationFailure() throws Exception {
        ResponseEntity<MutationResponse<Void>> response = handler.handleWebExchangeBindException(
                bindException("notificationId", "notificationId must not be empty"),
                exchangeWithRequestId("REQ-BIND"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FIELD",
                ErrorCodes.REQUEST_VALIDATION_FAILED,
                List.of("notificationId"),
                "REQ-BIND");
    }

    @Test
    void mapsServerInputExceptionToFormValidationMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleServerWebInputException(
                new ServerWebInputException("payload is malformed"),
                exchangeWithRequestId("REQ-MALFORMED"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FORM",
                ErrorCodes.REQUEST_BODY_MALFORMED,
                List.of(),
                "REQ-MALFORMED");
    }

    @Test
    void mapsGenericApplicationExceptionToBusinessMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleApplicationException(
                new ApplicationException(ErrorCodes.REQUEST_INVALID, "application rejected request"),
                exchangeWithRequestId("REQ-APPLICATION"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.REQUEST_INVALID,
                List.of(),
                "REQ-APPLICATION");
    }

    @Test
    void mapsInvalidPersonalInformationUpdateToValidationMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleInvalidPersonalInformationUpdate(
                new InvalidPersonalInformationUpdateException("No personal information fields were submitted."),
                exchangeWithRequestId("REQ-PI"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.VALIDATION_FAILED,
                "FORM",
                ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID,
                List.of(),
                "REQ-PI");
    }

    @Test
    void mapsPortalAccessContextFailureToBusinessMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handlePortalAccessContextResolutionException(
                new PortalAccessContextResolutionException(
                        ErrorCodes.MEMBER_CONTEXT_INVALID,
                        "Missing Account-Ref header for selected-account API"),
                exchangeWithRequestId("REQ-CONTEXT"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_REQUEST,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.MEMBER_CONTEXT_INVALID,
                List.of(),
                "REQ-CONTEXT");
    }

    @Test
    void mapsAuthenticationExceptionToBusinessMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleAuthenticationException(
                new BadCredentialsException("bad credentials"),
                exchangeWithRequestId("REQ-AUTHN"));

        assertMutationFailure(
                response,
                HttpStatus.UNAUTHORIZED,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
                List.of(),
                "REQ-AUTHN");
    }

    @Test
    void mapsAccessDeniedExceptionToBusinessMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleAccessDeniedException(
                new AccessDeniedException("access denied"),
                exchangeWithRequestId("REQ-AUTHZ"));

        assertMutationFailure(
                response,
                HttpStatus.FORBIDDEN,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.SECURITY_ACCESS_DENIED,
                List.of(),
                "REQ-AUTHZ");
    }

    @Test
    void mapsUnknownExceptionToSystemMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleUnexpectedException(
                new IllegalStateException("policyNo=123 should not leak"),
                exchangeWithRequestId("REQ-SYSTEM"));

        assertMutationFailure(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                ErrorCodes.SYSTEM_UNEXPECTED,
                List.of(),
                "REQ-SYSTEM");
    }

    @Test
    void mapsApimExceptionToDownstreamMutationFailure() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleApimException(
                new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE, "APIM failed"),
                exchangeWithRequestId("REQ-APIM"));

        assertMutationFailure(
                response,
                HttpStatus.BAD_GATEWAY,
                ApiStatus.DOWNSTREAM_ERROR,
                "DOWNSTREAM_SYSTEM",
                ErrorCodes.APIM_UPSTREAM_FAILURE,
                List.of(),
                "REQ-APIM");
    }


    @Test
    void usesAcceptLanguageHeaderAndIgnoresLangQueryParameterWhenResolvingPublicMessages() {
        java.util.concurrent.atomic.AtomicReference<String> capturedLocale = new java.util.concurrent.atomic.AtomicReference<>();
        ApiExceptionHandler localHandler = new ApiExceptionHandler(
                (errorCode, locale, accountEnv, trustCode, schemeType) -> {
                    capturedLocale.set(locale);
                    return "message for " + errorCode;
                },
                loggingSanitizer(),
                currentPortalAccessContextProvider()
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/notifications?lang=zh-HK")
                        .header("Accept-Language", "en-US"));
        exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "REQ-LANG");

        ResponseEntity<MutationResponse<Void>> response = localHandler.handleUnexpectedException(
                new IllegalStateException("unexpected"),
                exchange);

        assertEquals("en-US", capturedLocale.get());
        assertNotNull(response.getBody());
        assertEquals("message for " + ErrorCodes.SYSTEM_UNEXPECTED, response.getBody().errors().getFirst().message());
    }

    @Test
    void resolvesAccountDimensionsFromCurrentPortalAccessContextAndIgnoresLegacyQueryParameters() {
        ApiExceptionHandler localHandler = new ApiExceptionHandler(
                (errorCode, locale, accountEnv, trustCode, schemeType) -> {
                    assertEquals("zh-HK", locale);
                    assertEquals("CTX-ENV", accountEnv);
                    assertEquals("CTX-TRUST", trustCode);
                    assertEquals("CTX-SCHEME", schemeType);
                    return "contextual message";
                },
                loggingSanitizer(),
                currentPortalAccessContextProvider()
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test")
                        .queryParam("env", "QUERY-ENV")
                        .queryParam("trustCode", "QUERY-TRUST")
                        .queryParam("schemeType", "QUERY-SCHEME")
                        .header("Accept-Language", "zh-HK")
                        .header("Account-Ref", "ACC-CTX"));
        exchange.getAttributes().put(
                PortalAccessContextKeys.ATTRIBUTE_KEY,
                new PortalAccessContext(
                        null, new AccountContext(
                                "ACC-CTX",
                                "CTX-ENV",
                                "POL-001",
                                "CERT-001",
                                "CTX-TRUST",
                                "CTX-SCHEME",
                                null,
                                null)));

        ResponseEntity<MutationResponse<Void>> response = localHandler.handleUnexpectedException(
                new IllegalStateException("unexpected"),
                exchange);

        assertNotNull(response.getBody());
        assertEquals("contextual message", response.getBody().errors().getFirst().message());
    }

    private static void assertMutationFailure(
            ResponseEntity<MutationResponse<Void>> response,
            HttpStatus expectedHttpStatus,
            ApiStatus expectedApiStatus,
            String expectedErrorType,
            String expectedErrorCode,
            List<String> expectedTargets,
            String expectedRequestId) {
        assertEquals(expectedHttpStatus, response.getStatusCode());
        assertEquals(expectedRequestId, response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));

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
        assertEquals("message for " + expectedErrorCode, error.message());
        assertEquals(expectedTargets, error.targets());
        assertEquals("ERROR", error.severity());
        assertEquals("SERVER", error.source());
    }

    @Test
    void responseBodyIsConcreteMutationResponse() {
        ResponseEntity<MutationResponse<Void>> response = handler.handleUnexpectedException(
                new IllegalStateException("unexpected"),
                exchangeWithRequestId("REQ-CONCRETE"));

        assertInstanceOf(MutationResponse.class, response.getBody());
    }

    private static WebExchangeBindException bindException(String fieldName, String defaultMessage) throws Exception {
        Method method = ApiExceptionHandlerTest.class.getDeclaredMethod(
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
    private static CurrentPortalAccessContextProvider currentPortalAccessContextProvider() {
        return new CurrentPortalAccessContextProvider() {
            @Override
            public Mono<PortalAccessContext> current() {
                return Mono.empty();
            }

            @Override
            public Mono<PortalAccessContext> currentOrEmpty() {
                return Mono.empty();
            }
        };
    }

}

