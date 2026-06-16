package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ServerWebInputException;

class ApiExceptionHandlerLoggingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;
    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        handler = new ApiExceptionHandler(
                testErrorMessageResolver(),
                testLoggingSanitizer(),
                currentPortalAccessContextProvider()
        );
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void logsHandledBusinessExceptionAtWarnWithErrorCodeAndRequestId() throws Exception {
        handler.handleInvalidNotificationRequestException(
                new InvalidNotificationRequestException("policyNo=P001 invalid request"),
                exchangeWithRequestId("business-req-id"));

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        JsonNode payload = parse(event);
        assertEquals("http.api.error", payload.get("event").asText());
        assertEquals("business-req-id", payload.get("requestId").asText());
        assertEquals(ErrorCodes.NOTIFICATION_REQUEST_INVALID, payload.get("errorCode").asText());
        assertEquals("InvalidNotificationRequestException", payload.get("exceptionType").asText());
        assertEquals("policyNo=*** invalid request", payload.get("sanitizedMessage").asText());
        assertTrue(event.getThrowableProxy() == null, "Expected no stack trace for handled business exception");
    }

    @Test
    void logsValidationErrorsAtWarn() throws Exception {
        handler.handleServerWebInputException(
                new ServerWebInputException("policyNo=P001 is malformed"),
                exchangeWithRequestId("validation-req-id"));

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        JsonNode payload = parse(event);
        assertEquals(ErrorCodes.REQUEST_BODY_MALFORMED, payload.get("errorCode").asText());
        assertEquals("policyNo=*** is malformed", payload.get("sanitizedMessage").asText());
        assertTrue(event.getThrowableProxy() == null, "Expected no stack trace for validation exception");
    }

    // @Test
    // void logsApimExceptionsAsIntegrationFailuresAtErrorWithoutStackTrace() throws Exception {
    //     handler.handleApimException(
    //             new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE, "userId=member-1 upstream failure"),
    //             exchangeWithRequestId("apim-req-id"));

    //     ILoggingEvent event = singleEvent();
    //     assertEquals(Level.ERROR, event.getLevel());
    //     JsonNode payload = parse(event);
    //     assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE, payload.get("errorCode").asText());
    //     assertEquals("ApimException", payload.get("exceptionType").asText());
    //     assertEquals("userId=*** upstream failure", payload.get("sanitizedMessage").asText());
    //     assertTrue(event.getThrowableProxy() == null, "Expected no stack trace for APIM exception");
    // }

    @Test
    void logsAuthenticationFailuresAtWarn() throws Exception {
        handler.handleAuthenticationException(
                new BadCredentialsException("token=secret auth failed"),
                exchangeWithRequestId("auth-req-id"));

        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        JsonNode payload = parse(event);
        assertEquals(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, payload.get("errorCode").asText());
        assertEquals("token=*** auth failed", payload.get("sanitizedMessage").asText());
    }

    @Test
    void unexpectedExceptionsIncludeStackTraceOnce() throws Exception {
        handler.handleUnexpectedException(
                new IllegalStateException("apiKey=secret exploded"),
                exchangeWithRequestId("unexpected-req-id"));

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = singleEvent();
        assertEquals(Level.ERROR, event.getLevel());
        JsonNode payload = parse(event);
        assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, payload.get("errorCode").asText());
        assertEquals("apiKey=*** exploded", payload.get("sanitizedMessage").asText());
        assertNotNull(event.getThrowableProxy());
    }

    private ILoggingEvent singleEvent() {
        assertEquals(1, listAppender.list.size());
        return listAppender.list.getFirst();
    }

    private JsonNode parse(ILoggingEvent event) throws Exception {
        return OBJECT_MAPPER.readTree(event.getFormattedMessage());
    }

    private MockServerWebExchange exchangeWithRequestId(String requestId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/test").build());
        exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, requestId);
        return exchange;
    }

    private static ErrorMessageResolver testErrorMessageResolver() {
        return (errorCode, locale, accountEnv, trustCode, schemeType) -> switch (errorCode) {
            case ErrorCodes.APIM_UPSTREAM_FAILURE -> "Service is temporarily unavailable. Please try again later.";
            case ErrorCodes.NOTIFICATION_REQUEST_INVALID -> "Invalid notification request.";
            case ErrorCodes.REQUEST_BODY_MALFORMED -> "Malformed request body.";
            case ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED -> "Authentication is required.";
            case ErrorCodes.SYSTEM_UNEXPECTED -> "Sorry, this service might be interrupted. Please try again later.";
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
        return new LoggingSanitizer(OBJECT_MAPPER, properties);
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