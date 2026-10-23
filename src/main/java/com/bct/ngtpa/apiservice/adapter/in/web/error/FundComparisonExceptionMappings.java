package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Mapping for {@link FundComparisonRetrievalException}. {@code
 * FundComparisonRequestInvalidException} is mapped in {@link ApplicationExceptionMappings} since
 * its original handler used the shared {@code handleApplicationException} helper. Built via {@link
 * ErrorDescriptor#single} (one {@link MessagePolicy#RESOLVE} entry).
 */
public final class FundComparisonExceptionMappings {

  private FundComparisonExceptionMappings() {}

  public static ExceptionMapping<FundComparisonRetrievalException> retrievalUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<FundComparisonRetrievalException> exceptionType() {
        return FundComparisonRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(
          FundComparisonRetrievalException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "FORM",
            ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE,
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }
    };
  }
}
