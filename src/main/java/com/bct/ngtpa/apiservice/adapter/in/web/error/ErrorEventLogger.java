package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
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
 *
 * <p>SonarQube fix (post-M1 quality gate): {@code status}, {@code errorCode}, {@code logAtError}
 * and {@code includeStackTrace} are now carried by a single {@link ErrorLogDirective} parameter
 * object instead of 4 separate primitives, bringing {@link #log} from 9 parameters down to 6
 * (java:S107). The redundant {@code exchange.getRequest().getMethod() != null} guard was also
 * removed (java:S2589): on the project's current spring-web version (7.0.7), {@code
 * ServerHttpRequest.getMethod()} always returns a non-null {@code HttpMethod}, so the condition
 * could never be false. Event shape, field order, and every other behaviour are unchanged.
 */
public final class ErrorEventLogger {

  private ErrorEventLogger() {}

  public static void log(
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
