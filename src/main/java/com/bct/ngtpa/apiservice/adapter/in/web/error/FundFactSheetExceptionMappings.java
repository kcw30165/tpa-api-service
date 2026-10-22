package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.7 (M1 closure, Option A): moves {@code
 * ApiExceptionHandler.handleFundFactSheetRetrievalException}'s mapping out of the handler.
 *
 * <p>Built via {@link ErrorDescriptor#singleWithoutMessage} ({@link MessagePolicy#NONE}), since
 * this handler has always passed a hard-coded {@code null} message (never calling {@code
 * resolvePublicMessage}), confirmed unchanged by the sub-task 1.6b characterization pack (O-12).
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
