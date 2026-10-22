package com.bct.ngtpa.apiservice.adapter.in.web.error;

/**
 * The log level an {@link ErrorDescriptor} requests for the single {@code http.api.error} event
 * emitted for an unhandled/mapped exception. Mirrors the two levels used today across every {@code
 * ApiExceptionHandler} handler method (WARN for expected/business failures, ERROR for downstream
 * and system failures) per docs/design/15-exception-handling/01-current-state.md.
 */
public enum LogLevel {
  WARN,
  ERROR
}
