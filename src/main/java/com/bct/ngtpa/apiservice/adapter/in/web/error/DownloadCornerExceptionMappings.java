package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.DownloadCornerRetrievalException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class DownloadCornerExceptionMappings {
  private static final String SYSTEM_ERROR_TYPE = "SYSTEM";
  private static final String HTTP_API_ERROR = "http.api.error";

  private DownloadCornerExceptionMappings() {}

  public static ExceptionMapping<DownloadCornerRetrievalException> retrievalUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<DownloadCornerRetrievalException> exceptionType() {
        return DownloadCornerRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(
          DownloadCornerRetrievalException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            SYSTEM_ERROR_TYPE,
            ErrorCodes.DOWNLOAD_CORNER_RETRIEVAL_UNAVAILABLE,
            List.of(),
            HTTP_API_ERROR,
            LogLevel.WARN,
            false);
      }
    };
  }
}
