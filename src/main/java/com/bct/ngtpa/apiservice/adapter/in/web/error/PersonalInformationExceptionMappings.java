package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.4: verbatim move of {@code
 * ApiExceptionHandler.handleInvalidPersonalInformationUpdate}'s mapping.
 */
public final class PersonalInformationExceptionMappings {

  private PersonalInformationExceptionMappings() {}

  public static ExceptionMapping<InvalidPersonalInformationUpdateException> invalidUpdate() {
    return new ExceptionMapping<>() {
      @Override
      public Class<InvalidPersonalInformationUpdateException> exceptionType() {
        return InvalidPersonalInformationUpdateException.class;
      }

      @Override
      public ErrorDescriptor describe(
          InvalidPersonalInformationUpdateException exception, ErrorContext context) {
        return new ErrorDescriptor(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            "FORM",
            ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }
}
