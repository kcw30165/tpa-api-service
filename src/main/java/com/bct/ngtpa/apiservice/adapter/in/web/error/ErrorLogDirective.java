package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;

/**
 * SonarQube fix (java:S107): groups the four primitive values {@link ErrorEventLogger#log} used to
 * accept as separate parameters -- status, errorCode, logAtError, includeStackTrace -- into a
 * single parameter object. This brings {@code log(...)} from 9 parameters down to 6. Pure data
 * carrier; no behaviour change versus the previous 9-parameter signature.
 *
 * <p>Sub-task 1.7 (M1 closure, A2): gained a 5th component, {@code extraFields}, so a mapping's
 * {@link ErrorDescriptor#extraLogFields()} can be merged into the single {@code http.api.error}
 * event {@link ErrorEventLogger} writes, instead of that mapping logging a second event itself. The
 * pre-existing 4-argument constructor is kept (delegating to the 5-argument canonical constructor
 * with an empty map), so every call site written before this sub-task -- both production ({@code
 * ApiExceptionHandler.delegate}/{@code buildMutationErrorResponse}) and test ({@code
 * ErrorEventLoggerTest}'s 7 call sites) -- compiles and behaves unchanged.
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
