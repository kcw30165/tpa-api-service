package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import java.util.List;

/**
 * Mappings for {@link ApimProfileResolutionException} and {@link ApimException}. Both are built via
 * {@link ErrorDescriptor#single} (one {@link MessagePolicy#RESOLVE} entry each).
 *
 * <p>{@link ApimProfileResolutionException#getInternalCode()} is hardcoded to always return a
 * non-null value, and {@link ApimException#getErrorCode()} is always normalized by its own
 * constructor to a non-blank default, so neither mapping needs a null/blank guard on the
 * exception's own code.
 */
public final class ApimExceptionMappings {

  private ApimExceptionMappings() {}

  public static ExceptionMapping<ApimProfileResolutionException> profileResolution() {
    return new ExceptionMapping<>() {
      @Override
      public Class<ApimProfileResolutionException> exceptionType() {
        return ApimProfileResolutionException.class;
      }

      @Override
      public ErrorDescriptor describe(
          ApimProfileResolutionException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            exception.getPublicStatus(),
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            exception.getInternalCode(),
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }

      @Override
      public String diagnosticMessage(ApimProfileResolutionException exception) {
        return "APIM profile resolution failed";
      }
    };
  }

  public static ExceptionMapping<ApimException> upstream() {
    return new ExceptionMapping<>() {
      @Override
      public Class<ApimException> exceptionType() {
        return ApimException.class;
      }

      @Override
      public ErrorDescriptor describe(ApimException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            exception.getStatusCode(),
            ApiStatus.SYSTEM_ERROR,
            "DOWNSTREAM_SYSTEM",
            exception.getErrorCode(),
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }
    };
  }
}
