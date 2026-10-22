package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.4: verbatim move of {@code ApiExceptionHandler.handleCacheException}'s mapping. Logs
 * at ERROR without a stack trace because the cause carries raw Redis connection details (addresses,
 * passwords, cert paths) that must never reach application logs.
 */
public final class CacheExceptionMappings {

  private CacheExceptionMappings() {}

  public static ExceptionMapping<CacheException> unexpected() {
    return new ExceptionMapping<>() {
      @Override
      public Class<CacheException> exceptionType() {
        return CacheException.class;
      }

      @Override
      public ErrorDescriptor describe(CacheException exception, ErrorContext context) {
        return new ErrorDescriptor(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "FORM",
            ErrorCodes.CACHE_UNEXPECTED,
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }
    };
  }
}
