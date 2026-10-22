package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;

/**
 * The complete, exception-agnostic description of how one failure must be rendered and logged:
 * every column of the sub-task 1.0 mapping table (HTTP status, {@link ApiStatus}, one or more
 * {@link ErrorEntry} values, log event name, {@link LogLevel}, and whether a stack trace is
 * attached).
 *
 * <p>An {@link ExceptionMapping} produces one of these per exception instance; {@link
 * ErrorResponseRenderer} and {@link ErrorEventLogger} (sub-task 1.3) consume it, via {@code
 * ApiExceptionHandler.delegate(...)}.
 *
 * <p>Sub-task 1.7 (M1 closure, Option A): {@code errorType}/{@code errorCode}/{@code targets} were
 * replaced by {@code errors}, a {@code List<ErrorEntry>}, so a single descriptor can express either
 * exactly one public error (every mapping before this sub-task, and every mapping that only ever
 * has one) or several (one {@link ErrorEntry} per field error for {@code
 * WebExchangeBindException}). Each {@link ErrorEntry} carries its own {@link MessagePolicy},
 * letting {@code ApiExceptionHandler.delegate(...)} skip message resolution per entry instead of
 * per exception type, which is what let {@code AccountBalanceRetrievalException}, {@code
 * AccountGainLossRetrievalException} and {@code FundFactSheetRetrievalException} move into the
 * registry without changing their confirmed null-message contract (O-12,
 * docs/design/15-exception-handling/06-open-items.md). {@link #single} is a convenience factory
 * reproducing the exact previous constructor shape (one {@link MessagePolicy#RESOLVE} entry), so
 * the pre-existing 17 single-error mappings change by one line each: the constructor call becomes a
 * call to {@code ErrorDescriptor.single(...)} with the same arguments, in the same order.
 *
 * <p>{@code extraLogFields} is new in this sub-task too: a mapping may contribute extra, value-free
 * key/value pairs that {@link ErrorEventLogger} merges into the single {@code http.api.error} event
 * it already writes, instead of that mapping needing to log a second event itself. This is how A2
 * (merging {@code AccountBalanceRetrievalException}'s normalization-failure diagnostics into the
 * one {@code http.api.error} event, instead of a separate {@code
 * account.balance.normalization.failed} event logged first) is implemented without {@code
 * ErrorEventLogger} needing any exception-type-specific knowledge.
 */
public record ErrorDescriptor(
    HttpStatusCode httpStatus,
    ApiStatus apiStatus,
    List<ErrorEntry> errors,
    String logEventName,
    LogLevel logLevel,
    boolean includeStackTrace,
    Map<String, Object> extraLogFields) {

  public ErrorDescriptor {
    Objects.requireNonNull(httpStatus, "httpStatus");
    Objects.requireNonNull(apiStatus, "apiStatus");
    errors = errors == null ? List.of() : List.copyOf(errors);
    Objects.requireNonNull(logEventName, "logEventName");
    Objects.requireNonNull(logLevel, "logLevel");
    extraLogFields = extraLogFields == null ? Map.of() : Map.copyOf(extraLogFields);
  }

  /**
   * Reproduces the pre-sub-task-1.7 single-error constructor shape exactly: one {@link
   * MessagePolicy#RESOLVE} entry, no extra log fields. Every mapping that was already correct
   * (every one except AccountBalance, AccountGainLoss, FundFactSheet and WebExchangeBind) changes
   * only by replacing {@code new ErrorDescriptor(...)} with {@code ErrorDescriptor.single(...)},
   * same arguments, same order.
   */
  public static ErrorDescriptor single(
      HttpStatusCode httpStatus,
      ApiStatus apiStatus,
      String errorType,
      String errorCode,
      List<String> targets,
      String logEventName,
      LogLevel logLevel,
      boolean includeStackTrace) {
    return new ErrorDescriptor(
        httpStatus,
        apiStatus,
        List.of(new ErrorEntry(errorType, errorCode, targets, MessagePolicy.RESOLVE)),
        logEventName,
        logLevel,
        includeStackTrace,
        Map.of());
  }

  /**
   * Same as {@link #single} but with {@link MessagePolicy#NONE}: used by {@code
   * AccountBalanceRetrievalException}, {@code AccountGainLossRetrievalException} and {@code
   * FundFactSheetRetrievalException}'s mappings, whose public {@code message} field is always
   * {@code null} today (confirmed by the sub-task 1.6b characterization pack).
   */
  public static ErrorDescriptor singleWithoutMessage(
      HttpStatusCode httpStatus,
      ApiStatus apiStatus,
      String errorType,
      String errorCode,
      List<String> targets,
      String logEventName,
      LogLevel logLevel,
      boolean includeStackTrace) {
    return new ErrorDescriptor(
        httpStatus,
        apiStatus,
        List.of(new ErrorEntry(errorType, errorCode, targets, MessagePolicy.NONE)),
        logEventName,
        logLevel,
        includeStackTrace,
        Map.of());
  }

  /** Same as {@link #singleWithoutMessage} but also carrying A2's merged log fields. */
  public static ErrorDescriptor singleWithoutMessage(
      HttpStatusCode httpStatus,
      ApiStatus apiStatus,
      String errorType,
      String errorCode,
      List<String> targets,
      String logEventName,
      LogLevel logLevel,
      boolean includeStackTrace,
      Map<String, Object> extraLogFields) {
    return new ErrorDescriptor(
        httpStatus,
        apiStatus,
        List.of(new ErrorEntry(errorType, errorCode, targets, MessagePolicy.NONE)),
        logEventName,
        logLevel,
        includeStackTrace,
        extraLogFields);
  }
}
