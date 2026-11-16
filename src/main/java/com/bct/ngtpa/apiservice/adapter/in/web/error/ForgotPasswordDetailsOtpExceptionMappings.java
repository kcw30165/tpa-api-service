package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp
    .ForgotPasswordDetailsOtpIncidentPublishedException;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp
    .ForgotPasswordDetailsOtpExpectedFailureException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Preserves catch-all response rendering without taking a second terminal ERROR owner. */
public final class ForgotPasswordDetailsOtpExceptionMappings {
  private ForgotPasswordDetailsOtpExceptionMappings() {}

  public static ExceptionMapping<ForgotPasswordDetailsOtpIncidentPublishedException>
      incidentAlreadyPublished() {
    return new ExceptionMapping<>() {
      @Override
      public Class<ForgotPasswordDetailsOtpIncidentPublishedException> exceptionType() {
        return ForgotPasswordDetailsOtpIncidentPublishedException.class;
      }

      @Override
      public ErrorDescriptor describe(
          ForgotPasswordDetailsOtpIncidentPublishedException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            ErrorCodes.SYSTEM_UNEXPECTED,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }

      @Override
      public String diagnosticMessage(
          ForgotPasswordDetailsOtpIncidentPublishedException exception) {
        return null;
      }
    };
  }

  public static ExceptionMapping<ForgotPasswordDetailsOtpExpectedFailureException>
      expectedFailure() {
    return new ExceptionMapping<>() {
      @Override
      public Class<ForgotPasswordDetailsOtpExpectedFailureException> exceptionType() {
        return ForgotPasswordDetailsOtpExpectedFailureException.class;
      }

      @Override
      public ErrorDescriptor describe(
          ForgotPasswordDetailsOtpExpectedFailureException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            ErrorCodes.SYSTEM_UNEXPECTED,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }

      @Override
      public String diagnosticMessage(
          ForgotPasswordDetailsOtpExpectedFailureException exception) {
        return null;
      }
    };
  }
}
