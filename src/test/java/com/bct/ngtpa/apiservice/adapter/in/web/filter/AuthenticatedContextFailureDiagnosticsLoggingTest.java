package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pins the reason-coded, secret-free diagnostics emitted when authenticated portal context
 * resolution fails in the web layer. Logging only: behaviour and HTTP mapping are asserted by the
 * pre-existing filter / handler tests.
 *
 * <p>Sub-task 1.8.3 (M1 closure, step 3 of 3): the {@code
 * AuthenticatedPortalContextWebExceptionHandler} constructor gained a 3rd parameter, {@link
 * ErrorResponseRenderer}, now injected as a bean. No assertion changed.
 */
class AuthenticatedContextFailureDiagnosticsLoggingTest {
  private static final String SID = "sid_abcdefghijklmnopqrstuvwx";
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String CSRF_VALUE = "csrf_value_secret";
  private static final String REQUEST_ID = "req-42";

  private final ListAppender<ILoggingEvent> filterLogs = new ListAppender<>();
  private final ListAppender<ILoggingEvent> handlerLogs = new ListAppender<>();
  private Logger filterLogger;
  private Logger handlerLogger;

  @BeforeEach
  void attachAppenders() {
    filterLogger = (Logger) LoggerFactory.getLogger(PortalAccessContextWebFilter.class);
    handlerLogger =
        (Logger) LoggerFactory.getLogger(AuthenticatedPortalContextWebExceptionHandler.class);
    filterLogs.start();
    handlerLogs.start();
    filterLogger.addAppender(filterLogs);
    handlerLogger.addAppender(handlerLogs);
  }

  @AfterEach
  void detachAppenders() {
    filterLogger.detachAppender(filterLogs);
    handlerLogger.detachAppender(handlerLogs);
    filterLogs.stop();
    handlerLogs.stop();
  }

  @Test
  void missingCsrfHeaderLogsReasonAndPresenceFlagsOnly() {
    runFilter(SID, null);

    assertThat(messages(filterLogs))
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains(
                        "reason=MISSING_CSRF_HEADER",
                        "sessionCookiePresent=true",
                        "csrfHeaderPresent=false",
                        "requestId=" + REQUEST_ID)
                    .doesNotContain(SID, ACCOUNT_REF, CSRF_VALUE));
  }

  @Test
  void missingSessionCookieLogsReason() {
    runFilter(null, CSRF_VALUE);

    assertThat(messages(filterLogs))
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains(
                        "reason=MISSING_SESSION_COOKIE",
                        "sessionCookiePresent=false",
                        "csrfHeaderPresent=true")
                    .doesNotContain(ACCOUNT_REF, CSRF_VALUE));
  }

  @Test
  void malformedSessionIdLogsReasonWithoutEchoingTheValue() {
    runFilter("not-a-valid-session-id", CSRF_VALUE);

    assertThat(messages(filterLogs))
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains("reason=MALFORMED_SESSION_ID", "requestId=" + REQUEST_ID)
                    .doesNotContain("not-a-valid-session-id", CSRF_VALUE, ACCOUNT_REF));
  }

  @Test
  void exceptionHandlerLogsTheStatusActuallyReturned() {
    var request = MockServerHttpRequest.get("/api/v1/fund-price");
    var exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, REQUEST_ID);
    var handler =
        new AuthenticatedPortalContextWebExceptionHandler(
            new ObjectMapper(),
            new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
            new ErrorResponseRenderer());

    StepVerifier.create(
            handler.handle(exchange, new AuthenticatedPortalContextResolutionException()))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    assertThat(messages(handlerLogs))
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains(
                        "event=authenticated.context.failure.mapped",
                        "status=401",
                        "path=/api/v1/fund-price",
                        "requestId=" + REQUEST_ID));
  }

  private void runFilter(String sessionId, String csrf) {
    var filter =
        new PortalAccessContextWebFilter(
            ref -> Mono.error(new AssertionError("must not resolve")),
            command -> Mono.error(new AssertionError("must not validate")),
            new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie()));
    var request = MockServerHttpRequest.get("/api/v1/fund-price");
    request.header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF);
    if (sessionId != null) request.cookie(new HttpCookie("portal_sid", sessionId));
    if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
    var exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, REQUEST_ID);

    StepVerifier.create(
            filter.filter(
                exchange, current -> Mono.error(new AssertionError("chain must not run"))))
        .expectError(AuthenticatedPortalContextResolutionException.class)
        .verify();
  }

  private static List<String> messages(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
