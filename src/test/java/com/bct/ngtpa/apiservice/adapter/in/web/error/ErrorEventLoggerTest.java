package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * Verifies the {@code http.api.error} log event built by {@link ErrorEventLogger#log}: field shape
 * and order, the ERROR/WARN and with/without-stack-trace branching, blank-message handling, and
 * {@link ErrorLogDirective}'s extra-fields merge (both its 4-argument and 5-argument constructors).
 * {@link ErrorEventLogger}'s static {@code log} method was removed in sub-task 1.11; every test
 * below uses the {@code eventLogger} bean created in {@link #setUp()}.
 */
class ErrorEventLoggerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  // Logs under an arbitrary category; verbatim-move tests only need to prove field shape and
  // level/stack-trace branching, not which category a given production caller passes in. The
  // characterization pack (sub-task 1.1) pins the real ApiExceptionHandler category end to end.
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;
  private LoggingSanitizer loggingSanitizer;
  private ErrorEventLogger eventLogger;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(ErrorEventLoggerTest.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    MaskingProperties properties = new MaskingProperties();
    loggingSanitizer = new LoggingSanitizer(OBJECT_MAPPER, new MaskingService(properties));
    eventLogger = new ErrorEventLogger(loggingSanitizer);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
    listAppender.stop();
  }

  private MockServerWebExchange exchangeWithRequestId(String requestId) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, requestId);
    return exchange;
  }

  private JsonNode parse(ILoggingEvent event) {
    try {
      return OBJECT_MAPPER.readTree(event.getFormattedMessage());
    } catch (Exception e) {
      throw new AssertionError(
          "Expected valid JSON log payload but got: " + event.getFormattedMessage(), e);
    }
  }

  @Test
  void logsAtErrorWithoutStackTraceWhenLogAtErrorTrueAndIncludeStackTraceFalse() {
    eventLogger.log(
        logger,
        new ErrorLogDirective(
            HttpStatus.SERVICE_UNAVAILABLE, "err.system.unavailable", true, false),
        exchangeWithRequestId("req-1"),
        new IllegalStateException("boom"),
        "diagnostic message");

    assertEquals(1, listAppender.list.size());
    ILoggingEvent event = listAppender.list.getFirst();
    assertEquals(Level.ERROR, event.getLevel());
    assertNull(event.getThrowableProxy());

    JsonNode payload = parse(event);
    assertEquals("http.api.error", payload.get("event").asText());
    assertEquals("req-1", payload.get("requestId").asText());
    assertEquals("GET", payload.get("httpMethod").asText());
    assertEquals("/api/v1/fund-price", payload.get("path").asText());
    assertEquals(503, payload.get("httpStatus").asInt());
    assertEquals("err.system.unavailable", payload.get("errorCode").asText());
    assertEquals("IllegalStateException", payload.get("exceptionType").asText());
    assertEquals("diagnostic message", payload.get("sanitizedMessage").asText());
  }

  @Test
  void logsAtErrorWithStackTraceWhenIncludeStackTraceTrue() {
    var exception = new IllegalStateException("unexpected");

    eventLogger.log(
        logger,
        new ErrorLogDirective(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM_UNEXPECTED", true, true),
        exchangeWithRequestId("req-2"),
        exception,
        exception.getMessage());

    assertEquals(1, listAppender.list.size());
    ILoggingEvent event = listAppender.list.getFirst();
    assertEquals(Level.ERROR, event.getLevel());
    assertEquals(IllegalStateException.class.getName(), event.getThrowableProxy().getClassName());
  }

  @Test
  void logsAtWarnWithoutStackTraceWhenLogAtErrorFalseAndIncludeStackTraceFalse() {
    eventLogger.log(
        logger,
        new ErrorLogDirective(
            HttpStatus.BAD_REQUEST, "err.request.validation.failed", false, false),
        exchangeWithRequestId("req-3"),
        new IllegalArgumentException("invalid"),
        "invalid");

    assertEquals(1, listAppender.list.size());
    ILoggingEvent event = listAppender.list.getFirst();
    assertEquals(Level.WARN, event.getLevel());
    assertNull(event.getThrowableProxy());
  }

  @Test
  void logsAtWarnWithStackTraceWhenLogAtErrorFalseAndIncludeStackTraceTrue() {
    var exception = new IllegalArgumentException("invalid, with trace");

    eventLogger.log(
        logger,
        new ErrorLogDirective(HttpStatus.BAD_REQUEST, "err.request.validation.failed", false, true),
        exchangeWithRequestId("req-4"),
        exception,
        exception.getMessage());

    assertEquals(1, listAppender.list.size());
    ILoggingEvent event = listAppender.list.getFirst();
    assertEquals(Level.WARN, event.getLevel());
    assertEquals(
        IllegalArgumentException.class.getName(), event.getThrowableProxy().getClassName());
  }

  @Test
  void omitsRequestIdFieldWhenAttributeAbsent() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price"));

    eventLogger.log(
        logger,
        new ErrorLogDirective(HttpStatus.BAD_REQUEST, "err.code", false, false),
        exchange,
        new IllegalStateException("boom"),
        "diagnostic");

    JsonNode payload = parse(listAppender.list.getFirst());
    assertFalse(payload.has("requestId"));
  }

  @Test
  void fallsBackToExceptionMessageWhenDiagnosticMessageIsBlank() {
    eventLogger.log(
        logger,
        new ErrorLogDirective(HttpStatus.BAD_REQUEST, "err.code", false, false),
        exchangeWithRequestId("req-5"),
        new IllegalStateException("fallback message"),
        "   ");

    JsonNode payload = parse(listAppender.list.getFirst());
    assertEquals("fallback message", payload.get("sanitizedMessage").asText());
  }

  @Test
  void omitsSanitizedMessageFieldWhenBothDiagnosticAndExceptionMessageAreBlank() {
    eventLogger.log(
        logger,
        new ErrorLogDirective(HttpStatus.BAD_REQUEST, "err.code", false, false),
        exchangeWithRequestId("req-6"),
        new IllegalStateException((String) null),
        null);

    JsonNode payload = parse(listAppender.list.getFirst());
    assertNull(payload.get("sanitizedMessage"));
  }

  // ───────────────────────── Sub-task 1.7 (A2): extraFields merge (static call style)
  // ─────────────────────────

  @Test
  void mergesExtraFieldsIntoTheSameEventInInsertionOrder() {
    Map<String, Object> extraFields = new LinkedHashMap<>();
    extraFields.put("collection", "fundDtl");
    extraFields.put("rowIndex", 3);
    extraFields.put("field", "fund-code");
    extraFields.put("reason", "EMPTY");

    eventLogger.log(
        logger,
        new ErrorLogDirective(
            HttpStatus.SERVICE_UNAVAILABLE, "err.system.unavailable", true, false, extraFields),
        exchangeWithRequestId("req-7"),
        new IllegalStateException("Account balance retrieval failed: SYSTEM_FAILURE"),
        "Account balance retrieval failed: SYSTEM_FAILURE");

    assertEquals(1, listAppender.list.size());
    JsonNode payload = parse(listAppender.list.getFirst());
    assertEquals("http.api.error", payload.get("event").asText());
    assertEquals("err.system.unavailable", payload.get("errorCode").asText());
    assertEquals("fundDtl", payload.get("collection").asText());
    assertEquals(3, payload.get("rowIndex").asInt());
    assertEquals("fund-code", payload.get("field").asText());
    assertEquals("EMPTY", payload.get("reason").asText());
  }

  @Test
  void defaultFourArgConstructorStillProducesNoExtraFields() {
    eventLogger.log(
        logger,
        new ErrorLogDirective(HttpStatus.BAD_REQUEST, "err.code", false, false),
        exchangeWithRequestId("req-8"),
        new IllegalStateException("boom"),
        "diagnostic");

    JsonNode payload = parse(listAppender.list.getFirst());
    assertFalse(payload.has("collection"));
    assertFalse(payload.has("rowIndex"));
    assertFalse(payload.has("field"));
    assertFalse(payload.has("reason"));
  }

  // ───────────────────────── Sub-task 1.8.2: instance method (bean) call style
  // ─────────────────────────

  @Test
  void constructorRejectsNullLoggingSanitizer() {
    assertThrows(NullPointerException.class, () -> new ErrorEventLogger(null));
  }

  @Test
  void instanceLogProducesTheIdenticalEventAsTheStaticCallStyle() {
    var bean = new ErrorEventLogger(loggingSanitizer);

    bean.log(
        logger,
        new ErrorLogDirective(
            HttpStatus.SERVICE_UNAVAILABLE, "err.system.unavailable", true, false),
        exchangeWithRequestId("req-9"),
        new IllegalStateException("boom"),
        "diagnostic message");

    assertEquals(1, listAppender.list.size());
    ILoggingEvent event = listAppender.list.getFirst();
    assertEquals(Level.ERROR, event.getLevel());
    assertNull(event.getThrowableProxy());

    JsonNode payload = parse(event);
    assertEquals("http.api.error", payload.get("event").asText());
    assertEquals("req-9", payload.get("requestId").asText());
    assertEquals("GET", payload.get("httpMethod").asText());
    assertEquals("/api/v1/fund-price", payload.get("path").asText());
    assertEquals(503, payload.get("httpStatus").asInt());
    assertEquals("err.system.unavailable", payload.get("errorCode").asText());
    assertEquals("IllegalStateException", payload.get("exceptionType").asText());
    assertEquals("diagnostic message", payload.get("sanitizedMessage").asText());
  }

  @Test
  void instanceLogAtWarnWithStackTraceMatchesTheStaticCallStyle() {
    var bean = new ErrorEventLogger(loggingSanitizer);
    var exception = new IllegalArgumentException("invalid, with trace");

    bean.log(
        logger,
        new ErrorLogDirective(HttpStatus.BAD_REQUEST, "err.request.validation.failed", false, true),
        exchangeWithRequestId("req-10"),
        exception,
        exception.getMessage());

    assertEquals(1, listAppender.list.size());
    ILoggingEvent event = listAppender.list.getFirst();
    assertEquals(Level.WARN, event.getLevel());
    assertEquals(
        IllegalArgumentException.class.getName(), event.getThrowableProxy().getClassName());
  }

  @Test
  void instanceLogMergesExtraFieldsIntoTheSameEventInInsertionOrder() {
    var bean = new ErrorEventLogger(loggingSanitizer);
    Map<String, Object> extraFields = new LinkedHashMap<>();
    extraFields.put("collection", "fundDtl");
    extraFields.put("rowIndex", 3);
    extraFields.put("field", "fund-code");
    extraFields.put("reason", "EMPTY");

    bean.log(
        logger,
        new ErrorLogDirective(
            HttpStatus.SERVICE_UNAVAILABLE, "err.system.unavailable", true, false, extraFields),
        exchangeWithRequestId("req-11"),
        new IllegalStateException("Account balance retrieval failed: SYSTEM_FAILURE"),
        "Account balance retrieval failed: SYSTEM_FAILURE");

    JsonNode payload = parse(listAppender.list.getFirst());
    assertEquals("fundDtl", payload.get("collection").asText());
    assertEquals(3, payload.get("rowIndex").asInt());
    assertEquals("fund-code", payload.get("field").asText());
    assertEquals("EMPTY", payload.get("reason").asText());
  }

  @Test
  void instanceLogOmitsRequestIdFieldWhenAttributeAbsent() {
    var bean = new ErrorEventLogger(loggingSanitizer);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price"));

    bean.log(
        logger,
        new ErrorLogDirective(HttpStatus.BAD_REQUEST, "err.code", false, false),
        exchange,
        new IllegalStateException("boom"),
        "diagnostic");

    JsonNode payload = parse(listAppender.list.getFirst());
    assertFalse(payload.has("requestId"));
  }
}
