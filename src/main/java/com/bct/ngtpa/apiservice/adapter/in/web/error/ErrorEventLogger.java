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
 *
 * <p>Sub-task 1.7 (M1 closure, A2): {@link ErrorLogDirective#extraFields()} entries, when present,
 * are merged into this same event -- inserted after {@code errorCode} and before {@code
 * exceptionType}, matching the field order the pre-1.7 separate {@code
 * account.balance.normalization.failed} event used -- instead of {@code
 * AccountBalanceExceptionMappings} needing to log a second event itself. An empty {@code
 * extraFields} map (every caller before this sub-task) adds nothing, so every other mapping's log
 * event is byte-for-byte unchanged.
 *
 * <p>Sub-task 1.8.2 (M1 closure, step 2 of 3): this class is now also a Spring {@code @Component}
 * bean, with {@link LoggingSanitizer} injected via its constructor instead of passed as a parameter
 * on every call. The new 5-parameter instance method {@link #log(Logger, ErrorLogDirective,
 * ServerWebExchange, Throwable, String)} differs in arity from the unchanged 6-parameter static
 * method (no {@code loggingSanitizer} argument, since it is now a field), so both coexist without a
 * signature clash. Both delegate to the same private {@link #doLog} so event shape, field order and
 * sanitization stay identical between the two call styles.
 *
 * <p>The instance method is NOT YET USED by any production caller in this sub-task: {@code
 * ApiExceptionHandler} still calls the unchanged static {@link #log(Logger, LoggingSanitizer,
 * ErrorLogDirective, ServerWebExchange, Throwable, String)} method today, so none of its ~20
 * constructor callers need to change in this step. Sub-task 1.8.3 switches {@code
 * ApiExceptionHandler} to the instance method (injecting this bean via its own, by-then-rewritten
 * constructor) at the same time it performs the A1 method collapse, and removes the static method
 * and its dedicated tests once the instance method is the only remaining call path.
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

  public static void log(
      Logger logger,
      LoggingSanitizer loggingSanitizer,
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
