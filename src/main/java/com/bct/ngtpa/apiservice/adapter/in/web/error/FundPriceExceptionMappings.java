package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.FundPriceInvalidDateException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Mappings for {@link FundPriceRetrievalException} and {@link FundPriceInvalidDateException}. Both
 * built via {@link ErrorDescriptor#single} (one {@link MessagePolicy#RESOLVE} entry each).
 */
public final class FundPriceExceptionMappings {

  private FundPriceExceptionMappings() {}

  public static ExceptionMapping<FundPriceRetrievalException> retrievalUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<FundPriceRetrievalException> exceptionType() {
        return FundPriceRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(FundPriceRetrievalException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "FORM",
            ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE,
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }
    };
  }

  public static ExceptionMapping<FundPriceInvalidDateException> invalidDate() {
    return new ExceptionMapping<>() {
      @Override
      public Class<FundPriceInvalidDateException> exceptionType() {
        return FundPriceInvalidDateException.class;
      }

      @Override
      public ErrorDescriptor describe(
          FundPriceInvalidDateException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            "FORM",
            ErrorCodes.FUND_PRICE_REQUEST_INVALID,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }
}
