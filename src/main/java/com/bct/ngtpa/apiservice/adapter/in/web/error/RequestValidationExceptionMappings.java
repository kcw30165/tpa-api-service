package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;

/**
 * Mapping for {@link ServerWebInputException}, including its reason-or-message diagnostic text.
 * Built via {@link ErrorDescriptor#single} (one {@link MessagePolicy#RESOLVE} entry).
 *
 * <p>Sub-task 1.11 (item 5, coverage closure): the {@code StringUtils.hasText(reason)} false branch
 * in {@code diagnosticMessage} (falling back to {@code exception.getMessage()}) is left
 * undocumented by a new test, since {@link ServerWebInputException}'s internal reason-handling
 * behavior for a blank-reason construction path was not confirmed from framework source in this
 * session; a test against unconfirmed framework internals risks asserting behavior that may not
 * hold across Spring versions.
 */
public final class RequestValidationExceptionMappings {

  private RequestValidationExceptionMappings() {}

  public static ExceptionMapping<ServerWebInputException> malformedBody() {
    return new ExceptionMapping<>() {
      @Override
      public Class<ServerWebInputException> exceptionType() {
        return ServerWebInputException.class;
      }

      @Override
      public ErrorDescriptor describe(ServerWebInputException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            "FORM",
            ErrorCodes.REQUEST_BODY_MALFORMED,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }

      @Override
      public String diagnosticMessage(ServerWebInputException exception) {
        return StringUtils.hasText(exception.getReason())
            ? exception.getReason()
            : exception.getMessage();
      }
    };
  }
}
