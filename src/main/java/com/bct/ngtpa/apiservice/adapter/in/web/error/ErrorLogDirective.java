package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.Objects;
import org.springframework.http.HttpStatusCode;

/**
 * SonarQube fix (java:S107): groups the four primitive values {@link ErrorEventLogger#log} used to
 * accept as separate parameters -- status, errorCode, logAtError, includeStackTrace -- into a
 * single parameter object. This brings {@code log(...)} from 9 parameters down to 6. Pure data
 * carrier; no behaviour change versus the previous 9-parameter signature.
 */
public record ErrorLogDirective(
    HttpStatusCode status, String errorCode, boolean logAtError, boolean includeStackTrace) {

  public ErrorLogDirective {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(errorCode, "errorCode");
  }
}
