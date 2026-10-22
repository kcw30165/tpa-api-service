package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.4: verbatim move of {@code
 * ApiExceptionHandler.handleRegistrationLookupDownstreamException}'s mapping, including its
 * reason-qualified diagnostic message.
 */
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
        return new ErrorDescriptor(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "DOWNSTREAM_SYSTEM",
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
}
