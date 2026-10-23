package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Mappings for {@link ApimProfileResolutionException} and {@link ApimException}, including the
 * {@code resolveApimErrorCode} logic specific to {@link ApimException}. Both are built via {@link
 * ErrorDescriptor#single} (one {@link MessagePolicy#RESOLVE} entry each).
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
        String internalCode =
            exception.getInternalCode() != null
                ? exception.getInternalCode()
                : "err.apim.profile.resolution.failed";
        return ErrorDescriptor.single(
            exception.getPublicStatus(),
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            internalCode,
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
            resolveErrorCode(exception),
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }

      private String resolveErrorCode(ApimException exception) {
        return StringUtils.hasText(exception.getErrorCode())
            ? exception.getErrorCode()
            : ErrorCodes.APIM_UPSTREAM_FAILURE;
      }
    };
  }
}
