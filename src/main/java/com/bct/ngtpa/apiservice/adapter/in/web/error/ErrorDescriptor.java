package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;

/**
 * The complete, exception-agnostic description of how one failure must be rendered and logged: HTTP
 * status, {@link ApiStatus}, one or more {@link ErrorEntry} values, log event name, {@link
 * LogLevel}, whether a stack trace is attached, and {@code extraLogFields}.
 *
 * <p>An {@link ExceptionMapping} produces one of these per exception instance; {@link
 * ErrorResponseRenderer} and {@link ErrorEventLogger} consume it.
 *
 * <p>{@code errors} is a {@code List<ErrorEntry>} so a single descriptor can express either exactly
 * one public error or several (one {@link ErrorEntry} per field error for {@code
 * WebExchangeBindException}). Each {@link ErrorEntry} carries its own {@link MessagePolicy},
 * letting message resolution be skipped per entry instead of per exception type -- the contract
 * {@code AccountBalanceRetrievalException}, {@code AccountGainLossRetrievalException} and {@code
 * FundFactSheetRetrievalException} rely on for their null-message mappings (O-12,
 * docs/design/15-exception-handling/06-open-items.md). {@link #single} and {@link
 * #singleWithoutMessage} are convenience factories for the common one-entry case.
 *
 * <p>{@code extraLogFields} lets a mapping contribute extra, value-free key/value pairs that {@link
 * ErrorEventLogger} merges into the single {@code http.api.error} event it writes, instead of that
 * mapping needing to log a second event itself.
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
   * Convenience factory for the common one-entry case: one {@link MessagePolicy#RESOLVE} entry, no
   * extra log fields.
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
   * {@code null} (O-12, docs/design/15-exception-handling/06-open-items.md).
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
