package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnavailableException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class InvestmentChoiceExceptionMappings {

  private InvestmentChoiceExceptionMappings() {}

  public static ExceptionMapping<InvestmentChoiceDetailsRetrievalException> retrievalUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<InvestmentChoiceDetailsRetrievalException> exceptionType() {
        return InvestmentChoiceDetailsRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(
          InvestmentChoiceDetailsRetrievalException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            ErrorCodes.INVESTMENT_CHOICE_RETRIEVAL_UNAVAILABLE,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }

  public static ExceptionMapping<InvestmentChoiceSubmissionUnavailableException>
      submissionUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<InvestmentChoiceSubmissionUnavailableException> exceptionType() {
        return InvestmentChoiceSubmissionUnavailableException.class;
      }

      @Override
      public ErrorDescriptor describe(
          InvestmentChoiceSubmissionUnavailableException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            ErrorCodes.INVESTMENT_CHOICE_SUBMISSION_UNAVAILABLE,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }
}
