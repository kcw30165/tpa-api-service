package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * Builds and emits the single {@code http.api.error} log event for a handled exception.
 *
 * <p>Sub-task 1.3: this is a verbatim move of {@code ApiExceptionHandler.logException(...)}. The
 * event shape, field order, sanitization, and the ERROR/WARN + with/without-stack-trace branching
 * are unchanged. The caller's own SLF4J {@link Logger} is passed in (rather than this class owning
 * a logger) so the emitted events keep the exact logger category they have today (for example
 * {@code ApiExceptionHandler}), which is what the sub-task 1.1 characterization tests (Logback
 * {@code ListAppender} attached to that category) assert against.
 */
public final class ErrorEventLogger {

  private ErrorEventLogger() {}

  public static void log(
      Logger logger,
      LoggingSanitizer loggingSanitizer,
      HttpStatusCode status,
      String errorCode,
      ServerWebExchange exchange,
      Throwable exception,
      String diagnosticMessage,
      boolean logAtError,
      boolean includeStackTrace) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("event", "http.api.error");
    String requestId =
        (String) exchange.getAttributes().get(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
    if (requestId != null) {
      event.put("requestId", requestId);
    }
    if (exchange.getRequest().getMethod() != null) {
      event.put("httpMethod", exchange.getRequest().getMethod().name());
    }
    event.put("path", exchange.getRequest().getPath().value());
    event.put("httpStatus", status.value());
    event.put("errorCode", errorCode);
    event.put("exceptionType", exception.getClass().getSimpleName());
    String sanitizedMessage =
        loggingSanitizer.sanitizeText(firstNonBlank(diagnosticMessage, exception.getMessage()));
    if (StringUtils.hasText(sanitizedMessage)) {
      event.put("sanitizedMessage", sanitizedMessage);
    }
    String payload = loggingSanitizer.toSafeString(event);
    if (logAtError) {
      if (includeStackTrace) {
        logger.error("{}", payload, exception);
      } else {
        logger.error("{}", payload);
      }
      return;
    }
    if (includeStackTrace) {
      logger.warn("{}", payload, exception);
      return;
    }
    logger.warn("{}", payload);
  }

  private static String firstNonBlank(String first, String second) {
    return StringUtils.hasText(first) ? first : second;
  }
}
