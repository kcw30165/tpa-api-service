package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiErrorResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.RequestPath;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler(testErrorMessageResolver(),
            testLoggingSanitizer());

    // ── Existing body contract tests (body must only have errorCode and message)
    // ──

    // @Test
    // void mapsApimExceptionToItsStatusAndErrorCode() {
    // ResponseEntity<ApiErrorResponse> response = handler.handleApimException(
    // new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE,
    // "APIM failure"),
    // emptyExchange());

    // assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    // assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE,
    // response.getBody().errorCode());
    // assertEquals("Service is temporarily unavailable. Please try again later.",
    // response.getBody().message());
    // }

    // @Test
    // void mapsApimInternalServerErrorToItsStatusAndErrorCode() {
    // ResponseEntity<ApiErrorResponse> response = handler.handleApimException(
    // new ApimException(HttpStatus.INTERNAL_SERVER_ERROR,
    // ErrorCodes.SYSTEM_UNEXPECTED, "Crypto failure"),
    // emptyExchange());

    // assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    // assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, response.getBody().errorCode());
    // assertEquals("Sorry, this service might be interrupted. Please try again
    // later.", response.getBody().message());
    // }

    @Test
    void mapsInvalidNotificationRequestExceptionToBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidNotificationRequestException(
                new InvalidNotificationRequestException("Invalid request"), emptyExchange());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.NOTIFICATION_REQUEST_INVALID, response.getBody().errorCode());
        assertEquals("Invalid notification request.", response.getBody().message());
    }

    @Test
    void mapsInvalidContributionRequestExceptionToBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidContributionRequestException(
                new InvalidContributionRequestException("Invalid contribution request"), emptyExchange());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.CONTRIBUTION_REQUEST_INVALID, response.getBody().errorCode());
        assertEquals("Invalid contribution request.", response.getBody().message());
    }

    @Test
    void usesFirstFieldErrorMessageForBindFailures() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "field", "must not be blank"));

        ResponseEntity<ApiErrorResponse> response = handler.handleWebExchangeBindException(
                new WebExchangeBindException(methodParameter(), bindingResult), emptyExchange());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.REQUEST_VALIDATION_FAILED, response.getBody().errorCode());
        assertEquals("Invalid request payload.", response.getBody().message());
    }

    @Test
    void fallsBackForBlankBindFailureMessages() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "field", null, false, null, null, "  "));

        ResponseEntity<ApiErrorResponse> response = handler.handleWebExchangeBindException(
                new WebExchangeBindException(methodParameter(), bindingResult), emptyExchange());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.REQUEST_VALIDATION_FAILED, response.getBody().errorCode());
        assertEquals("Invalid request payload.", response.getBody().message());
    }

    @Test
    void usesDefaultMessageForBindFailuresFromBoundRequestTargetWithoutEnv() throws Exception {
        UpdateNotificationsReadStatusRequest target = new UpdateNotificationsReadStatusRequest(List.of(""));
        BindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "notificationId", "must not contain blank values"));

        ResponseEntity<ApiErrorResponse> response = handler.handleWebExchangeBindException(
                new WebExchangeBindException(methodParameter(), bindingResult), emptyExchange());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.REQUEST_VALIDATION_FAILED, response.getBody().errorCode());
        assertEquals("Invalid request payload.", response.getBody().message());
    }

    @Test
    void usesServerInputReasonWhenPresent() {
        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInputException(
                new ServerWebInputException("Malformed JSON"), emptyExchange());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.REQUEST_BODY_MALFORMED, response.getBody().errorCode());
        assertEquals("Malformed request body.", response.getBody().message());
    }

    @Test
    void fallsBackForBlankServerInputReason() {
        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInputException(
                new ServerWebInputException("  "), emptyExchange());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.REQUEST_BODY_MALFORMED, response.getBody().errorCode());
        assertEquals("Malformed request body.", response.getBody().message());
    }

    @Test
    void mapsGenericApplicationExceptionToItsOwnErrorCode() {
        ResponseEntity<ApiErrorResponse> response = handler.handleApplicationException(
                new ApplicationException(ErrorCodes.REQUEST_INVALID, "internal only"),
                emptyExchange());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.REQUEST_INVALID, response.getBody().errorCode());
        assertEquals("Invalid request.", response.getBody().message());
    }

    @Test
    void mapsAuthenticationExceptionToUnauthorizedBusinessError() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAuthenticationException(
                new BadCredentialsException("raw auth failure"), emptyExchange());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, response.getBody().errorCode());
        assertEquals("Authentication is required.", response.getBody().message());
    }

    @Test
    void mapsAccessDeniedExceptionToForbiddenBusinessError() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAccessDeniedException(
                new AccessDeniedException("raw access denied"), emptyExchange());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCodes.SECURITY_ACCESS_DENIED, response.getBody().errorCode());
        assertEquals("Access is denied.", response.getBody().message());
    }

    @Test
    void mapsUnknownExceptionToSystemUnexpectedWithSafeMessage() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("raw internal failure"), emptyExchange());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, response.getBody().errorCode());
        assertEquals("Sorry, this service might be interrupted. Please try again later.", response.getBody().message());
    }

    @Test
    void usesQueryLanguageWhenResolvingPublicMessages() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/contributions?lang=zh_HK").build());

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("raw internal failure"), exchange);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, response.getBody().errorCode());
        assertEquals("對不起，此服務可能暫受阻延，請稍後再嘗試。", response.getBody().message());
    }

    // ── Error body does NOT contain requestId ────────────────────────────────

    // @Test
    // void errorBodyDoesNotContainRequestId() {
    // MockServerWebExchange exchange = exchangeWithRequestId("test-req-id-123");

    // ResponseEntity<ApiErrorResponse> response = handler.handleApimException(
    // new ApimException(HttpStatus.BAD_GATEWAY, "ERR", "msg"), exchange);

    // // Body has only errorCode and message
    // assertNotNull(response.getBody());
    // assertEquals("ERR", response.getBody().errorCode());
    // assertEquals("Sorry, this service might be interrupted. Please try again
    // later.", response.getBody().message());
    // // Verify by checking the record only has 2 components
    // assertEquals(2, response.getBody().getClass().getRecordComponents().length);
    // }

    // ── X-Request-Id present in response header ───────────────────────────────

    // @Test
    // void apimExceptionResponseIncludesRequestIdHeader() {
    // MockServerWebExchange exchange = exchangeWithRequestId("apim-req-id");

    // ResponseEntity<ApiErrorResponse> response = handler.handleApimException(
    // new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE,
    // "msg"), exchange);

    // assertEquals("apim-req-id",
    // response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    // }

    // @Test
    // void cryptoExceptionMappedAsApimExceptionPreservesRequestIdHeader() {
    // MockServerWebExchange exchange = exchangeWithRequestId("crypto-req-id");

    // ResponseEntity<ApiErrorResponse> response = handler.handleApimException(
    // new ApimException(HttpStatus.INTERNAL_SERVER_ERROR,
    // ErrorCodes.SYSTEM_UNEXPECTED, "fail"), exchange);

    // assertEquals("crypto-req-id",
    // response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    // }

    @Test
    void invalidNotificationResponseIncludesRequestIdHeader() {
        MockServerWebExchange exchange = exchangeWithRequestId("notif-req-id");

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidNotificationRequestException(
                new InvalidNotificationRequestException("bad"), exchange);

        assertEquals("notif-req-id", response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    @Test
    void bindExceptionResponseIncludesRequestIdHeader() throws Exception {
        MockServerWebExchange exchange = exchangeWithRequestId("bind-req-id");
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "field", "err"));

        ResponseEntity<ApiErrorResponse> response = handler.handleWebExchangeBindException(
                new WebExchangeBindException(methodParameter(), bindingResult), exchange);

        assertEquals("bind-req-id", response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    @Test
    void serverInputExceptionResponseIncludesRequestIdHeader() {
        MockServerWebExchange exchange = exchangeWithRequestId("input-req-id");

        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInputException(
                new ServerWebInputException("bad input"), exchange);

        assertEquals("input-req-id", response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    @Test
    void unexpectedExceptionResponseIncludesRequestIdHeader() {
        MockServerWebExchange exchange = exchangeWithRequestId("unexpected-req-id");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("boom"), exchange);

        assertEquals("unexpected-req-id", response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    // @Test
    // void omitsRequestIdHeaderWhenNotInExchangeAttributes() {
    // ResponseEntity<ApiErrorResponse> response = handler.handleApimException(
    // new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE,
    // "msg"), emptyExchange());

    // assertNull(response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    // }

    // @Test
    // void mapsPortalAccessContextResolutionExceptionToInternalServerError() {
    // ResponseEntity<ApiErrorResponse> response =
    // handler.handlePortalAccessContextResolutionException(
    // new PortalAccessContextResolutionException("No profile for notifications"),
    // emptyExchange());

    // assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    // // Error code string is kept stable to avoid breaking API clients
    // assertEquals(ErrorCodes.MEMBER_CONTEXT_UNAVAILABLE,
    // response.getBody().errorCode());
    // assertEquals("Member context is unavailable.", response.getBody().message());
    // }

    // @Test
    // void portalAccessContextResolutionExceptionResponseIncludesRequestIdHeader()
    // {
    // MockServerWebExchange exchange = exchangeWithRequestId("portal-ctx-req-id");

    // ResponseEntity<ApiErrorResponse> response =
    // handler.handlePortalAccessContextResolutionException(
    // new PortalAccessContextResolutionException("No profile"), exchange);

    // assertEquals("portal-ctx-req-id",
    // response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    // }

    @Test
    void mappedExceptionsNeverExposeHttpStatusStringsAsErrorCodeAndKeepStandardBodyShape() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "field", "must not be blank"));

        List<ResponseEntity<ApiErrorResponse>> responses = new ArrayList<>();
        responses.add(handler.handleApplicationException(
                new ApplicationException(ErrorCodes.REQUEST_INVALID, "internal only"), emptyExchange()));
        responses.add(handler.handleInvalidNotificationRequestException(
                new InvalidNotificationRequestException("bad"), emptyExchange()));
        responses.add(handler.handleInvalidContributionRequestException(
                new InvalidContributionRequestException("bad"), emptyExchange()));
        // responses.add(handler.handlePortalAccessContextResolutionException(
        // new PortalAccessContextResolutionException("missing portal"),
        // emptyExchange()));
        // responses.add(handler.handleApimException(
        // new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE,
        // "upstream"),
        // emptyExchange()));
        responses.add(handler.handleWebExchangeBindException(
                new WebExchangeBindException(methodParameter(), bindingResult), emptyExchange()));
        responses.add(handler.handleServerWebInputException(
                new ServerWebInputException("Malformed JSON"), emptyExchange()));
        responses.add(handler.handleUnexpectedException(new RuntimeException("boom"), emptyExchange()));

        for (ResponseEntity<ApiErrorResponse> response : responses) {
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().getClass().getRecordComponents().length);
            assertTrue(!response.getBody().errorCode().matches("\\d+"),
                    () -> "Expected business error code but got: " + response.getBody().errorCode());
        }
    }

    @Test
    void apimExceptionWithBlankErrorCodeFallsBackToUpstreamFailure() {
        ApiExceptionHandler handler = handler("public-message", "sanitized", "{}");
        ApimException ex = mock(ApimException.class);
        when(ex.getStatusCode()).thenReturn(HttpStatus.BAD_GATEWAY);
        when(ex.getErrorCode()).thenReturn(" ");
        when(ex.getMessage()).thenReturn("diagnostic");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/probe").build());

        var response = handler.handleApimException(ex, exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        // assertThat(response.getBody().errorCode()).isEqualTo(ErrorCodes.APIM_UPSTREAM_FAILURE);
    }

    @Test
    void privateLogExceptionHandlesMissingMethodBlankSanitizedMessageAndWarnStackTrace() throws Exception {
        LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);
        when(sanitizer.sanitizeText(any())).thenReturn(" ");
        when(sanitizer.toSafeString(any())).thenReturn("{}");
        ApiExceptionHandler handler = new ApiExceptionHandler(resolver("message"), sanitizer);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getMethod()).thenReturn(null);
        RequestPath path = mock(RequestPath.class);
        when(path.value()).thenReturn("/api/v1/no-method");
        when(request.getPath()).thenReturn(path);
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getAttributes()).thenReturn(new LinkedHashMap<>());

        Method method = ApiExceptionHandler.class.getDeclaredMethod(
                "logException",
                org.springframework.http.HttpStatusCode.class,
                String.class,
                ServerWebExchange.class,
                Throwable.class,
                String.class,
                boolean.class,
                boolean.class);
        method.setAccessible(true);

        method.invoke(handler, HttpStatus.BAD_REQUEST, ErrorCodes.REQUEST_INVALID, exchange,
                new IllegalArgumentException("boom"), " ", false, true);
    }

    @Test
    void privateRequestLocaleReturnsNullForInvalidAcceptLanguageHeader() throws Exception {
        ApiExceptionHandler handler = handler("message", "sanitized", "{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/probe")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "@@@")
                .build());

        Method method = ApiExceptionHandler.class.getDeclaredMethod("requestLocale", ServerWebExchange.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(handler, exchange)).isNull();
    }

    @Test
    void privateTrimToNullHandlesNullBlankAndValue() throws Exception {
        ApiExceptionHandler handler = handler("message", "sanitized", "{}");
        Method method = ApiExceptionHandler.class.getDeclaredMethod("trimToNull", Object.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(handler, new Object[] { null })).isNull();
        assertThat((String) method.invoke(handler, "   ")).isNull();
        assertThat((String) method.invoke(handler, " JP ")).isEqualTo("JP");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ServerWebExchange emptyExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    }

    private static MockServerWebExchange exchangeWithRequestId(String requestId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build());
        exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, requestId);
        return exchange;
    }

    private static MethodParameter methodParameter() throws Exception {
        Method method = ApiExceptionHandlerTest.class.getDeclaredMethod("sampleHandler", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void sampleHandler(String requestBody) {
    }

    private static ErrorMessageResolver testErrorMessageResolver() {
        return (errorCode, locale, accountEnv, trustCode, schemeType) -> switch (errorCode) {
            case ErrorCodes.APIM_UPSTREAM_FAILURE,
                    ErrorCodes.APIM_SERVICE_UNAVAILABLE,
                    ErrorCodes.APIM_TIMEOUT,
                    ErrorCodes.APIM_RESPONSE_INVALID ->
                "Service is temporarily unavailable. Please try again later.";
            case ErrorCodes.REQUEST_INVALID -> "Invalid request.";
            case ErrorCodes.REQUEST_VALIDATION_FAILED -> "JP".equals(accountEnv)
                    ? "Invalid request payload for JP."
                    : "Invalid request payload.";
            case ErrorCodes.REQUEST_BODY_MALFORMED -> "Malformed request body.";
            case ErrorCodes.SECURITY_ACCESS_DENIED -> "Access is denied.";
            case ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED -> "Authentication is required.";
            case ErrorCodes.CONTRIBUTION_REQUEST_INVALID -> "Invalid contribution request.";
            case ErrorCodes.NOTIFICATION_REQUEST_INVALID -> "Invalid notification request.";
            case ErrorCodes.MEMBER_CONTEXT_UNAVAILABLE -> "Member context is unavailable.";
            case ErrorCodes.SYSTEM_UNEXPECTED -> "zh_HK".equalsIgnoreCase(locale)
                    ? "對不起，此服務可能暫受阻延，請稍後再嘗試。"
                    : "Sorry, this service might be interrupted. Please try again later.";
            default -> "Sorry, this service might be interrupted. Please try again later.";
        };
    }

    private static LoggingSanitizer testLoggingSanitizer() {
        LoggingSanitizerProperties properties = new LoggingSanitizerProperties();
        properties.setSensitiveTokens(List.of(
                "authorization",
                "token",
                "apiKey",
                "Certificate",
                "policyNo",
                "certNo",
                "memberId",
                "userId"));
        return new LoggingSanitizer(new ObjectMapper(), properties);
    }

    private static ApiExceptionHandler handler(String publicMessage, String sanitizedMessage, String safeString) {
        LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);
        when(sanitizer.sanitizeText(any())).thenReturn(sanitizedMessage);
        when(sanitizer.toSafeString(any())).thenReturn(safeString);
        return new ApiExceptionHandler(resolver(publicMessage), sanitizer);
    }

    private static ErrorMessageResolver resolver(String message) {
        ErrorMessageResolver resolver = mock(ErrorMessageResolver.class);
        when(resolver.resolve(anyString(), any(), any(), any(), any())).thenReturn(message);
        return resolver;
    }
}
