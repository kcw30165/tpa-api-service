package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;

/**
 * Sub-task 1.4: verbatim move of {@code ApiExceptionHandler.handleServerWebInputException}'s
 * mapping, including its reason-or-message diagnostic text.
 *
 * <p>Sub-task 1.7 (M1 closure, Option A): built via {@link ErrorDescriptor#single} instead of the
 * record constructor directly (one {@link MessagePolicy#RESOLVE} entry); no behaviour change.
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
