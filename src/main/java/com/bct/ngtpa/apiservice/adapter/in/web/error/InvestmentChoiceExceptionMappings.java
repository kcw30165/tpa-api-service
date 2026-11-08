package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelNotCancellableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnavailableException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class InvestmentChoiceExceptionMappings {
  private static final String SYSTEM_ERROR_TYPE = "SYSTEM";
  private static final String HTTP_API_ERROR = "http.api.error";

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
            SYSTEM_ERROR_TYPE,
            ErrorCodes.INVESTMENT_CHOICE_RETRIEVAL_UNAVAILABLE,
            List.of(),
            HTTP_API_ERROR,
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
            SYSTEM_ERROR_TYPE,
            ErrorCodes.INVESTMENT_CHOICE_SUBMISSION_UNAVAILABLE,
            List.of(),
            HTTP_API_ERROR,
            LogLevel.WARN,
            false);
      }
    };
  }

  public static ExceptionMapping<InvestmentChoiceCancelUnavailableException> cancelUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<InvestmentChoiceCancelUnavailableException> exceptionType() {
        return InvestmentChoiceCancelUnavailableException.class;
      }

      @Override
      public ErrorDescriptor describe(
          InvestmentChoiceCancelUnavailableException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            SYSTEM_ERROR_TYPE,
            ErrorCodes.INVESTMENT_CHOICE_CANCEL_UNAVAILABLE,
            List.of(),
            HTTP_API_ERROR,
            LogLevel.WARN,
            false);
      }
    };
  }

  public static ExceptionMapping<InvestmentChoiceCancelNotCancellableException>
      cancelNotCancellable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<InvestmentChoiceCancelNotCancellableException> exceptionType() {
        return InvestmentChoiceCancelNotCancellableException.class;
      }

      @Override
      public ErrorDescriptor describe(
          InvestmentChoiceCancelNotCancellableException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.UNPROCESSABLE_CONTENT,
            ApiStatus.BUSINESS_REJECTED,
            "BUSINESS",
            ErrorCodes.INVESTMENT_CHOICE_CANCEL_NOT_CANCELLABLE,
            List.of(),
            HTTP_API_ERROR,
            LogLevel.WARN,
            false);
      }
    };
  }
}
