package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;

/**
 * The complete, exception-agnostic description of how one failure must be rendered and logged:
 * every column of the sub-task 1.0 mapping table (HTTP status, {@link ApiStatus}, error type, error
 * code, targets, log event name, {@link LogLevel}, and whether a stack trace is attached).
 *
 * <p>An {@link ExceptionMapping} produces one of these per exception instance; the error response
 * renderer and error event logger introduced in sub-task 1.3 will consume it. Not wired into any
 * handler yet (sub-task 1.2).
 *
 * <p>{@code httpStatus} is an {@link HttpStatusCode} rather than the {@code HttpStatus} enum
 * because some current mappings (for example {@code ApimException}, and the {@code
 * AccountBalanceRetrievalResponsePolicy} / {@code AccountGainLossRetrievalResponsePolicy} policies)
 * resolve their status from the downstream exception rather than from a fixed constant.
 */
public record ErrorDescriptor(
    HttpStatusCode httpStatus,
    ApiStatus apiStatus,
    String errorType,
    String errorCode,
    List<String> targets,
    String logEventName,
    LogLevel logLevel,
    boolean includeStackTrace) {

  public ErrorDescriptor {
    Objects.requireNonNull(httpStatus, "httpStatus");
    Objects.requireNonNull(apiStatus, "apiStatus");
    Objects.requireNonNull(errorType, "errorType");
    Objects.requireNonNull(errorCode, "errorCode");
    Objects.requireNonNull(logEventName, "logEventName");
    Objects.requireNonNull(logLevel, "logLevel");
    targets = targets == null ? List.of() : List.copyOf(targets);
  }
}
