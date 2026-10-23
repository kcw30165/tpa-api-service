package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * Builds and emits the single {@code http.api.error} log event for a handled exception: field
 * shape, field order, sanitization, and the ERROR/WARN plus with/without-stack-trace branching. The
 * caller's own SLF4J {@link Logger} is passed in (rather than this class owning a logger) so
 * emitted events keep the exact logger category of the calling handler.
 *
 * <p>{@link LoggingSanitizer} is injected via the constructor. {@link ErrorLogDirective} carries
 * {@code status}, {@code errorCode}, {@code logAtError}, {@code includeStackTrace} and {@code
 * extraFields} as a single parameter object; {@code extraFields} entries, when present, are merged
 * into the event between {@code errorCode} and {@code exceptionType}.
 */
@Component
public final class ErrorEventLogger {

  private final LoggingSanitizer loggingSanitizer;

  @Autowired
  public ErrorEventLogger(LoggingSanitizer loggingSanitizer) {
    this.loggingSanitizer = Objects.requireNonNull(loggingSanitizer, "loggingSanitizer");
  }

  public void log(
      Logger logger,
      ErrorLogDirective directive,
      ServerWebExchange exchange,
      Throwable exception,
      String diagnosticMessage) {
    doLog(logger, loggingSanitizer, directive, exchange, exception, diagnosticMessage);
  }

  private static void doLog(
      Logger logger,
      LoggingSanitizer loggingSanitizer,
      ErrorLogDirective directive,
      ServerWebExchange exchange,
      Throwable exception,
      String diagnosticMessage) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("event", "http.api.error");
    String requestId =
        (String) exchange.getAttributes().get(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
    if (requestId != null) {
      event.put("requestId", requestId);
    }
    event.put("httpMethod", exchange.getRequest().getMethod().name());
    event.put("path", exchange.getRequest().getPath().value());
    event.put("httpStatus", directive.status().value());
    event.put("errorCode", directive.errorCode());
    event.putAll(directive.extraFields());
    event.put("exceptionType", exception.getClass().getSimpleName());
    String sanitizedMessage =
        loggingSanitizer.sanitizeText(firstNonBlank(diagnosticMessage, exception.getMessage()));
    if (StringUtils.hasText(sanitizedMessage)) {
      event.put("sanitizedMessage", sanitizedMessage);
    }
    String payload = loggingSanitizer.toSafeString(event);
    if (directive.logAtError()) {
      if (directive.includeStackTrace()) {
        logger.error("{}", payload, exception);
      } else {
        logger.error("{}", payload);
      }
      return;
    }
    if (directive.includeStackTrace()) {
      logger.warn("{}", payload, exception);
      return;
    }
    logger.warn("{}", payload);
  }

  private static String firstNonBlank(String first, String second) {
    return StringUtils.hasText(first) ? first : second;
  }
}
