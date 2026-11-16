package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.VestingScaleRetrievalException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class VestingScaleExceptionMappings {
  private static final String SYSTEM_ERROR_TYPE = "SYSTEM";
  private static final String HTTP_API_ERROR = "http.api.error";

  private VestingScaleExceptionMappings() {}

  public static ExceptionMapping<VestingScaleRetrievalException> retrievalUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<VestingScaleRetrievalException> exceptionType() {
        return VestingScaleRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(
          VestingScaleRetrievalException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            SYSTEM_ERROR_TYPE,
            ErrorCodes.VESTING_SCALE_RETRIEVAL_UNAVAILABLE,
            List.of(),
            HTTP_API_ERROR,
            LogLevel.WARN,
            false);
      }
    };
  }
}
