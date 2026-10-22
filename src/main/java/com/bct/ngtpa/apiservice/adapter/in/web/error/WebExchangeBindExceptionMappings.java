package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;

/**
 * Sub-task 1.7 (M1 closure, Option A): moves {@code
 * ApiExceptionHandler.handleWebExchangeBindException}'s mapping out of the handler. This is the one
 * exception type {@link ErrorDescriptor} could not previously express: it produces one {@link
 * ErrorEntry} per field error (or a single {@code FORM}-type entry when there are none), which is
 * exactly why {@link ErrorDescriptor} changed from a single type/code/targets triple to a {@code
 * List<ErrorEntry>} in this sub-task.
 *
 * <p>Every field-error entry uses {@link MessagePolicy#RESOLVE} with the same error code ({@link
 * ErrorCodes#REQUEST_VALIDATION_FAILED}); {@code ApiExceptionHandler.delegate(...)} resolves the
 * public message once per entry, and since {@code resolvePublicMessage(...)} is a pure function of
 * the error code, the exchange and the exception, every entry ends up with the identical resolved
 * message, in field order -- matching the pre-1.7 behaviour exactly (the old {@code
 * validationErrors(...)} helper resolved the message once and reused it for every field).
 */
public final class WebExchangeBindExceptionMappings {

  private WebExchangeBindExceptionMappings() {}

  public static ExceptionMapping<WebExchangeBindException> validationFailed() {
    return new ExceptionMapping<>() {
      @Override
      public Class<WebExchangeBindException> exceptionType() {
        return WebExchangeBindException.class;
      }

      @Override
      public ErrorDescriptor describe(WebExchangeBindException exception, ErrorContext context) {
        List<ErrorEntry> entries =
            exception.getFieldErrors().stream()
                .map(
                    fieldError ->
                        new ErrorEntry(
                            "FIELD",
                            ErrorCodes.REQUEST_VALIDATION_FAILED,
                            fieldTarget(fieldError),
                            MessagePolicy.RESOLVE))
                .toList();
        if (entries.isEmpty()) {
          entries =
              List.of(
                  new ErrorEntry(
                      "FORM",
                      ErrorCodes.REQUEST_VALIDATION_FAILED,
                      List.of(),
                      MessagePolicy.RESOLVE));
        }
        return new ErrorDescriptor(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            entries,
            "http.api.error",
            LogLevel.WARN,
            false,
            Map.of());
      }

      @Override
      public String diagnosticMessage(WebExchangeBindException exception) {
        return exception.getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(exception.getMessage());
      }

      private List<String> fieldTarget(FieldError fieldError) {
        return StringUtils.hasText(fieldError.getField())
            ? List.of(fieldError.getField())
            : List.of();
      }
    };
  }
}
