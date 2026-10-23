package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Mapping for {@link PortalAccessContextResolutionException}. Built via {@link
 * ErrorDescriptor#single} (one {@link MessagePolicy#RESOLVE} entry).
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
