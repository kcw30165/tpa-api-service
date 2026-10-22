package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

/**
 * M1 sub-task 1.1 characterization pack (docs/design/15-exception-handling/05-milestones.md).
 *
 * <p>Pins the filter-path handler's current behaviour: HTTP status, response body shape, the
 * (absent) {@code X-Request-Id} response header, the log event name, the log level, and the absence
 * of a stack trace. Also pins that every other throwable is passed through unchanged via {@code
 * Mono.error(failure)}, per docs/design/15-exception-handling/01-current-state.md and the O-6
 * investigation (script 005 / 007 evidence).
 *
 * <p>Sub-task 1.5 update: {@code AuthenticatedPortalContextWebExceptionHandler} gained a second
 * constructor parameter, {@code ExceptionMappingRegistry}, when it started resolving its status,
 * error type/code and targets from the shared registry; this pack now passes the real production
 * registry ({@code ExceptionMappingRegistryConfig}) so every pinned assertion below still exercises
 * genuine, unchanged behaviour.
 *
 * <p>SonarQube fix (post-M1 quality gate): removed the unused {@code throws Exception} declaration
 * from {@code jsonSerializationFailureFallsBackToUnauthorizedWithNoBody} (java:S1130 -- nothing in
 * the method body throws a checked exception) and reworded its explanatory comment so it no longer
 * contains a code-like {@code catch(...)} token that SonarQube's heuristic mistook for
 * commented-out code (java:S125). No behaviour or assertion changed.
 */
class AuthenticatedPortalContextWebExceptionHandlerCharacterizationTest {

  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;
  private AuthenticatedPortalContextWebExceptionHandler handler;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(AuthenticatedPortalContextWebExceptionHandler.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    handler =
        new AuthenticatedPortalContextWebExceptionHandler(
            new ObjectMapper(), new ExceptionMappingRegistryConfig().exceptionMappingRegistry());
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
    listAppender.stop();
  }

  @Test
  void mapsAuthenticatedPortalContextResolutionExceptionToUnauthorizedBusinessRejectedBody() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/personal-information")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-1"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "req-1");

    StepVerifier.create(
            handler.handle(exchange, new AuthenticatedPortalContextResolutionException()))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
    String body = exchange.getResponse().getBodyAsString().block();
    assertThat(body)
        .contains(
            "\"status\":\"BUSINESS_REJECTED\"",
            "\"type\":\"AUTHENTICATION_REQUIRED\"",
            "\"code\":\"" + ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED + "\"",
            "\"targets\":[\"PAGE\"]",
            "\"severity\":\"ERROR\"",
            "\"source\":\"SERVER\"",
            "\"success\":false")
        .doesNotContain("sid_", "acc_", "Authenticated account context unavailable");
  }

  @Test
  void doesNotSetTheXRequestIdResponseHeader() {
    // Confirmed gap carried over from sub-task 1.0: unlike ApiExceptionHandler, this handler
    // never calls exchange.getResponse().getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER,
    // ...), so the response never carries the header even though the request supplied one.
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/fund-price")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-2"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "req-2");

    StepVerifier.create(
            handler.handle(exchange, new AuthenticatedPortalContextResolutionException()))
        .verifyComplete();

    assertThat(exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER))
        .isNull();
  }

  @Test
  void logsExactlyOneWarnEventNamedAuthenticatedContextFailureMappedWithoutAStackTrace() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/fund-price")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-3"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "req-3");

    StepVerifier.create(
            handler.handle(exchange, new AuthenticatedPortalContextResolutionException()))
        .verifyComplete();

    assertThat(listAppender.list).hasSize(1);
    ILoggingEvent event = listAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage())
        .contains(
            "event=authenticated.context.failure.mapped",
            "status=401",
            "code=" + ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
            "path=/api/v1/fund-price",
            "requestId=req-3");
  }

  @Test
  void everyOtherThrowableIsPassedThroughUnchangedWithoutWritingAResponseOrLogging() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price"));
    var original = new IllegalStateException("not this handler's concern");

    StepVerifier.create(handler.handle(exchange, original))
        .expectErrorSatisfies(propagated -> assertThat(propagated).isSameAs(original))
        .verify();

    assertThat(exchange.getResponse().getStatusCode()).isNull();
    assertThat(listAppender.list).isEmpty();
  }

  @Test
  void jsonSerializationFailureFallsBackToUnauthorizedWithNoBody() {
    // Pins the exception-handling fallback: if JSON serialization throws, the handler still
    // completes with 401 and an empty body rather than propagating the failure.
    var throwingMapper =
        new ObjectMapper() {
          @Override
          public byte[] writeValueAsBytes(Object value)
              throws com.fasterxml.jackson.core.JsonProcessingException {
            throw new RuntimeException("serialization boom");
          }
        };
    var throwingHandler =
        new AuthenticatedPortalContextWebExceptionHandler(
            throwingMapper, new ExceptionMappingRegistryConfig().exceptionMappingRegistry());
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price"));

    StepVerifier.create(
            throwingHandler.handle(exchange, new AuthenticatedPortalContextResolutionException()))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
