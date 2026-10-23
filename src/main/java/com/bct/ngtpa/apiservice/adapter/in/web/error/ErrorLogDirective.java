package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;

/**
 * Groups the values {@link ErrorEventLogger#log} needs beyond the logger/exchange/exception/
 * diagnostic-message arguments: {@code status}, {@code errorCode}, {@code logAtError}, {@code
 * includeStackTrace}, and {@code extraFields} (merged into the single {@code http.api.error} event
 * {@link ErrorEventLogger} writes, letting a mapping contribute extra fields without logging a
 * second event). The 4-argument constructor is a convenience that defaults {@code extraFields} to
 * an empty map.
 */
public record ErrorLogDirective(
    HttpStatusCode status,
    String errorCode,
    boolean logAtError,
    boolean includeStackTrace,
    Map<String, Object> extraFields) {

  public ErrorLogDirective {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(errorCode, "errorCode");
    extraFields = extraFields == null ? Map.of() : Map.copyOf(extraFields);
  }

  public ErrorLogDirective(
      HttpStatusCode status, String errorCode, boolean logAtError, boolean includeStackTrace) {
    this(status, errorCode, logAtError, includeStackTrace, Map.of());
  }
}
