package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Mapping for {@link FundFactSheetRetrievalException}. Built via {@link
 * ErrorDescriptor#singleWithoutMessage} ({@link MessagePolicy#NONE}), since this exception's public
 * message is always {@code null} (O-12, docs/design/15-exception-handling/06-open-items.md).
 */
public final class FundFactSheetExceptionMappings {

  private FundFactSheetExceptionMappings() {}

  public static ExceptionMapping<FundFactSheetRetrievalException> retrievalUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<FundFactSheetRetrievalException> exceptionType() {
        return FundFactSheetRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(
          FundFactSheetRetrievalException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE,
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }
    };
  }
}
