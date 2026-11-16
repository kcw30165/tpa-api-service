package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.resource.NoResourceFoundException;

/** Mapping for a WebFlux request whose route or static resource does not exist. */
public final class WebResourceExceptionMappings {

  private WebResourceExceptionMappings() {}

  public static ExceptionMapping<NoResourceFoundException> notFound() {
    return new ExceptionMapping<>() {
      @Override
      public Class<NoResourceFoundException> exceptionType() {
        return NoResourceFoundException.class;
      }

      @Override
      public ErrorDescriptor describe(NoResourceFoundException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.NOT_FOUND,
            ApiStatus.VALIDATION_FAILED,
            "FORM",
            ErrorCodes.REQUEST_RESOURCE_NOT_FOUND,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }
}
