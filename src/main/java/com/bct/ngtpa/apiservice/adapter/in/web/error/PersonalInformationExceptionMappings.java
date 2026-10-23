package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Mapping for {@link InvalidPersonalInformationUpdateException}. Built via {@link
 * ErrorDescriptor#single} (one {@link MessagePolicy#RESOLVE} entry).
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
        return ErrorDescriptor.single(
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
