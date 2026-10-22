package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.4: verbatim move of {@code
 * ApiExceptionHandler.handlePortalAccessContextResolutionException}'s mapping.
 *
 * <p>Sub-task 1.7 (M1 closure, Option A): built via {@link ErrorDescriptor#single} instead of the
 * record constructor directly (one {@link MessagePolicy#RESOLVE} entry); no behaviour change.
 */
public final class PortalAccessExceptionMappings {

  private PortalAccessExceptionMappings() {}

  public static ExceptionMapping<PortalAccessContextResolutionException> resolutionFailure() {
    return new ExceptionMapping<>() {
      @Override
      public Class<PortalAccessContextResolutionException> exceptionType() {
        return PortalAccessContextResolutionException.class;
      }

      @Override
      public ErrorDescriptor describe(
          PortalAccessContextResolutionException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.BAD_REQUEST,
            ApiStatus.BUSINESS_REJECTED,
            "BUSINESS",
            exception.getErrorCode(),
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }
}
