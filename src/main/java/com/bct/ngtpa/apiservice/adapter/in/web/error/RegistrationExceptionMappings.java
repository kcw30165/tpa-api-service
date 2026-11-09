package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete
    .RegistrationCompletionPreDispatchException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Exception mappings for registration lookup and completion failures. */
public final class RegistrationExceptionMappings {

  private RegistrationExceptionMappings() {}

  public static ExceptionMapping<RegistrationLookupDownstreamException> lookupDownstream() {
    return new ExceptionMapping<>() {
      @Override
      public Class<RegistrationLookupDownstreamException> exceptionType() {
        return RegistrationLookupDownstreamException.class;
      }

      @Override
      public ErrorDescriptor describe(
          RegistrationLookupDownstreamException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value(),
            List.of("PAGE"),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }

      @Override
      public String diagnosticMessage(RegistrationLookupDownstreamException exception) {
        return "TRPSearchMember contract failure: " + exception.reason();
      }
    };
  }

  public static ExceptionMapping<RegistrationCompletionPreDispatchException>
      completionPreDispatch() {
    return new ExceptionMapping<>() {
      @Override
      public Class<RegistrationCompletionPreDispatchException> exceptionType() {
        return RegistrationCompletionPreDispatchException.class;
      }

      @Override
      public ErrorDescriptor describe(
          RegistrationCompletionPreDispatchException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            ErrorCodes.SYSTEM_UNEXPECTED,
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }

      @Override
      public String diagnosticMessage(RegistrationCompletionPreDispatchException exception) {
        return "Registration completion failed before downstream dispatch";
      }
    };
  }
}
