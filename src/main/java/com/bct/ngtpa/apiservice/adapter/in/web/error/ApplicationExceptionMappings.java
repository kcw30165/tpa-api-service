package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRequestInvalidException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.4: verbatim move of the mappings that originally went through {@code
 * ApiExceptionHandler}'s private {@code handleApplicationException(ApplicationException ex,
 * ServerWebExchange exchange, HttpStatus status, ApiStatus apiStatus, String errorType)} helper:
 * the generic {@link ApplicationException} handler itself, plus {@link
 * InvalidNotificationRequestException}, {@link InvalidContributionRequestException} and {@link
 * FundComparisonRequestInvalidException}, each of which only varied that helper's fixed {@code
 * apiStatus}/{@code errorType} arguments. All four always used {@code HttpStatus.BAD_REQUEST} and
 * {@code ex.getErrorCode()}, so a single private {@link #fixed} factory reproduces every one of
 * them exactly.
 */
public final class ApplicationExceptionMappings {

  private ApplicationExceptionMappings() {}

  public static ExceptionMapping<ApplicationException> generic() {
    return fixed(
        ApplicationException.class,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ApplicationException::getErrorCode);
  }

  public static ExceptionMapping<InvalidNotificationRequestException> invalidNotificationRequest() {
    return fixed(
        InvalidNotificationRequestException.class,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        InvalidNotificationRequestException::getErrorCode);
  }

  public static ExceptionMapping<InvalidContributionRequestException> invalidContributionRequest() {
    return fixed(
        InvalidContributionRequestException.class,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        InvalidContributionRequestException::getErrorCode);
  }

  public static ExceptionMapping<FundComparisonRequestInvalidException>
      invalidFundComparisonRequest() {
    return fixed(
        FundComparisonRequestInvalidException.class,
        ApiStatus.VALIDATION_FAILED,
        "VALIDATION",
        FundComparisonRequestInvalidException::getErrorCode);
  }

  private static <T extends ApplicationException> ExceptionMapping<T> fixed(
      Class<T> type, ApiStatus apiStatus, String errorType, Function<T, String> errorCode) {
    return new ExceptionMapping<>() {
      @Override
      public Class<T> exceptionType() {
        return type;
      }

      @Override
      public ErrorDescriptor describe(T exception, ErrorContext context) {
        return new ErrorDescriptor(
            HttpStatus.BAD_REQUEST,
            apiStatus,
            errorType,
            errorCode.apply(exception),
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }
}
